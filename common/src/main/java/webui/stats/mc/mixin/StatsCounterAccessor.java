package webui.stats.mc.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StatsCounter.class)
public interface StatsCounterAccessor {
	/** The player's live stats; Minecraft changes them on the server thread only. */
	@Accessor("stats")
	Object2IntMap<Stat<?>> mcStatsWebui$stats();
}
