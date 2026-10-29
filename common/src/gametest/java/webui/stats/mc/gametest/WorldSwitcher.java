package webui.stats.mc.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import webui.stats.mc.McStatsWebui;

/**
 * For the singleplayer test (scripts/smoke_test/client_test.py): leaves the open world and opens another one in the
 * same client, when the test writes that world's folder name to switch-world.txt in the game folder. Called on every
 * client tick by the loader's test code (fabric/src/gametest, neoforge/src/gametest); not part of the mod JAR.
 */
public final class WorldSwitcher {
	private static int ticks;

	private WorldSwitcher() {
	}

	public static void onClientTick() {
		if (++ticks % 20 != 0) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Path trigger = minecraft.gameDirectory.toPath().resolve("switch-world.txt");
		if (minecraft.level == null || !Files.isRegularFile(trigger)) {
			return;
		}
		String world;
		try {
			world = Files.readString(trigger).trim();
			Files.delete(trigger);
		} catch (IOException e) {
			McStatsWebui.LOGGER.warn("Could not read {}", trigger, e);
			return;
		}
		McStatsWebui.LOGGER.info("Game test: leaving the world and opening {}", world);
		minecraft.disconnectWithSavingScreen();
		minecraft.createWorldOpenFlows().openWorld(world, () -> McStatsWebui.LOGGER.warn("Game test: opening {} was cancelled", world));
	}
}
