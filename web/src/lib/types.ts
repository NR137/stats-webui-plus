// The answers of the mod's JSON API, version 2, as common/src/main/resources/assets/stats_webui_plus/openapi.yaml
// describes them (docs/api.md explains it for people). Values are plain numbers in the unit next to them, never
// preformatted: format.ts formats them. IDs in JSON are always full ("minecraft:jump"); times are epoch milliseconds,
// null when unknown.

/** An error answer (any status but 2xx). Match on `error`; `message` is for people. */
export type ApiError = {
	error: 'not_found' | 'method_not_allowed' | 'busy' | 'unavailable' | 'internal_error' | 'bad_request';
	message: string;
};

/** Epoch milliseconds (UTC); null when unknown. */
export type Time = number | null;

/** The unit of a value on a board (Unit). `skill` is a skill's value: 100 is very good, more is possible. */
export type Unit =
	| 'level'
	| 'skill'
	| 'hours'
	| 'km'
	| 'km_per_hour'
	| 'hearts'
	| 'count'
	| 'kills'
	| 'blocks'
	| 'mob_types'
	| 'kills_per_hour'
	| 'deaths_per_hour'
	| 'actions_per_hour'
	| 'ratio';

/** The unit of one of the game's statistics (StatisticUnit): distances, damage and times in `custom`, else counts. */
export type StatisticUnit = 'km' | 'hearts' | 'hours' | 'count';

/** The groups of the game's statistics (StatisticGroup), in the API's order. */
export type StatisticGroup =
	| 'custom'
	| 'killed'
	| 'killed_by'
	| 'mined'
	| 'crafted'
	| 'used'
	| 'broken'
	| 'picked_up'
	| 'dropped';

/** A player in answers. */
export type PlayerRef = {
	name: string;
	uuid: string;
};

/** How many of something are done (Progress). */
export type Progress = {
	done: number;
	total: number;
};

// --- Server ---

/** A text, or a text per language code (LocalizedText). */
export type LocalizedText = string | Record<string, string>;

/**
 * A custom menu button from the config's `links`. The API passes the config entries on as they are (plain objects);
 * these are the fields the website reads.
 */
export type MenuLink = {
	label: LocalizedText;
	url: string;
	newTab?: boolean;
	// main: an entry of the top menu; menu: a button in the row below it.
	place?: 'main' | 'menu';
	// A game item ID, the entry's icon in the top menu.
	icon?: string;
};

/**
 * A page from the config's `pages` that has a text (only `id` is promised by the API; the rest is the config entry
 * as written, and these are the fields the website reads).
 */
export type CustomPage = {
	id: string;
	label?: LocalizedText;
	// Where its entry goes: the top menu (main), the row below it (menu) or the footer.
	place?: string;
	// A game item ID, the entry's icon in the top menu.
	icon?: string;
};

/** `/api/status` `branding`: the look of the website, from the config. */
/** How the slogan is shown (config siteHeadingSloganStyle). */
export type SloganStyle = 'line' | 'plate' | 'above' | 'ribbon' | 'quote';

export type StatusBranding = {
	windowTitle?: LocalizedText;
	headerImagePosition?: 'top' | 'between' | 'bottom';
	siteHeading?: LocalizedText;
	siteHeadingSlogan?: LocalizedText;
	siteHeadingFont?: 'blocky' | 'gothic' | 'custom';
	siteHeadingSloganStyle?: SloganStyle;
	pageTitleFont?: 'tiny' | 'blocky' | 'minecraft';
	avatarPose?: 'idle' | 'walk' | 'run' | 'fly';
	avatarAngle?: number;
	avatarRotate?: boolean;
	statusBar?: boolean;
	serverAddress?: string;
};

/** `/api/status` (Status): the server's state and the website's settings. */
/** The server owner's font for the site heading (config siteHeadingCustomFont): the files that exist, best first. */
export type CustomFont = {
	name: string;
	// What the Licenses page says about it.
	license: LocalizedText;
	// The address of the font's license text (plain text), or null.
	licenseText: string | null;
	sources: { url: string; format: string }[];
};

export type StatusResponse = {
	trackedPlayers: number;
	// Names (lower case) more than one player has, sorted.
	nameClashes: string[];
	onlinePlayers: number;
	maxPlayers: number;
	branding: StatusBranding;
	// The header image's address, or null for none.
	headerImage: string | null;
	// The server's own font for the site heading, or null when it isn't used.
	customFont?: CustomFont | null;
	languages: string[];
	links: MenuLink[];
	pages: CustomPage[];
	// The id of the page that is the start page (config startPage), or null for the ranking.
	startPage?: string | null;
	defaultLanguage: string;
	gameAssets: 'ready' | 'preparing' | 'failed' | 'disabled';
};

/** `/api/pages/{id}` (Page): the page's config entry, and its text. */
export type CustomPageResponse = CustomPage & {
	// The text's language; null for the file for every language.
	language?: string | null;
	format: 'markdown' | 'html' | 'text';
	content: string;
};

// --- Players ---

/** One player of `/api/players`. */
export type PlayerSummary = PlayerRef & {
	// hours
	playHours: number;
	// level
	level: number;
	// actions_per_hour
	actionsPerHour: number;
	advancements: Progress;
};

/** `/api/players` (PlayerList): most play time first, then by name. */
export type PlayersResponse = {
	players: PlayerSummary[];
};

/** One part of a skill (Skill `parts`). */
export type SkillPart = {
	id: string;
	// The raw value (a count, km, types, ...).
	value: number;
	// The value that gives a score of 100.
	target: number;
	// The part's share of the skill (the shares add up to 1).
	weight: number;
	// The part's value, 100 at the target.
	score: number;
};

/** A player's skill (Skill): its value (unit `skill`) and what it is made of. */
export type Skill = {
	value: number;
	parts: SkillPart[];
};

/** A player's place on one board (PlayerBoard). */
export type PlayerBoard = {
	value: number;
	unit: Unit;
	category: string;
	rank: number;
};

/** One of the game's statistics a player raised (PlayerStatistic); ones at 0 are left out of the answer. */
export type PlayerStatistic = {
	value: number;
	unit: StatisticUnit;
	// One more than the number of players with a higher raw value; equal values share a rank.
	rank: number;
};

/** A player's advancements (PlayerAdvancements). */
export type PlayerAdvancements = {
	done: number;
	// How many advancements the server shows.
	total: number;
	// The rank on the board `advancements`.
	rank: number;
	// Every done advancement with its completion time, in the order of `/api/advancements`.
	completed: Record<string, Time>;
	// Shown advancements not done yet with at least two requirements: how many are met.
	progress: Record<string, Progress>;
};

/** `/api/players/{player}` (Player). */
export type PlayerDetail = PlayerRef & {
	online: boolean;
	firstJoin: Time;
	// True when the time is worked out from the game's files, not recorded at the join.
	firstJoinEstimated: boolean;
	lastLogout: Time;
	lastLogoutEstimated: boolean;
	// hours
	playHours: number;
	// level
	level: number;
	// The rank on the board `legends`.
	levelRank: number;
	// actions_per_hour
	actionsPerHour: number;
	// How many players there are (what the ranks are out of).
	trackedPlayers: number;
	// Other players whose name equals this one, ignoring case.
	sameName: PlayerRef[];
	// Each skill by ID (milestones, combat, exploration, mining, building, farming, survival).
	skills: Record<string, Skill>;
	// Every board of `/api/leaderboards` by ID.
	boards: Record<string, PlayerBoard>;
	// Per group, the statistics whose raw value isn't 0, by full ID; the others are 0 (see `/api/stats`).
	statistics: Partial<Record<StatisticGroup, Record<string, PlayerStatistic>>>;
	advancements: PlayerAdvancements;
};

/** `/api/players/{player}/advancements/{advancement}` (PlayerAdvancement). */
export type PlayerAdvancementResponse = AdvancementInfo & {
	player: PlayerRef;
	done: boolean;
	completedAt: Time;
	// The requirements as the game keeps them: each a list of criteria, any of which meets it.
	requirements: { criterion: string; at: Time }[][];
};

// --- Leaderboards ---

/** A board of `/api/leaderboards` (Board). */
export type LeaderboardBoard = {
	id: string;
	category: string;
	unit: Unit;
	// An item ID or one of the website's own icons ("special:kills-per-hour").
	icon: string;
	// Whether the leaderboards page lists it.
	listed: boolean;
	// Whether it is a row on the compare page.
	compare: boolean;
	// Whether fewer wins on the compare page; the board itself lists the highest value first too.
	lowerWins: boolean;
};

/** A category of the leaderboards page, with the IDs of its listed boards (look them up in `boards`). */
export type LeaderboardCategory = {
	id: string;
	icon: string;
	boards: string[];
};

/** `/api/leaderboards` (Leaderboards). */
export type LeaderboardsResponse = {
	categories: LeaderboardCategory[];
	// Every board, in the website's order.
	boards: LeaderboardBoard[];
};

/** A place on a board (Entry): highest value first, equal values get different ranks. */
export type LeaderboardEntry = PlayerRef & {
	rank: number;
	value: number;
};

/** `/api/leaderboards/{id}` and `/api/advancements/most` (Leaderboard). */
export type LeaderboardResponse = {
	id: string;
	category: string;
	unit: Unit;
	icon: string;
	lowerWins: boolean;
	// What the board adds up, group -> full statistic IDs; left out for a board of no combined statistics.
	parts?: Partial<Record<StatisticGroup, string[]>>;
	entries: LeaderboardEntry[];
};

// --- The game's statistics ---

/** The first place on one of the game's statistics. */
export type VanillaLeader = PlayerRef & {
	value: number;
};

/** One statistic of `/api/stats`. */
export type StatisticSummary = {
	id: string;
	// Left out when it is `count` (most statistics).
	unit?: StatisticUnit;
	// Left out when nobody has it.
	leader?: VanillaLeader;
};

/** `/api/stats` (StatisticsOverview): per group, its statistics sorted by ID. */
export type StatsResponse = {
	groups: Partial<Record<StatisticGroup, StatisticSummary[]>>;
};

/** `/api/stats/{group}/{statistic}` (StatisticBoard): without players at 0. */
export type StatisticBoardResponse = {
	group: StatisticGroup;
	id: string;
	unit: StatisticUnit;
	entries: LeaderboardEntry[];
};

// --- Advancements ---

/** An advancement tab (Tab). */
export type AdvancementTab = {
	id: string;
	title: string;
	icon: string;
	// The game's background texture's name ("stone", "nether", ...), or null for another one.
	background: string | null;
};

/** An advancement the server shows (Advancement). */
export type AdvancementInfo = {
	id: string;
	tab: string;
	// Plain text in the server's language.
	title: string;
	description: string;
	icon: string;
	frame: 'task' | 'goal' | 'challenge';
	hidden: boolean;
	// The experience points it rewards.
	experience: number;
	// The nearest shown ancestor; null for a tab's root.
	parent: string | null;
	// Column and row in the game's advancement tree.
	x: number;
	y: number;
	// How many players have it.
	holders: number;
};

/** `/api/advancements` (AdvancementList). */
export type AdvancementsResponse = {
	total: number;
	tabs: AdvancementTab[];
	advancements: AdvancementInfo[];
};

/** Someone who has an advancement (AdvancementDetail `entries`). */
export type AdvancementHolder = PlayerRef & {
	rank: number;
	completedAt: Time;
};

/** `/api/advancements/{advancement}` (AdvancementDetail): earliest first, unknown times last. */
export type AdvancementDetailResponse = AdvancementInfo & {
	entries: AdvancementHolder[];
};
