import { afterEach, describe, expect, it, vi } from 'vitest';
import {
	NotFoundError,
	getAdvancement,
	getCustomPage,
	getLeaderboard,
	getPlayer,
	getPlayerAdvancement,
	getStatistic
} from './api';

const json = (status: number, body: unknown) =>
	new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
const notFound = () => json(404, { error: 'not_found', message: 'Player not found' });

function serve(answer: (path: string) => Response) {
	const fetch = vi.fn(async (path: string) => answer(path));
	vi.stubGlobal('fetch', fetch);
	return fetch;
}

afterEach(() => {
	vi.unstubAllGlobals();
});

describe('not found or broken', () => {
	it("the mod's 404 means the player does not exist", async () => {
		serve(notFound);
		const error = await getPlayer('.Steve').catch((caught) => caught);
		expect(error).toBeInstanceOf(NotFoundError);
		expect(error.message).toBe('not_found');
	});

	it('an HTML 404 is something in between, not a missing player', async () => {
		serve(() => new Response('<h1>Not Found</h1>', { status: 404, headers: { 'content-type': 'text/html' } }));
		const error = await getPlayer('Steve').catch((caught) => caught);
		expect(error).toBeInstanceOf(Error);
		expect(error).not.toBeInstanceOf(NotFoundError);
		expect(error.message).toBe('unavailable');
	});

	it('a busy server is not a missing player', async () => {
		serve(() => json(503, { error: 'busy', message: 'Try again' }));
		const error = await getPlayer('Steve').catch((caught) => caught);
		expect(error).not.toBeInstanceOf(NotFoundError);
		expect(error.message).toBe('unavailable');
	});

	it('an HTML page with 200 (a proxy, or the website for an unknown path) is no answer either', async () => {
		serve(() => new Response('<!doctype html>', { status: 200, headers: { 'content-type': 'text/html' } }));
		const error = await getPlayer('Steve').catch((caught) => caught);
		expect(error).not.toBeInstanceOf(NotFoundError);
		expect(error.message).toBe('unavailable');
	});

	it('answers with the JSON as sent', async () => {
		serve(() => json(200, { name: 'Steve', uuid: 'u' }));
		expect(await getPlayer('Steve')).toEqual({ name: 'Steve', uuid: 'u' });
	});

	it('no fallback: a board that fails is not built from anything else', async () => {
		const fetch = serve(() => json(503, { error: 'busy', message: 'Try again' }));
		await expect(getLeaderboard('diamonds-mined')).rejects.toThrow('unavailable');
		expect(fetch).toHaveBeenCalledTimes(1);
	});
});

describe('paths', () => {
	it('asks for the whole name, encoded once', async () => {
		const fetch = serve(notFound);
		await getPlayer('+Steve.png').catch(() => {});
		expect(fetch).toHaveBeenCalledWith('/api/players/%2BSteve.png');
	});

	it('advancements in the website ID form', async () => {
		const fetch = serve(notFound);
		await expect(getAdvancement('mymod:some.thing')).rejects.toBeInstanceOf(NotFoundError);
		expect(fetch).toHaveBeenCalledWith('/api/advancements/mymod:some.thing');
		await getAdvancement('minecraft:story/mine_stone').catch(() => {});
		expect(fetch).toHaveBeenLastCalledWith('/api/advancements/story/mine_stone');
	});

	it("a player's advancement", async () => {
		const fetch = serve(notFound);
		await getPlayerAdvancement('Alex Two', 'minecraft:adventure/adventuring_time').catch(() => {});
		expect(fetch).toHaveBeenCalledWith('/api/players/Alex%20Two/advancements/adventure/adventuring_time');
	});

	it("a game statistic's board is under /api/stats, not /api/leaderboards", async () => {
		const fetch = serve(notFound);
		await expect(getStatistic('mined', 'minecraft:stone')).rejects.toBeInstanceOf(NotFoundError);
		expect(fetch).toHaveBeenCalledWith('/api/stats/mined/stone');
		expect(fetch).toHaveBeenCalledTimes(1);
		await getStatistic('killed', 'somemod:thing').catch(() => {});
		expect(fetch).toHaveBeenLastCalledWith('/api/stats/killed/somemod:thing');
	});

	it('a custom page in the given languages, each encoded', async () => {
		const fetch = serve(notFound);
		await getCustomPage('imprint', ['de', 'en', 'x&y']).catch(() => {});
		expect(fetch).toHaveBeenCalledWith('/api/pages/imprint?lang=de,en,x%26y');
	});
});
