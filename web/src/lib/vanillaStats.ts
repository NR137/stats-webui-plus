import { nameComparer, t } from '#lib/i18n/i18n.svelte.ts';
import type { MessageKey } from '#lib/i18n/messages/en.ts';
import { idToPath, pathToId } from '#lib/ids.ts';
import { officialName, type NameKind } from '#lib/mc/names.svelte.ts';
import type { PlayerStatistic, StatisticSummary } from '#lib/types.ts';

// The statistics Minecraft keeps itself (the game's statistics screen), in its groups. Their leaderboards live at
// /leaderboards/<group>/<id>, next to the hand-picked boards.

export const vanillaGroups = [
	'custom',
	'killed',
	'killed_by',
	'mined',
	'crafted',
	'used',
	'broken',
	'picked_up',
	'dropped'
] as const;

export type VanillaGroup = (typeof vanillaGroups)[number];

/** A player's statistic, also one at 0 (then without a rank). */
export type VanillaRow = {
	id: string;
	value: number;
	unit: string;
	rank?: number;
};

const GAME = 'minecraft:';

export function isVanillaGroup(text: string): text is VanillaGroup {
	return (vanillaGroups as readonly string[]).includes(text);
}

/** "minecraft:stone" -> "stone"; other namespaces stay. */
export function shortId(id: string): string {
	return id.startsWith(GAME) ? id.slice(GAME.length) : id;
}

/** "minecraft:diamond_ore" -> "Diamond Ore", for things the game has no name for (e.g. from a removed mod). */
export function humanizeId(id: string): string {
	return shortId(id)
		.split('_')
		.filter(Boolean)
		.map((word) => word.charAt(0).toUpperCase() + word.slice(1))
		.join(' ');
}

function nameKind(group: VanillaGroup): NameKind {
	switch (group) {
		case 'custom':
			return 'stat';
		case 'mined':
			return 'block';
		case 'killed':
		case 'killed_by':
			return 'entity';
		default:
			return 'item';
	}
}

/** The game's name of a statistic in the current language. */
export function vanillaLabel(group: VanillaGroup, id: string): string {
	return officialName(nameKind(group), id) ?? humanizeId(id);
}

/** Sorted by name in the current language, then by ID. */
export function sortVanillaIds(group: VanillaGroup, ids: string[]): string[] {
	const compare = nameComparer();
	// Each name once: groups hold up to 1,600 statistics.
	return ids
		.map((id) => ({ id, label: vanillaLabel(group, id) }))
		.sort((a, b) => compare(a.label, b.label) || a.id.localeCompare(b.id))
		.map((entry) => entry.id);
}

// The game's statistics screen has icons only for the item columns (mined, crafted, ...); the groups and the general
// statistics get items that stand for them.
export const vanillaGroupIcons: Record<VanillaGroup, string> = {
	custom: 'minecraft:writable_book',
	killed: 'minecraft:iron_sword',
	killed_by: 'special:player-killed',
	mined: 'minecraft:wooden_pickaxe',
	crafted: 'minecraft:anvil',
	used: 'minecraft:flint_and_steel',
	broken: 'special:broken-elytra',
	picked_up: 'minecraft:bundle',
	dropped: 'minecraft:dropper'
};

const customIcons: Record<string, string> = {
	animals_bred: 'wheat',
	aviate_one_cm: 'elytra',
	bell_ring: 'bell',
	boat_one_cm: 'oak_boat',
	clean_armor: 'leather_chestplate',
	clean_banner: 'white_banner',
	clean_shulker_box: 'shulker_box',
	climb_one_cm: 'ladder',
	crouch_one_cm: 'leather_boots',
	damage_absorbed: 'golden_apple',
	damage_blocked_by_shield: 'shield',
	damage_dealt: 'diamond_sword',
	damage_dealt_absorbed: 'diamond_sword',
	damage_dealt_resisted: 'netherite_sword',
	damage_resisted: 'iron_chestplate',
	damage_taken: 'wooden_sword',
	deaths: 'bone',
	drop: 'dropper',
	eat_cake_slice: 'cake',
	enchant_item: 'enchanted_book',
	fall_one_cm: 'feather',
	fill_cauldron: 'water_bucket',
	fish_caught: 'fishing_rod',
	fly_one_cm: 'firework_rocket',
	happy_ghast_one_cm: 'happy_ghast_spawn_egg',
	horse_one_cm: 'saddle',
	inspect_dispenser: 'dispenser',
	inspect_dropper: 'dropper',
	inspect_hopper: 'hopper',
	interact_with_anvil: 'anvil',
	interact_with_beacon: 'beacon',
	interact_with_blast_furnace: 'blast_furnace',
	interact_with_brewingstand: 'brewing_stand',
	interact_with_campfire: 'campfire',
	interact_with_cartography_table: 'cartography_table',
	interact_with_crafting_table: 'crafting_table',
	interact_with_furnace: 'furnace',
	interact_with_grindstone: 'grindstone',
	interact_with_lectern: 'lectern',
	interact_with_loom: 'loom',
	interact_with_smithing_table: 'smithing_table',
	interact_with_smoker: 'smoker',
	interact_with_stonecutter: 'stonecutter',
	jump: 'rabbit_foot',
	leave_game: 'oak_door',
	minecart_one_cm: 'minecart',
	mob_kills: 'iron_sword',
	nautilus_one_cm: 'nautilus_shell',
	open_barrel: 'barrel',
	open_chest: 'chest',
	open_enderchest: 'ender_chest',
	open_shulker_box: 'shulker_box',
	pig_one_cm: 'carrot_on_a_stick',
	play_noteblock: 'note_block',
	play_record: 'jukebox',
	play_time: 'clock',
	player_kills: 'player_head',
	pot_flower: 'flower_pot',
	raid_trigger: 'ominous_bottle',
	raid_win: 'crossbow',
	sleep_in_bed: 'red_bed',
	sleep_in_straw_bed: 'straw_bed',
	sneak_time: 'leather_boots',
	sprint_one_cm: 'sugar',
	strider_one_cm: 'warped_fungus_on_a_stick',
	swim_one_cm: 'heart_of_the_sea',
	talked_to_villager: 'emerald',
	target_hit: 'target',
	time_since_death: 'totem_of_undying',
	time_since_rest: 'phantom_membrane',
	total_world_time: 'clock',
	traded_with_villager: 'emerald',
	trigger_trapped_chest: 'trapped_chest',
	tune_noteblock: 'note_block',
	use_cauldron: 'cauldron',
	walk_on_water_one_cm: 'ice',
	walk_one_cm: 'leather_boots',
	walk_under_water_one_cm: 'turtle_helmet'
};

/** The picture of a statistic: the item, block or mob itself; for the general statistics an item standing for it. */
export function vanillaIcon(group: VanillaGroup, id: string): string {
	if (group === 'custom') {
		const item = id.startsWith(GAME) ? customIcons[id.slice(GAME.length)] : undefined;
		return item ? GAME + item : id;
	}
	if (group === 'killed' || group === 'killed_by') {
		return `entity:${id}`;
	}
	return id;
}

/** The address of a statistic's leaderboard. */
export function vanillaBoardHref(group: VanillaGroup, id: string): string {
	return `/leaderboards/${group}/${idToPath(id)}`;
}

/** "mined/stone" or "mined/minecraft:stone" -> the group and the full ID; null for any other board. */
export function parseVanillaBoardId(text: string): { group: VanillaGroup; id: string } | null {
	const slash = text.indexOf('/');
	if (slash <= 0) {
		return null;
	}
	const group = text.slice(0, slash);
	const id = text.slice(slash + 1);
	if (!isVanillaGroup(group) || !id) {
		return null;
	}
	return { group, id: pathToId(id) };
}

/** Whether a statistic matches a search: by its name, its ID or its ID without "minecraft:". */
export function matchesVanillaQuery(group: VanillaGroup, id: string, query: string): boolean {
	const needle = query.trim().toLowerCase();
	if (!needle) {
		return true;
	}
	return (
		vanillaLabel(group, id).toLowerCase().includes(needle) ||
		id.toLowerCase().includes(needle) ||
		shortId(id).toLowerCase().includes(needle)
	);
}

/** What a statistic counts, as a tooltip ("Mined: Stone."); the general statistics' names say it themselves. */
export function vanillaHint(group: VanillaGroup, id: string): string {
	if (group === 'custom') {
		return '';
	}
	return t(`stats.groups.${group}.hint` as MessageKey, { name: vanillaLabel(group, id) });
}

/**
 * A player's rows of one group: every statistic of `/api/stats`, the ones the player never raised at 0 (the API
 * leaves those out). The two answers can come from different refreshes, so a statistic the player raised for the
 * first time in between is added after the list.
 */
export function vanillaRows(
	listed: StatisticSummary[] | undefined,
	own: Record<string, PlayerStatistic> | undefined
): VanillaRow[] {
	const rows: VanillaRow[] = (listed ?? []).map((stat) => {
		const mine = own?.[stat.id];
		return mine
			? { id: stat.id, value: mine.value, unit: mine.unit, rank: mine.rank }
			: { id: stat.id, value: 0, unit: stat.unit ?? 'count' };
	});
	const seen = new Set(rows.map((row) => row.id));
	for (const [id, mine] of Object.entries(own ?? {})) {
		if (!seen.has(id)) {
			rows.push({ id, value: mine.value, unit: mine.unit, rank: mine.rank });
		}
	}
	return rows;
}

/**
 * The rows a player's group shows: without a search the ones above 0 (unless zeros are wanted), with one every row
 * whose name or ID matches; the highest value first, then by name and ID.
 */
export function visibleVanilla(
	group: VanillaGroup,
	rows: VanillaRow[],
	query: string,
	hideZeros: boolean
): VanillaRow[] {
	const searching = query.trim().length > 0;
	const compare = nameComparer();
	return rows
		.filter((row) => (searching ? matchesVanillaQuery(group, row.id, query) : !hideZeros || row.value > 0))
		.map((row) => ({ row, label: vanillaLabel(group, row.id) }))
		.sort((a, b) => b.row.value - a.row.value || compare(a.label, b.label) || a.row.id.localeCompare(b.row.id))
		.map((entry) => entry.row);
}
