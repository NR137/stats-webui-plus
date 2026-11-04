package webui.stats.mc.loadtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Online players for the load test (scripts/load_test): config/stats_webui_plus_loadtest.json {"players": 100} logs
 * that many in when the server has started, and on every tick raises their stats the way playing does: distance
 * walked every tick, now and then a block mined, an item used, a jump. So every refresh of the stats cache has
 * changed online players to copy on the server thread, the part of the mod that costs the game time.
 */
public final class LoadPlayers {
	private static final List<TestPlayers.Joined> PLAYERS = new ArrayList<>();
	private static final Random RANDOM = new Random(26);
	private static List<Block> blocks = List.of();
	private static List<Item> items = List.of();

	private LoadPlayers() {
	}

	public static void start(MinecraftServer server) {
		int count = configuredCount(server);
		if (count <= 0) {
			return;
		}
		blocks = BuiltInRegistries.BLOCK.stream().limit(200).toList();
		items = BuiltInRegistries.ITEM.stream().limit(200).toList();
		for (int index = 0; index < count; index++) {
			UUID uuid = UUID.nameUUIDFromBytes(("load-test-player-" + index).getBytes());
			PLAYERS.add(TestPlayers.join(server, server.overworld(), uuid, String.format("Online%03d", index)));
		}
		System.out.println("[load test] " + count + " online players logged in");
	}

	public static void tick(MinecraftServer server) {
		for (TestPlayers.Joined joined : PLAYERS) {
			TestPlayers.answer(joined);
			if (joined.player().hasDisconnected()) {
				continue;
			}
			var player = joined.player();
			player.awardStat(Stats.CUSTOM.get(Stats.WALK_ONE_CM), 20 + RANDOM.nextInt(10));
			int roll = RANDOM.nextInt(20);
			if (roll == 0) {
				player.awardStat(Stats.BLOCK_MINED.get(blocks.get(RANDOM.nextInt(blocks.size()))), 1);
			} else if (roll == 1) {
				player.awardStat(Stats.ITEM_USED.get(items.get(RANDOM.nextInt(items.size()))), 1);
			} else if (roll == 2) {
				player.awardStat(Stats.CUSTOM.get(Stats.JUMP), 1);
			}
		}
	}

	private static int configuredCount(MinecraftServer server) {
		Path file = server.getServerDirectory().resolve("config").resolve("stats_webui_plus_loadtest.json");
		if (!Files.isRegularFile(file)) {
			return 0;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			return json.has("players") ? json.get("players").getAsInt() : 0;
		} catch (IOException | RuntimeException e) {
			System.out.println("[load test] can't read " + file + ": " + e);
			return 0;
		}
	}
}
