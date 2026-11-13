package de.nr137.statswebuiplus.stats;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.mixin.PlayerAdvancementsAccessor;
import de.nr137.statswebuiplus.mixin.StatsCounterAccessor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Every player in memory, kept up to date without blocking the game. The mixins mark players whose statistics or
 * advancements changed; nothing else happens until the web UI asks. A refresh then has the server thread copy the
 * marked online players' statistics (and their advancement progress if it changed) out of memory, which takes a
 * fraction of a millisecond, and does everything else (reading files, metrics, ranking) on the asking thread.
 */
public final class StatsCache {
	private static final long SERVER_THREAD_TIMEOUT_MS = 5_000;
	private static final Snapshot EMPTY = new Snapshot(List.of(), 0, new VanillaRanks(List.of()));
	// After EMPTY, which the instance starts with.
	private static final StatsCache INSTANCE = new StatsCache();

	/**
	 * The players as of {@code refreshedAt} ({@link System#nanoTime()}, when they last matched the game), with the ranks
	 * of their vanilla statistics, shared by every request on this set of players.
	 */
	public record Snapshot(List<PlayerRecord> players, long refreshedAt, VanillaRanks vanillaRanks) {
	}

	/** An online player's statistics copied on the server thread; progress only if their advancements were copied. */
	private record Online(UUID uuid, Object2IntMap<Stat<?>> stats, AdvancementProgressFile progress) {
	}

	/** What the server thread handed over: when, the online players' copies, and the marked players who are offline. */
	private record Copy(long time, List<Online> online, Set<UUID> offline, Set<UUID> advancements) {
	}

	private final Object refreshing = new Object();
	private final Set<UUID> statsDirty = ConcurrentHashMap.newKeySet();
	private final Set<UUID> advancementsDirty = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean allDirty = new AtomicBoolean();

	// Written under this lock; the snapshot is read without it.
	private volatile Snapshot snapshot = EMPTY;
	private volatile boolean ready;
	private volatile NameDirectory names = new NameDirectory();
	private Path worldRoot;
	// Counts boots and clears, so a refresh that started before one of them applies nothing.
	private long generation;

	private StatsCache() {
	}

	public static StatsCache get() {
		return INSTANCE;
	}

	/** Server start: every player of the world from their files. */
	public void boot(Path worldRoot, Map<UUID, String> knownNames) {
		NameDirectory directory = PlayerFiles.nameDirectory(knownNames);
		List<PlayerRecord> players = PlayerFiles.load(worldRoot, directory);
		synchronized (this) {
			generation++;
			this.worldRoot = worldRoot;
			this.names = directory;
			snapshot = snapshot(players, System.nanoTime());
			ready = true;
		}
		StatsWebuiPlus.LOGGER.info("Stats cache loaded ({} players)", players.size());
	}

	/** Server stopping. */
	public void clear() {
		synchronized (this) {
			generation++;
			ready = false;
			worldRoot = null;
			snapshot = EMPTY;
			names = new NameDirectory();
			statsDirty.clear();
			advancementsDirty.clear();
			allDirty.set(false);
		}
	}

	public boolean isReady() {
		return ready;
	}

	public void rememberName(UUID uuid, String name) {
		names.put(uuid, name);
	}

	/** A name lookup finished: the player is renamed in place, the ranks stay. */
	public void onNameResolved(UUID uuid, String name) {
		rememberName(uuid, name);
		synchronized (this) {
			List<PlayerRecord> players = snapshot.players();
			for (int i = 0; i < players.size(); i++) {
				PlayerRecord player = players.get(i);
				if (player.uuid().equals(uuid)) {
					if (!player.name().equals(name)) {
						List<PlayerRecord> renamed = new ArrayList<>(players);
						renamed.set(i, player.withName(name));
						snapshot = new Snapshot(List.copyOf(renamed), snapshot.refreshedAt(), snapshot.vanillaRanks());
					}
					return;
				}
			}
		}
	}

	/** A statistic of the player changed. Called on every change (play time: every tick), so it only marks. */
	public void markDirty(UUID uuid) {
		if (uuid != null) {
			statsDirty.add(uuid);
		}
	}

	/** After a data-pack reload: advancements may have come or gone for everyone. */
	public void markAllDirty() {
		allDirty.set(true);
	}

	/** An award, a revoke or a reload of the player's progress; the statistics are copied along. */
	public void markAdvancementsDirty(UUID uuid) {
		if (uuid != null) {
			advancementsDirty.add(uuid);
			statsDirty.add(uuid);
		}
	}

	/** The game saved the player's files: for a player who just left, the files now hold everything. */
	public void onStatsSaved(UUID uuid) {
		if (uuid != null) {
			statsDirty.add(uuid);
		}
	}

	/** The players as they are; never touches the server thread. */
	public Snapshot current() {
		return snapshot;
	}

	/** The snapshot if it matched the game less than {@code maxAgeMs} ago; null if older or not booted. */
	public Snapshot recentSnapshot(long maxAgeMs) {
		Snapshot current = snapshot;
		if (!ready || System.nanoTime() - current.refreshedAt() >= TimeUnit.MILLISECONDS.toNanos(maxAgeMs)) {
			return null;
		}
		return current;
	}

	/**
	 * The players, brought up to date unless they matched the game less than {@code maxAgeMs} ago. One refresh runs at
	 * a time; others wait for it and take its result if that is fresh enough for them.
	 *
	 * @throws TimeoutException if the server thread didn't hand over the statistics within 5 seconds (a lag spike)
	 */
	public Snapshot refresh(MinecraftServer server, long maxAgeMs) throws Exception {
		Snapshot fresh = recentSnapshot(maxAgeMs);
		if (fresh != null) {
			return fresh;
		}
		synchronized (refreshing) {
			fresh = recentSnapshot(maxAgeMs);
			if (fresh != null) {
				return fresh;
			}
			long started;
			Set<UUID> known = new HashSet<>();
			synchronized (this) {
				if (!ready) {
					return snapshot;
				}
				started = generation;
				for (PlayerRecord player : snapshot.players()) {
					known.add(player.uuid());
				}
			}
			apply(copyFromServer(server, known), started);
			return snapshot;
		}
	}

	private Copy copyFromServer(MinecraftServer server, Set<UUID> known) throws Exception {
		CompletableFuture<Copy> copy = new CompletableFuture<>();
		Runnable task = () -> {
			try {
				copy.complete(copy(server, known));
			} catch (Throwable e) {
				copy.completeExceptionally(e);
			}
		};
		if (server.isSameThread()) {
			task.run();
		} else {
			server.execute(task);
		}
		try {
			return copy.get(SERVER_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			// The copy may still come; its players are marked again so the next refresh doesn't miss them.
			copy.thenAccept(this::markAgain);
			throw new TimeoutException("The server thread did not hand over the stats within " + SERVER_THREAD_TIMEOUT_MS + " ms");
		} catch (ExecutionException e) {
			if (e.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw e;
		}
	}

	/** Server thread only: the live statistics and progress belong to it. */
	private Copy copy(MinecraftServer server, Set<UUID> known) {
		long time = System.nanoTime();
		Set<UUID> stats = drain(statsDirty);
		Set<UUID> advancements = drain(advancementsDirty);
		List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
		if (allDirty.getAndSet(false)) {
			stats.addAll(known);
			for (ServerPlayer player : onlinePlayers) {
				stats.add(player.getUUID());
			}
			advancements.addAll(stats);
		}
		stats.addAll(advancements);
		NameDirectory directory = names;
		for (ServerPlayer player : onlinePlayers) {
			directory.put(player.getUUID(), player.getName().getString());
		}
		AdvancementCatalog.get().ensure(server);

		List<Online> online = new ArrayList<>();
		Set<UUID> offline = new HashSet<>();
		for (UUID uuid : stats) {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player == null) {
				// Offline: the game saved their files on logout, they are complete.
				offline.add(uuid);
				continue;
			}
			Object2IntMap<Stat<?>> live = ((StatsCounterAccessor) player.getStats()).statsWebuiPlus$stats();
			Object2IntMap<Stat<?>> statsCopy;
			synchronized (live) {
				statsCopy = new Object2IntOpenHashMap<>(live);
			}
			AdvancementProgressFile progress = null;
			if (advancements.contains(uuid) || !known.contains(uuid)) {
				progress = AdvancementProgressFile.of(((PlayerAdvancementsAccessor) player.getAdvancements()).statsWebuiPlus$progress());
			}
			online.add(new Online(uuid, statsCopy, progress));
		}
		return new Copy(time, online, offline, advancements);
	}

	private void markAgain(Copy copy) {
		for (Online player : copy.online()) {
			statsDirty.add(player.uuid());
		}
		statsDirty.addAll(copy.offline());
		advancementsDirty.addAll(copy.advancements());
	}

	/** The calling thread: files, metrics and ranking. */
	private void apply(Copy copy, long started) {
		Path root;
		NameDirectory directory;
		Map<UUID, PlayerRecord> previous = new HashMap<>();
		synchronized (this) {
			if (started != generation) {
				return;
			}
			root = worldRoot;
			directory = names;
			for (PlayerRecord player : snapshot.players()) {
				previous.put(player.uuid(), player);
			}
		}
		if (copy.online().isEmpty() && copy.offline().isEmpty()) {
			synchronized (this) {
				if (started == generation) {
					snapshot = new Snapshot(snapshot.players(), copy.time(), snapshot.vanillaRanks());
				}
			}
			return;
		}

		Map<UUID, PlayerRecord> changed = new LinkedHashMap<>();
		Set<UUID> gone = new HashSet<>();
		for (UUID uuid : copy.offline()) {
			PlayerRecord player = PlayerFiles.loadOne(root, uuid, directory);
			if (player != null) {
				changed.put(uuid, player);
			} else if (!PlayerFiles.hasStatsFile(root, uuid)) {
				gone.add(uuid);
			}
		}
		for (Online player : copy.online()) {
			changed.put(player.uuid(), PlayerFiles.fromMemory(
				player.uuid(), directory, StatFile.of(player.stats()), player.progress(), previous.get(player.uuid())
			));
		}

		synchronized (this) {
			if (started != generation) {
				return;
			}
			Map<UUID, PlayerRecord> players = new LinkedHashMap<>();
			for (PlayerRecord player : snapshot.players()) {
				players.put(player.uuid(), player);
			}
			gone.forEach(players::remove);
			changed.forEach((uuid, player) -> {
				// A name learned since the copy was made wins.
				String name = directory.get(uuid);
				players.put(uuid, directory.knows(uuid) && !name.equals(player.name()) ? player.withName(name) : player);
			});
			snapshot = snapshot(Skills.apply(new ArrayList<>(players.values())), copy.time());
		}
	}

	private static Snapshot snapshot(List<PlayerRecord> players, long refreshedAt) {
		List<PlayerRecord> copy = List.copyOf(players);
		return new Snapshot(copy, refreshedAt, new VanillaRanks(copy));
	}

	private static Set<UUID> drain(Set<UUID> marked) {
		Set<UUID> taken = new HashSet<>();
		for (Iterator<UUID> it = marked.iterator(); it.hasNext(); ) {
			taken.add(it.next());
			it.remove();
		}
		return taken;
	}
}
