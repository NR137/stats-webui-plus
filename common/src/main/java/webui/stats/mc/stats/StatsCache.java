package webui.stats.mc.stats;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import webui.stats.mc.McStatsWebui;
import webui.stats.mc.mixin.PlayerAdvancementsAccessor;
import webui.stats.mc.mixin.StatsCounterAccessor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class StatsCache {
	private static final StatsCache INSTANCE = new StatsCache();

	private final Object lock = new Object();
	// Held for a whole refresh, so a burst of requests waits for one refresh instead of each starting its own.
	private final Object refreshLock = new Object();
	private final ConcurrentHashMap.KeySetView<UUID, Boolean> dirty = ConcurrentHashMap.newKeySet();
	// Players whose advancement progress changed (awarded or revoked) since the last refresh.
	private final ConcurrentHashMap.KeySetView<UUID, Boolean> dirtyAdvancements = ConcurrentHashMap.newKeySet();
	private Path worldRoot;
	private Map<UUID, String> knownNames = Map.of();
	private List<PlayerRecord> players = List.of();
	private volatile boolean ready;
	// System.nanoTime() of the last moment the cache matched the game's live stats.
	private volatile long refreshedAt;

	private StatsCache() {
	}

	public static StatsCache get() {
		return INSTANCE;
	}

	public void boot(Path worldRoot, Map<UUID, String> knownNames) {
		synchronized (lock) {
			this.worldRoot = worldRoot;
			this.knownNames = Map.copyOf(knownNames);
			this.players = StatsService.load(worldRoot, this.knownNames);
			this.ready = true;
			this.dirty.clear();
			this.dirtyAdvancements.clear();
			this.refreshedAt = System.nanoTime();
			McStatsWebui.LOGGER.info("Stats cache loaded ({} players)", this.players.size());
		}
	}

	public void clear() {
		synchronized (lock) {
			worldRoot = null;
			knownNames = Map.of();
			players = List.of();
			ready = false;
			dirty.clear();
			dirtyAdvancements.clear();
		}
	}

	public boolean isReady() {
		return ready;
	}

	public void rememberName(UUID uuid, String name) {
		synchronized (lock) {
			Map<UUID, String> next = new LinkedHashMap<>(knownNames);
			next.put(uuid, name);
			knownNames = Map.copyOf(next);
		}
	}

	public void onNameResolved(UUID uuid, String name) {
		synchronized (lock) {
			rememberName(uuid, name);
			List<PlayerRecord> next = new ArrayList<>(players.size());
			for (PlayerRecord player : players) {
				next.add(player.uuid.equals(uuid) ? player.withName(name) : player);
			}
			players = next;
		}
	}

	public void markDirty(UUID uuid) {
		dirty.add(uuid);
	}

	/** Everyone is read again at the next refresh, e.g. after /reload changed which advancements exist. */
	public void markAllDirty() {
		synchronized (lock) {
			for (PlayerRecord player : players) {
				dirtyAdvancements.add(player.uuid);
				dirty.add(player.uuid);
			}
		}
	}

	public void markAdvancementsDirty(UUID uuid) {
		dirtyAdvancements.add(uuid);
		dirty.add(uuid);
	}

	/** Minecraft saved the player's files, e.g. on logout; if they are offline by the next refresh, it reads them. */
	public void onStatsSaved(UUID uuid) {
		if (uuid != null) {
			dirty.add(uuid);
		}
	}

	/** The cached players as they are, however old; never touches the server thread. */
	public Snapshot current() {
		return copy();
	}

	/** Returns the cached players without touching the server thread, or null if older than maxAgeMs. */
	public Snapshot recentSnapshot(long maxAgeMs) {
		if (!ready || maxAgeMs <= 0 || System.nanoTime() - refreshedAt > maxAgeMs * 1_000_000L) {
			return null;
		}
		return copy();
	}

	/**
	 * Returns the players, brought up to date first unless they are less than maxAgeMs old. The server thread only
	 * copies the changed players' stats out of memory; reading, scoring and ranking happen on the calling thread.
	 */
	public Snapshot refresh(MinecraftServer server, long maxAgeMs) throws Exception {
		Snapshot recent = recentSnapshot(maxAgeMs);
		if (recent != null) {
			return recent;
		}
		synchronized (refreshLock) {
			// Another request may have refreshed while this one waited.
			recent = recentSnapshot(maxAgeMs);
			if (recent != null) {
				return recent;
			}
			CompletableFuture<Capture> future = new CompletableFuture<>();
			server.execute(() -> {
				try {
					future.complete(capture(server));
				} catch (Exception e) {
					future.completeExceptionally(e);
				}
			});
			Capture capture;
			try {
				capture = future.get(5, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				// A capture that still runs later must not swallow the changes it took; the next refresh picks them up.
				future.thenAccept(late -> late.players().forEach(player -> {
					if (player.advancements() != null) {
						dirtyAdvancements.add(player.uuid());
					}
					dirty.add(player.uuid());
				}));
				throw e;
			}
			apply(capture);
			return copy();
		}
	}

	/** Server thread: copies what changed since the last refresh, and nothing more. */
	private Capture capture(MinecraftServer server) {
		// The cache is loaded at startup and only cleared while the server stops: then nothing is refreshed.
		if (!isReady()) {
			return new Capture(List.of(), refreshedAt);
		}
		AdvancementCatalog.get().ensure(server);
		long at = System.nanoTime();
		List<PlayerCapture> captured = new ArrayList<>();
		for (UUID uuid : List.copyOf(dirty)) {
			dirty.remove(uuid);
			boolean advancementsChanged = dirtyAdvancements.remove(uuid);
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player == null) {
				// Offline: Minecraft saved their files on logout, so those are complete.
				captured.add(new PlayerCapture(uuid, null, null));
				continue;
			}
			rememberName(uuid, player.getGameProfile().name());
			Object2IntMap<Stat<?>> live = ((StatsCounterAccessor) player.getStats()).mcStatsWebui$stats();
			Object2IntMap<Stat<?>> stats;
			synchronized (live) {
				stats = new Object2IntOpenHashMap<>(live);
			}
			AdvancementProgressFile advancements = advancementsChanged || !tracks(uuid)
				? AdvancementProgressFile.of(((PlayerAdvancementsAccessor) player.getAdvancements()).mcStatsWebui$progress())
				: null;
			captured.add(new PlayerCapture(uuid, stats, advancements));
		}
		return new Capture(captured, at);
	}

	private boolean tracks(UUID uuid) {
		synchronized (lock) {
			for (PlayerRecord player : players) {
				if (player.uuid.equals(uuid)) {
					return true;
				}
			}
			return false;
		}
	}

	/** Calling thread: turns the copies into players and ranks everyone once. */
	private void apply(Capture capture) {
		if (!capture.players().isEmpty()) {
			Path root;
			Map<UUID, PlayerRecord> previous = new LinkedHashMap<>();
			Map<UUID, String> known;
			synchronized (lock) {
				root = worldRoot;
				known = knownNames;
				for (PlayerRecord player : players) {
					previous.put(player.uuid, player);
				}
			}
			NameDirectory names = StatsService.nameDirectory(known);
			Map<UUID, PlayerRecord> updated = new LinkedHashMap<>();
			for (PlayerCapture player : capture.players()) {
				if (player.stats() == null) {
					if (root != null) {
						updated.put(player.uuid(), StatsService.loadOne(root, player.uuid(), names));
					}
				} else {
					StatFile stats = StatFile.of(player.stats());
					PlayerRecord before = previous.get(player.uuid());
					updated.put(
						player.uuid(),
						StatsService.fromMemory(player.uuid(), names, stats, player.advancements(), before)
					);
				}
			}
			synchronized (lock) {
				if (!ready) {
					return;
				}
				Map<UUID, PlayerRecord> byId = new LinkedHashMap<>();
				for (PlayerRecord player : players) {
					byId.put(player.uuid, player);
				}
				updated.forEach((uuid, player) -> {
					if (player == null) {
						byId.remove(uuid);
						return;
					}
					// A name looked up while this refresh ran must not be overwritten by the older one.
					String name = knownNames.get(uuid);
					byId.put(uuid, name != null && !name.equals(player.name) ? player.withName(name) : player);
				});
				players = ChampionScorer.apply(new ArrayList<>(byId.values()));
			}
		}
		refreshedAt = capture.at();
	}

	private Snapshot copy() {
		// Read before copying: the players can only be newer than this, so the age is never understated.
		long at = refreshedAt;
		synchronized (lock) {
			return new Snapshot(List.copyOf(players), at);
		}
	}

	/** The players together with the System.nanoTime() at which they last matched the game's live stats. */
	public record Snapshot(List<PlayerRecord> players, long refreshedAt) {
	}

	/** What changed since the last refresh, copied on the server thread at System.nanoTime() {@code at}. */
	private record Capture(List<PlayerCapture> players, long at) {
	}

	/** Live stats and, if they changed, advancements of an online player; both null for an offline player. */
	private record PlayerCapture(UUID uuid, Object2IntMap<Stat<?>> stats, AdvancementProgressFile advancements) {
	}
}
