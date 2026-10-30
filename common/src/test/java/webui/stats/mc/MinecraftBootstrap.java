package webui.stats.mc;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Starts Minecraft's registries once, for tests that read vanilla stat or block IDs. */
public final class MinecraftBootstrap {
	private static boolean done;

	private MinecraftBootstrap() {
	}

	public static synchronized void run() {
		if (!done) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			done = true;
		}
	}
}
