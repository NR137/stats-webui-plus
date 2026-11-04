import { afterEach, describe, expect, it, vi } from 'vitest';
import { NotFoundError, getAdvancement, getLeaderboard, getPlayer } from './api';

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
		await expect(getPlayer('.Steve')).rejects.toBeInstanceOf(NotFoundError);
	});

	it('an HTML 404 is something in between, not a missing player', async () => {
		serve(() => new Response('<h1>Not Found</h1>', { status: 404, headers: { 'content-type': 'text/html' } }));
		const error = await getPlayer('Steve').catch((caught) => caught);
		expect(error).toBeInstanceOf(Error);
		expect(error).not.toBeInstanceOf(NotFoundError);
	});

	it('a busy server is not a missing player', async () => {
		serve(() => json(503, { error: 'busy', message: 'Try again' }));
		const error = await getPlayer('Steve').catch((caught) => caught);
		expect(error).not.toBeInstanceOf(NotFoundError);
	});

	it('asks for the whole name, encoded once', async () => {
		const fetch = serve(notFound);
		await getPlayer('+Steve.png').catch(() => {});
		expect(fetch).toHaveBeenCalledWith('/api/players/%2BSteve.png');
	});

	it('unknown advancement', async () => {
		const fetch = serve(notFound);
		await expect(getAdvancement('mymod:some.thing')).rejects.toBeInstanceOf(NotFoundError);
		expect(fetch).toHaveBeenCalledWith('/api/advancements/mymod/some.thing');
	});
});

describe('getLeaderboard', () => {
	it('an unknown vanilla stat is not found, without asking every player', async () => {
		const fetch = serve(notFound);
		await expect(getLeaderboard('mined/minecraft:nope')).rejects.toBeInstanceOf(NotFoundError);
		expect(fetch).toHaveBeenCalledTimes(1);
	});

	it('other errors still build the board from the players', async () => {
		const fetch = serve((path) => {
			if (path === '/api/players') {
				return json(200, { players: [] });
			}
			return json(503, { error: 'busy', message: 'Try again' });
		});
		const board = await getLeaderboard('mined/minecraft:stone');
		expect(board.entries).toEqual([]);
		expect(fetch).toHaveBeenCalledWith('/api/players');
	});
});

// CHANGELOG: never-raised stats come as compact ID lists (group -> rank -> namespace -> paths) and the web UI
// "rebuilds the identical rows".
describe('vanilla zero rows', () => {
	const row = (id: string, value: number, rank: number, unit = 'count') => ({
		id,
		value,
		display: String(value),
		unit,
		rank
	});

	it('come back as full rows, sorted like the server sorts IDs', async () => {
		serve(() =>
			json(200, {
				name: 'Steve',
				uuid: 'u',
				vanilla: {
					mined: [row('minecraft:dirt', 5, 1)],
					custom: [row('minecraft:aviate_one_cm', 0, 2, 'km')]
				},
				vanillaZero: {
					mined: { '3': { minecraft: ['stone', 'andesite'], mymod: ['ore'] } }
				}
			})
		);
		const player = await getPlayer('Steve');

		expect(player.vanilla?.mined).toEqual([
			row('minecraft:andesite', 0, 3),
			row('minecraft:dirt', 5, 1),
			row('minecraft:stone', 0, 3),
			row('mymod:ore', 0, 3)
		]);
		expect(player.vanilla?.custom).toEqual([row('minecraft:aviate_one_cm', 0, 2, 'km')]);
		expect(player).not.toHaveProperty('vanillaZero');
	});

	it('sorts by UTF-16 code units like Java, not by locale', async () => {
		serve(() =>
			json(200, {
				name: 'Steve',
				uuid: 'u',
				vanilla: { mined: [] },
				vanillaZero: { mined: { '1': { minecraft: ['b', 'B', 'a_b', 'a'] } } }
			})
		);
		const ids = (await getPlayer('Steve')).vanilla?.mined.map((stat) => stat.id);
		expect(ids).toEqual(['minecraft:B', 'minecraft:a', 'minecraft:a_b', 'minecraft:b']);
	});

	it('a player without zero lists keeps the rows as sent', async () => {
		serve(() => json(200, { name: 'Steve', uuid: 'u', vanilla: { mined: [row('minecraft:dirt', 5, 1)] } }));
		expect((await getPlayer('Steve')).vanilla?.mined).toEqual([row('minecraft:dirt', 5, 1)]);
	});
});
