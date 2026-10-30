export type PlayerSummary = {
	name: string;
	uuid: string;
	playHours?: number;
	playHoursDisplay?: string;
	championScore?: number;
	championDisplay?: string;
	scorePerHour?: number;
	scorePerHourDisplay?: string;
	advancements?: {
		done: number;
		total: number;
		rank?: number;
		ids?: string[];
	};
};

export type PlayerStat = {
	value: number;
	display: string;
	unit: string;
	category: string;
	rank: number;
};

export type PlayerDetail = PlayerSummary & {
	championScore: number;
	championDisplay: string;
	championRank: number;
	playHours: number;
	playHoursDisplay: string;
	scorePerHour: number;
	scorePerHourDisplay: string;
	trackedPlayers: number;
	// Epoch milliseconds (UTC), shown in the visitor's time zone; null when unknown.
	firstJoin: number | null;
	firstJoinEstimated: boolean;
	lastLogout: number | null;
	lastLogoutEstimated: boolean;
	online: boolean;
	grades: Record<string, number>;
	stats: Record<string, PlayerStat>;
	vanilla?: Record<string, VanillaStat[]>;
	advancements?: {
		done: number;
		total: number;
		rank: number;
		ids: string[];
		// Completion time per finished advancement (epoch ms), where Minecraft recorded one.
		times?: Record<string, number>;
	};
};

export type VanillaStat = {
	id: string;
	value: number;
	display: string;
	unit: string;
	rank: number;
};

export type PlayersResponse = {
	players: PlayerSummary[];
};

export type HealthResponse = {
	ok: boolean;
	mod: string;
	version: string;
};

// Config text: plain, or per language code ({ "en": "Map", "de": "Karte" }).
export type LocalizedText = string | Record<string, string>;

export type MenuLink = {
	label: LocalizedText;
	url: string;
	newTab: boolean;
};

export type StatusResponse = {
	bind: string;
	trackedPlayers: number;
	branding?: {
		windowTitle: LocalizedText;
		headerImagePosition?: string;
		siteHeading?: LocalizedText;
		siteHeadingTagline?: LocalizedText;
		siteHeadingTaglinePulse?: boolean;
		siteHeadingSlogan?: LocalizedText;
		avatarPose?: string;
		avatarAngle?: number;
		avatarRotate?: boolean;
		statusBar?: boolean;
		serverAddress?: string;
	};
	onlinePlayers?: number;
	maxPlayers?: number;
	defaultLanguage?: string;
	languages?: string[];
	// Custom menu buttons from the config; only ones with a URL are sent.
	links?: MenuLink[];
	// URL of the configured header image; absent when there is none.
	headerImage?: string;
};

export type LeaderboardBoard = {
	id: string;
	category: string;
	unit: string;
	icon: string;
	listed: boolean;
	compare: boolean;
	lowerWins: boolean;
};

export type LeaderboardCategory = {
	id: string;
	icon: string;
	boards: LeaderboardBoard[];
};

export type LeaderboardsResponse = {
	categories: LeaderboardCategory[];
	boards: LeaderboardBoard[];
	vanilla?: Partial<Record<string, string[]>>;
};

export type LeaderboardEntry = {
	rank: number;
	name: string;
	uuid: string;
	value: number;
	display: string;
};

export type LeaderboardResponse = {
	id: string;
	title: string;
	category: string;
	unit: string;
	icon?: string;
	lowerWins?: boolean;
	entries: LeaderboardEntry[];
};

export type Crown = {
	name: string;
	uuid: string;
	score: number;
	display: string;
} | null;

export type CrownsResponse = {
	champion: Crown;
	dedicated: Crown;
	efficient: Crown;
};

export type AdvancementInfo = {
	id: string;
	tab: string;
	title: string;
	description: string;
	icon: string;
	frame: string;
	hidden: boolean;
	holders: number;
};

export type AdvancementTab = {
	id: string;
	title: string;
	icon: string;
};

export type AdvancementsCatalogResponse = {
	total: number;
	tabs: AdvancementTab[];
	advancements: AdvancementInfo[];
};

export type AdvancementDetailResponse = AdvancementInfo & {
	entries: LeaderboardEntry[];
};

export type ApiError = {
	error: string;
	message: string;
};
