package de.nr137.statswebuiplus.mixin;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import de.nr137.statswebuiplus.stats.PlayerFiles;
import de.nr137.statswebuiplus.stats.StatsCache;

/** Tells the stats cache when a player's advancements change and when the game saved them. */
@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
	@Shadow
	@Final
	private Path playerSavePath;

	@Shadow
	private ServerPlayer player;

	@Inject(method = "save", at = @At("RETURN"))
	private void statsWebuiPlus$afterSave(CallbackInfo ci) {
		StatsCache.get().onStatsSaved(statsWebuiPlus$uuid());
	}

	// Award and revoke return whether a criterion changed; a repeated award changes nothing.
	@Inject(method = "award", at = @At("RETURN"))
	private void statsWebuiPlus$afterAward(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			StatsCache.get().markAdvancementsDirty(statsWebuiPlus$uuid());
		}
	}

	@Inject(method = "revoke", at = @At("RETURN"))
	private void statsWebuiPlus$afterRevoke(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			StatsCache.get().markAdvancementsDirty(statsWebuiPlus$uuid());
		}
	}

	// /reload replaces the whole progress from the file, without award or revoke.
	@Inject(method = "reload", at = @At("RETURN"))
	private void statsWebuiPlus$afterReload(ServerAdvancementManager manager, CallbackInfo ci) {
		StatsCache.get().markAdvancementsDirty(statsWebuiPlus$uuid());
	}

	@Unique
	private UUID statsWebuiPlus$uuid() {
		return player != null ? player.getUUID() : PlayerFiles.uuidOf(playerSavePath);
	}
}
