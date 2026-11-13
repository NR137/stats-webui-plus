package de.nr137.statswebuiplus.stats;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The advancements the server has (data packs included), as the web UI shows them: every advancement with a display,
 * grouped in the tabs of the game's advancements screen. Booted on server start and again after /reload; a boot
 * swaps everything at once, so readers never see a half-built catalog.
 */
public final class AdvancementCatalog {
	private static final List<String> TAB_ORDER = List.of("story", "nether", "end", "adventure", "husbandry");
	private static final String BACKGROUNDS = "gui/advancements/backgrounds/";
	// A parent chain longer than this is a broken data pack (or a loop); the tab is then the topmost reached.
	private static final int MAX_DEPTH = 48;

	private record State(
		boolean ready,
		List<AdvancementInfo> all,
		Map<String, AdvancementInfo> byId,
		List<AdvancementTab> tabs,
		Map<String, List<List<String>>> requirements
	) {
	}

	private static final State EMPTY = new State(false, List.of(), Map.of(), List.of(), Map.of());
	// After EMPTY, which the instance starts with.
	private static final AdvancementCatalog INSTANCE = new AdvancementCatalog();

	private volatile State state = EMPTY;

	private AdvancementCatalog() {
	}

	public static AdvancementCatalog get() {
		return INSTANCE;
	}

	/** Boots the catalog unless it is booted already. */
	public void ensure(MinecraftServer server) {
		if (!state.ready()) {
			boot(server);
		}
	}

	public void boot(MinecraftServer server) {
		Map<Identifier, AdvancementHolder> holders = new LinkedHashMap<>();
		for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
			holders.put(holder.id(), holder);
		}
		AdvancementTree tree = server.getAdvancements().tree();

		Map<String, List<List<String>>> requirements = new HashMap<>();
		List<AdvancementInfo> all = new ArrayList<>();
		Map<String, AdvancementTab> tabs = new LinkedHashMap<>();
		for (AdvancementHolder holder : holders.values()) {
			Advancement advancement = holder.value();
			requirements.put(holder.id().toString(), copy(advancement.requirements().requirements()));
			if (advancement.display().isEmpty()) {
				continue;
			}
			DisplayInfo display = advancement.display().get();
			List<AdvancementHolder> chain = ancestors(holder, holders);
			AdvancementHolder root = chain.getLast();
			AdvancementHolder shownRoot = holder;
			for (AdvancementHolder ancestor : chain) {
				if (ancestor.value().display().isPresent()) {
					shownRoot = ancestor;
				}
			}
			String tab = tabId(shownRoot.id());
			String parent = null;
			for (AdvancementHolder ancestor : chain.subList(1, chain.size())) {
				if (ancestor.value().display().isPresent()) {
					parent = ancestor.id().toString();
					break;
				}
			}
			AdvancementNode node = tree.get(holder);
			all.add(new AdvancementInfo(
				holder.id().toString(),
				tab,
				display.title().getString(),
				display.description().getString(),
				icon(display),
				frame(display),
				display.hidden(),
				advancement.rewards().experience(),
				parent,
				node == null ? 0 : node.x(),
				node == null ? 0 : node.y()
			));
			if (!tabs.containsKey(tab)) {
				tabs.put(tab, tab(tab, holder, root));
			}
		}

		all.sort(Comparator.comparingInt((AdvancementInfo info) -> tabRank(info.tab()))
			.thenComparing(AdvancementInfo::tab)
			.thenComparing(info -> info.title().toLowerCase(Locale.ROOT))
			.thenComparing(AdvancementInfo::id));
		List<AdvancementTab> sortedTabs = new ArrayList<>(tabs.values());
		sortedTabs.sort(Comparator.comparingInt((AdvancementTab info) -> tabRank(info.id())).thenComparing(AdvancementTab::id));
		Map<String, AdvancementInfo> byId = new LinkedHashMap<>();
		for (AdvancementInfo info : all) {
			byId.put(info.id(), info);
		}
		state = new State(true, List.copyOf(all), Collections.unmodifiableMap(byId), List.copyOf(sortedTabs), Map.copyOf(requirements));
	}

	/** Server stopping: the next server may have other data packs. */
	public void clear() {
		state = EMPTY;
	}

	public boolean isReady() {
		return state.ready();
	}

	public int size() {
		return state.all().size();
	}

	/** Every shown advancement, by tab (story, nether, end, adventure, husbandry, then others), title and ID. */
	public List<AdvancementInfo> all() {
		return state.all();
	}

	public List<AdvancementTab> tabs() {
		return state.tabs();
	}

	public AdvancementInfo find(String id) {
		return id == null ? null : state.byId().get(id);
	}

	/** The requirement lists of criterion names of any advancement (shown or not); null if the server has no such one. */
	public List<List<String>> requirements(String id) {
		return id == null ? null : state.requirements().get(id);
	}

	/** The IDs of the shown advancements, in {@link #all()} order. */
	public Set<String> ids() {
		return state.byId().keySet();
	}

	/** The advancement itself, then its parents up to the root (or as far as MAX_DEPTH goes). */
	private static List<AdvancementHolder> ancestors(AdvancementHolder holder, Map<Identifier, AdvancementHolder> holders) {
		List<AdvancementHolder> chain = new ArrayList<>();
		chain.add(holder);
		AdvancementHolder current = holder;
		for (int step = 0; step < MAX_DEPTH; step++) {
			Identifier parentId = current.value().parent().orElse(null);
			AdvancementHolder parent = parentId == null ? null : holders.get(parentId);
			if (parent == null) {
				break;
			}
			chain.add(parent);
			current = parent;
		}
		return chain;
	}

	/** The first folder of the root's path: {@code story/root} -> {@code story}. */
	private static String tabId(Identifier root) {
		String path = root.getPath();
		if (path.isEmpty()) {
			return "other";
		}
		int slash = path.indexOf('/');
		return slash < 0 ? path : path.substring(0, slash);
	}

	private static AdvancementTab tab(String id, AdvancementHolder first, AdvancementHolder root) {
		if (root.value().display().isEmpty()) {
			return new AdvancementTab(id, id, icon(first.value().display().orElseThrow()), null);
		}
		DisplayInfo display = root.value().display().get();
		return new AdvancementTab(id, display.title().getString(), icon(display), background(display));
	}

	private static String icon(DisplayInfo display) {
		Holder<Item> item = display.icon() == null ? null : display.icon().item();
		String id = item == null ? null : item.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
		return id == null || id.equals("minecraft:air") ? "minecraft:barrier" : id;
	}

	private static String frame(DisplayInfo display) {
		return display.type() == null ? "task" : display.type().getSerializedName();
	}

	/** {@code minecraft:gui/advancements/backgrounds/stone} -> {@code stone}; null for any other texture. */
	private static String background(DisplayInfo display) {
		ClientAsset.ResourceTexture texture = display.background().orElse(null);
		if (texture == null) {
			return null;
		}
		Identifier id = texture.id();
		if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) || !id.getPath().startsWith(BACKGROUNDS)) {
			return null;
		}
		String name = id.getPath().substring(BACKGROUNDS.length());
		return name.isEmpty() || name.contains("/") ? null : name;
	}

	private static int tabRank(String tab) {
		int rank = TAB_ORDER.indexOf(tab);
		return rank < 0 ? TAB_ORDER.size() : rank;
	}

	private static List<List<String>> copy(List<List<String>> requirements) {
		List<List<String>> copy = new ArrayList<>(requirements.size());
		for (List<String> list : requirements) {
			copy.add(List.copyOf(list));
		}
		return List.copyOf(copy);
	}
}
