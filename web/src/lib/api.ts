import { idToPath } from '#lib/ids.ts';
import type {
	AdvancementDetailResponse,
	AdvancementsResponse,
	CustomPageResponse,
	LeaderboardResponse,
	LeaderboardsResponse,
	PlayerAdvancementResponse,
	PlayerDetail,
	PlayersResponse,
	StatisticBoardResponse,
	StatisticGroup,
	StatsResponse,
	StatusResponse
} from '#lib/types.ts';

// The client of the mod's JSON API, version 2 (docs/api.md, openapi.yaml): one function per endpoint the website uses.
// IDs in paths go in the website's own form (lib/ids.ts), which the API reads too; a player's name is encoded once.

/** The API says the thing asked for doesn't exist (a JSON 404, `not_found`). Pages check it with instanceof. */
export class NotFoundError extends Error {
	constructor() {
		super('not_found');
		this.name = 'NotFoundError';
	}
}

function isJson(response: Response): boolean {
	return (response.headers.get('content-type') ?? '').toLowerCase().includes('application/json');
}

async function request<T>(path: string): Promise<T> {
	const response = await fetch(path);
	// Only the mod answers in JSON: an HTML 404 or 502 comes from a proxy in between, so the thing may well exist.
	if (!isJson(response)) {
		throw new Error('unavailable');
	}
	if (response.status === 404) {
		throw new NotFoundError();
	}
	if (!response.ok) {
		throw new Error('unavailable');
	}
	return (await response.json()) as T;
}

export function getStatus(): Promise<StatusResponse> {
	return request('/api/status');
}

/** A configured page in the first of the given languages it has a text in. */
export function getCustomPage(id: string, languages: string[]): Promise<CustomPageResponse> {
	const lang = languages.map((code) => encodeURIComponent(code)).join(',');
	return request(`/api/pages/${encodeURIComponent(id)}?lang=${lang}`);
}

export function getPlayers(): Promise<PlayersResponse> {
	return request('/api/players');
}

/** A player by name, or by UUID where several share the name. */
export function getPlayer(player: string): Promise<PlayerDetail> {
	return request(`/api/players/${encodeURIComponent(player)}`);
}

export function getPlayerAdvancement(player: string, advancement: string): Promise<PlayerAdvancementResponse> {
	return request(`/api/players/${encodeURIComponent(player)}/advancements/${idToPath(advancement)}`);
}

export function getLeaderboards(): Promise<LeaderboardsResponse> {
	return request('/api/leaderboards');
}

/** One of the boards of getLeaderboards(); the game's statistics have theirs at getStatistic(). */
export function getLeaderboard(id: string): Promise<LeaderboardResponse> {
	return request(`/api/leaderboards/${encodeURIComponent(id)}`);
}

export function getStats(): Promise<StatsResponse> {
	return request('/api/stats');
}

/** The board of one of the game's statistics. */
export function getStatistic(group: StatisticGroup, id: string): Promise<StatisticBoardResponse> {
	return request(`/api/stats/${group}/${idToPath(id)}`);
}

export function getAdvancements(): Promise<AdvancementsResponse> {
	return request('/api/advancements');
}

/** The board of the most advancements. */
export function getAdvancementMost(): Promise<LeaderboardResponse> {
	return request('/api/advancements/most');
}

export function getAdvancement(id: string): Promise<AdvancementDetailResponse> {
	return request(`/api/advancements/${idToPath(id)}`);
}
