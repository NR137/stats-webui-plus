import { compareAdvancementTitles } from '#lib/advancements.ts';
import { formatValue } from '#lib/format.ts';
import { boardHint, i18n, nameComparer, t, tOptional } from '#lib/i18n/i18n.svelte.ts';
import { skillBoard, skillColors, skillIcons, skillOrder, skillPartNumbers, type Skill } from '#lib/playerStats.ts';
import type { AdvancementInfo, AdvancementTab, LeaderboardsResponse, PlayerDetail } from '#lib/types.ts';
import { vanillaBoardHref, vanillaHint, vanillaIcon, vanillaLabel, type VanillaGroup } from '#lib/vanillaStats.ts';

// What the compare page shows, worked out from the two players; the components only draw it.

export type Tone = 'legends' | 'play-time' | 'actions-per-hour' | Skill;

export type Side = 'a' | 'b';

export type Winner = Side | 'tie';

/** One part of a skill: the numbers as text, the raw values to decide who wins it. */
export type RowPart = {
	id: string;
	label: string;
	a: string;
	b: string;
	target: string;
	aValue: number;
	bValue: number;
};

/** One value of both players. */
export type CompareRowData = {
	id: string;
	icon: string;
	label: string;
	href?: string;
	hint?: string;
	a: number;
	b: number;
	aText: string;
	bText: string;
	// Shown as levels, with the experience orb.
	coin?: boolean;
	// The value is the bar's percentage itself (a 0-to-100 scale).
	capped?: boolean;
	lowerWins?: boolean;
	tone?: Tone;
	parts?: RowPart[];
};

export type LeaderboardSection = {
	id: string;
	name: string;
	rows: CompareRowData[];
};

export type StatisticsGroup = {
	group: VanillaGroup;
	// The rows shown: the matching ones, capped while searching.
	rows: CompareRowData[];
	// How many rows the group has in all.
	total: number;
};

/** An advancement as the tiles take it; ones the catalog doesn't know get a stand-in. */
export type UniqueAdvancement = Pick<AdvancementInfo, 'id' | 'title' | 'description' | 'icon' | 'frame' | 'tab'> & {
	hidden?: boolean;
};

export type AdvancementGroup = {
	// '' for the advancements of tabs the catalog doesn't know.
	id: string;
	title: string;
	icon: string;
	background: string;
	a: UniqueAdvancement[];
	b: UniqueAdvancement[];
};

const FALLBACK_ICON = 'minecraft:experience_bottle';

export function winner(a: number, b: number, lowerWins = false): Winner {
	if (a === b) {
		return 'tie';
	}
	return a > b !== lowerWins ? 'a' : 'b';
}

/** How full a side's bar is, in percent. */
export function fillPercent(value: number, other: number, capped = false): number {
	if (capped) {
		return Math.min(100, Math.max(0, value));
	}
	return Math.min(100, Math.max(0, (value / Math.max(value, other, 0.0001)) * 100));
}

const toneColors: Record<Tone, string> = {
	legends: 'var(--color-xp)',
	'play-time': '#e6c35c',
	'actions-per-hour': '#f28aa0',
	...skillColors
};

export function toneColor(tone: Tone | undefined): string {
	return tone ? toneColors[tone] : 'var(--color-accent)';
}

/** The query (lower case) is in the row's label or ID. */
export function rowMatches(row: Pick<CompareRowData, 'id' | 'label'>, query: string): boolean {
	return !query || row.label.toLowerCase().includes(query) || row.id.toLowerCase().includes(query);
}

/** All rows when the query is in the section's name, else the matching ones. */
export function matchingRows<T extends Pick<CompareRowData, 'id' | 'label'>>(
	rows: T[],
	query: string,
	...names: string[]
): T[] {
	if (!query || names.some((name) => name.toLowerCase().includes(query))) {
		return rows;
	}
	return rows.filter((row) => rowMatches(row, query));
}

export function overviewRows(a: PlayerDetail, b: PlayerDetail): CompareRowData[] {
	return [
		{
			id: 'legends',
			icon: 'minecraft:nether_star',
			label: t('players.columns.score'),
			href: '/leaderboards/legends',
			hint: boardHint('legends'),
			a: a.level,
			b: b.level,
			aText: formatValue(a.level, 'level'),
			bText: formatValue(b.level, 'level'),
			coin: true,
			capped: true,
			tone: 'legends'
		},
		{
			id: 'play-time',
			icon: 'minecraft:clock',
			label: t('player.playTime'),
			href: '/leaderboards/play-time',
			hint: boardHint('play-time'),
			a: a.playHours,
			b: b.playHours,
			aText: formatValue(a.playHours, 'hours'),
			bText: formatValue(b.playHours, 'hours'),
			tone: 'play-time'
		},
		{
			id: 'actions-per-hour',
			icon: 'minecraft:sugar',
			label: t('player.actionsPerHour'),
			href: '/leaderboards/grinders',
			hint: boardHint('grinders'),
			a: a.actionsPerHour,
			b: b.actionsPerHour,
			aText: formatValue(a.actionsPerHour, 'actions_per_hour'),
			bText: formatValue(b.actionsPerHour, 'actions_per_hour'),
			tone: 'actions-per-hour'
		},
		{
			id: 'advancements',
			icon: FALLBACK_ICON,
			label: t('player.advancements'),
			href: '/advancements/most',
			hint: boardHint('advancements'),
			a: a.advancements.done,
			b: b.advancements.done,
			aText: t('advancements.doneOf', { done: a.advancements.done, total: a.advancements.total }),
			bText: t('advancements.doneOf', { done: b.advancements.done, total: b.advancements.total })
		}
	];
}

/** "4,120 / 5,000 km" -> the number and the target. */
function splitPartNumbers(text: string): { number: string; target: string } {
	const at = text.indexOf(' / ');
	return at < 0 ? { number: text, target: '' } : { number: text.slice(0, at), target: text.slice(at + 3) };
}

/** The skills either player has, in the game's order, with A's parts. */
export function skillRows(a: PlayerDetail, b: PlayerDetail): CompareRowData[] {
	const locale = i18n.locale;
	return skillOrder
		.filter((id) => a.skills[id] || b.skills[id])
		.map((id) => {
			const aValue = a.skills[id]?.value ?? 0;
			const bValue = b.skills[id]?.value ?? 0;
			const parts = (a.skills[id]?.parts ?? []).map((part): RowPart => {
				const other = b.skills[id]?.parts.find((candidate) => candidate.id === part.id) ?? { ...part, value: 0 };
				const own = splitPartNumbers(skillPartNumbers(part, locale));
				return {
					id: part.id,
					label: tOptional(`skills.parts.${part.id}`, part.id),
					a: own.number,
					b: splitPartNumbers(skillPartNumbers(other, locale)).number,
					target: own.target,
					aValue: part.value,
					bValue: other.value
				};
			});
			return {
				id,
				icon: skillIcons[id],
				label: tOptional(`skills.${id}.name`, id),
				href: `/leaderboards/${skillBoard(id)}`,
				hint: boardHint(skillBoard(id)),
				a: aValue,
				b: bValue,
				aText: String(Math.floor(aValue)),
				bText: String(Math.floor(bValue)),
				capped: true,
				tone: id,
				parts
			};
		});
}

/** The compared boards per category of the leaderboards page; the skills have their own section. */
export function leaderboardSections(
	a: PlayerDetail,
	b: PlayerDetail,
	leaderboards: LeaderboardsResponse | null
): LeaderboardSection[] {
	if (!leaderboards) {
		return [];
	}
	const skillBoards = new Set<string>(skillOrder.map(skillBoard));
	const boards = new Map(leaderboards.boards.map((board) => [board.id, board]));
	const text = (player: PlayerDetail, id: string) => {
		const own = player.boards[id];
		return own ? formatValue(own.value, own.unit) : '0';
	};
	return leaderboards.categories
		.map((category) => ({
			id: category.id,
			name: tOptional(`boards.categories.${category.id}`, category.id),
			rows: category.boards
				.flatMap((id) => {
					const board = boards.get(id);
					return board && board.listed && board.compare && !skillBoards.has(id) ? [board] : [];
				})
				.map(
					(board): CompareRowData => ({
						id: board.id,
						icon: board.icon,
						label: tOptional(`boards.${board.id}.name`, board.id),
						href: `/leaderboards/${board.id}`,
						hint: boardHint(board.id),
						a: a.boards[board.id]?.value ?? 0,
						b: b.boards[board.id]?.value ?? 0,
						aText: text(a, board.id),
						bText: text(b, board.id),
						lowerWins: board.lowerWins
					})
				)
		}))
		.filter((section) => section.rows.length > 0);
}

/** Every statistic of the group either player has above 0, the highest first, then by name and ID. */
export function vanillaGroupRows(a: PlayerDetail, b: PlayerDetail, group: VanillaGroup): CompareRowData[] {
	const own = a.statistics[group] ?? {};
	const other = b.statistics[group] ?? {};
	const ids = new Set(
		[...Object.entries(own), ...Object.entries(other)].filter(([, stat]) => stat.value > 0).map(([id]) => id)
	);
	const compare = nameComparer();
	return [...ids]
		.map((id): CompareRowData => {
			const statA = own[id];
			const statB = other[id];
			// A side without the statistic is at 0, in the other side's unit.
			const unit = statA?.unit ?? statB?.unit ?? 'count';
			return {
				id,
				icon: vanillaIcon(group, id),
				label: vanillaLabel(group, id),
				href: vanillaBoardHref(group, id),
				hint: vanillaHint(group, id),
				a: statA?.value ?? 0,
				b: statB?.value ?? 0,
				aText: formatValue(statA?.value ?? 0, statA?.unit ?? unit),
				bText: formatValue(statB?.value ?? 0, statB?.unit ?? unit),
				lowerWins: group === 'killed_by' || id === 'minecraft:deaths'
			};
		})
		.sort(
			(x, y) =>
				Math.max(y.a, y.b) - Math.max(x.a, x.b) ||
				compare(x.label, y.label) ||
				(x.id < y.id ? -1 : x.id > y.id ? 1 : 0)
		);
}

/** What `own` has done and `other` hasn't, sorted by title. */
export function uniqueAdvancements(
	own: PlayerDetail,
	other: PlayerDetail,
	catalog: ReadonlyMap<string, AdvancementInfo>
): UniqueAdvancement[] {
	const theirs = other.advancements.completed;
	return Object.keys(own.advancements.completed)
		.filter((id) => !Object.hasOwn(theirs, id))
		.map(
			(id): UniqueAdvancement =>
				catalog.get(id) ?? { id, title: id, description: '', icon: FALLBACK_ICON, frame: 'task', tab: '' }
		)
		.sort(compareAdvancementTitles);
}

/** One group per tab of the catalog in its order, then the other tabs' advancements if there are any. */
export function advancementGroups(
	tabs: AdvancementTab[],
	titleOf: (tab: AdvancementTab) => string,
	a: UniqueAdvancement[],
	b: UniqueAdvancement[]
): AdvancementGroup[] {
	const known = new Set(tabs.map((tab) => tab.id));
	const groups: AdvancementGroup[] = tabs.map((tab) => ({
		id: tab.id,
		title: titleOf(tab),
		icon: tab.icon,
		background: tab.background ?? '',
		a: a.filter((item) => item.tab === tab.id),
		b: b.filter((item) => item.tab === tab.id)
	}));
	const other: AdvancementGroup = {
		id: '',
		title: t('compare.otherAdvancements'),
		icon: FALLBACK_ICON,
		background: '',
		a: a.filter((item) => !known.has(item.tab)),
		b: b.filter((item) => !known.has(item.tab))
	};
	if (other.a.length > 0 || other.b.length > 0) {
		groups.push(other);
	}
	return groups;
}
