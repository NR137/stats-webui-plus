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
	int experience,
	// The advancement it hangs off in the game's tree; null for a tab's root.
	String parent,
	// Where the game's advancement screen puts it: column (x) and row (y) of the tab's tree, as the game lays it out.
	float x,
	float y
) {
}
