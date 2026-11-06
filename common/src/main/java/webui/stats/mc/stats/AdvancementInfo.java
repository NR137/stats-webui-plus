package webui.stats.mc.stats;

public record AdvancementInfo(
	String id,
	String tab,
	String title,
	String description,
	String icon,
	String frame,
	boolean hidden,
	// Experience points the game gives for it (0 for none).
	int experience
) {
}
