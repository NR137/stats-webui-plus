import { describe, expect, it } from 'vitest';
import type { PlayerDetail } from '#lib/types.ts';
import { fillPercent, matchingRows, uniqueAdvancements, vanillaGroupRows, winner } from './compare';

function player(name: string, extra: Partial<PlayerDetail> = {}): PlayerDetail {
	return {
		name,
		uuid: name,
		online: false,
		firstJoin: null,
		firstJoinEstimated: false,
		lastLogout: null,
		lastLogoutEstimated: false,
		playHours: 0,
		level: 0,
		levelRank: 1,
		actionsPerHour: 0,
		trackedPlayers: 2,
		sameName: [],
		skills: {},
		boards: {},
		statistics: {},
		advancements: { done: 0, total: 0, rank: 1, completed: {}, progress: {} },
		...extra
	};
}

describe('winner', () => {
	it('ties equal values and lets the higher win, or the lower where lower wins', () => {
		expect(winner(3, 3)).toBe('tie');
		expect(winner(4, 3)).toBe('a');
		expect(winner(4, 3, true)).toBe('b');
	});
});

describe('fillPercent', () => {
	it('uses the value itself when capped, else the share of the larger value', () => {
		expect(fillPercent(140, 20, true)).toBe(100);
		expect(fillPercent(-5, 20, true)).toBe(0);
		expect(fillPercent(5, 20)).toBe(25);
		expect(fillPercent(0, 0)).toBe(0);
	});
});

describe('matchingRows', () => {
	const rows = [
		{ id: 'minecraft:jump', label: 'Jumps' },
		{ id: 'minecraft:deaths', label: 'Deaths' }
	];

	it('keeps every row when the query is in a name of the section', () => {
		expect(matchingRows(rows, 'general', 'General statistics')).toHaveLength(2);
	});

	it('else keeps the rows whose label or ID has the query', () => {
		expect(matchingRows(rows, 'jump', 'General statistics').map((row) => row.label)).toEqual(['Jumps']);
		expect(matchingRows(rows, 'deaths', 'x').map((row) => row.label)).toEqual(['Deaths']);
	});
});

describe('vanillaGroupRows', () => {
	it('takes both players statistics, the larger value first, deaths lower wins', () => {
		const stat = (value: number) => ({ value, unit: 'count' as const, rank: 1 });
		const a = player('a', { statistics: { custom: { 'minecraft:jump': stat(5), 'minecraft:deaths': stat(2) } } });
		const b = player('b', { statistics: { custom: { 'minecraft:deaths': stat(9) } } });
		const rows = vanillaGroupRows(a, b, 'custom');
		expect(rows.map((row) => [row.id, row.a, row.b])).toEqual([
			['minecraft:deaths', 2, 9],
			['minecraft:jump', 5, 0]
		]);
		expect(rows[0].lowerWins).toBe(true);
		expect(rows[1].lowerWins).toBe(false);
	});
});

describe('uniqueAdvancements', () => {
	it('lists what only one has, with a stand-in for unknown ones', () => {
		const a = player('a', { advancements: { done: 2, total: 2, rank: 1, completed: { x: 1, y: 2 }, progress: {} } });
		const b = player('b', { advancements: { done: 1, total: 2, rank: 2, completed: { y: 3 }, progress: {} } });
		expect(uniqueAdvancements(a, b, new Map())).toEqual([
			{ id: 'x', title: 'x', description: '', icon: 'minecraft:experience_bottle', frame: 'task', tab: '' }
		]);
		expect(uniqueAdvancements(b, a, new Map())).toEqual([]);
	});
});
