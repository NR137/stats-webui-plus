package de.nr137.statswebuiplus.mixin;

import java.nio.file.Path;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.statswebuiplus.stats.PlayerFiles;
import de.nr137.statswebuiplus.stats.StatsCache;

/** Tells the stats cache when a player's statistics change and when the game saved them. */
@Mixin(ServerStatsCounter.class)
public class ServerStatsCounterMixin {
	@Shadow
	@Final
	private Path file;

	// The counter doesn't know its player; its file is named after their UUID.
	@Inject(method = "save", at = @At("RETURN"))
	private void statsWebuiPlus$afterSave(CallbackInfo ci) {
		StatsCache.get().onStatsSaved(PlayerFiles.uuidOf(file));
	}

	@Inject(method = "setValue", at = @At("RETURN"))
	private void statsWebuiPlus$afterSetValue(Player player, Stat<?> stat, int count, CallbackInfo ci) {
		if (player != null) {
			StatsCache.get().markDirty(player.getUUID());
		}
	}
}
