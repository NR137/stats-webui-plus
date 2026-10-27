package webui.stats.mc.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PlayerAdvancements.class)
public interface PlayerAdvancementsAccessor {
	/** The player's live advancement progress; Minecraft changes it on the server thread only. */
	@Accessor("progress")
	Map<AdvancementHolder, AdvancementProgress> mcStatsWebui$progress();
}
