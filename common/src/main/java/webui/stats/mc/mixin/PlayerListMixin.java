package webui.stats.mc.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import webui.stats.mc.McStatsWebui;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	// Minecraft calls this on every /reload, once the new data packs are in place.
	@Inject(method = "reloadResources", at = @At("RETURN"))
	private void mcStatsWebui$afterReloadResources(CallbackInfo ci) {
		McStatsWebui.onDataPacksReloaded(server);
	}
}
