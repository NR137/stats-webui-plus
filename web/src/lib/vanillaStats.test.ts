import { afterEach, describe, expect, it } from 'vitest';
import { i18n } from '#lib/i18n/i18n.svelte.ts';
import { useNames } from '#lib/mc/names.svelte.ts';
import {
	humanizeId,
	matchesVanillaQuery,
	parseVanillaBoardId,
	shortId,
	sortVanillaIds,
	vanillaBoardHref,
	vanillaGroups,
	vanillaHint,
	vanillaIcon,
	vanillaLabel,
	vanillaRows,
	visibleVanilla
} from './vanillaStats';

afterEach(() => {
	useNames('en', {});
	i18n.locale = 'en';
});

describe('vanilla leaderboard ids', () => {
	it.each(vanillaGroups.map((group) => [group]))('%s round trips', (group) => {
		const parsed = parseVanillaBoardId(`${group}/minecraft:stone`);
		expect(parsed).toEqual({ group, id: 'minecraft:stone' });
		expect(vanillaBoardHref(group, 'minecraft:stone')).toBe(`/leaderboards/${group}/stone`);
	});

	it('reads the address form too', () => {
		expect(parseVanillaBoardId('mined/stone')).toEqual({ group: 'mined', id: 'minecraft:stone' });
		expect(parseVanillaBoardId('mined/somemod:ore')).toEqual({ group: 'mined', id: 'somemod:ore' });
		expect(vanillaBoardHref('mined', 'somemod:ore')).toBe('/leaderboards/mined/somemod:ore');
	});

	it('rejects unknown groups and ids without a group', () => {
		expect(parseVanillaBoardId('nonsense/minecraft:stone')).toBeNull();
		expect(parseVanillaBoardId('minecraft:stone')).toBeNull();
		expect(parseVanillaBoardId('/minecraft:stone')).toBeNull();
		expect(parseVanillaBoardId('mined/')).toBeNull();
		expect(parseVanillaBoardId('')).toBeNull();
	});

	it('shortId drops the namespace', () => {
		expect(shortId('minecraft:stone')).toBe('stone');
		expect(shortId('stone')).toBe('stone');
		expect(shortId('somemod:ore')).toBe('somemod:ore');
	});

	it('humanizeId gives readable text', () => {
		expect(humanizeId('minecraft:diamond_ore')).toBe('Diamond Ore');
	});
});

describe('names and icons', () => {
	it("uses the game's names by the group's kind, else the ID made readable", () => {
		useNames('en', {
			stat: { 'minecraft:jump': 'Jumps' },
			block: { 'minecraft:stone': 'Stone' },
			entity: { 'minecraft:zombie': 'Zombie' },
			item: { 'minecraft:stone': 'Stone (item)' }
		});
		expect(vanillaLabel('custom', 'minecraft:jump')).toBe('Jumps');
		expect(vanillaLabel('mined', 'minecraft:stone')).toBe('Stone');
		expect(vanillaLabel('crafted', 'minecraft:stone')).toBe('Stone (item)');
		expect(vanillaLabel('killed_by', 'minecraft:zombie')).toBe('Zombie');
		expect(vanillaLabel('killed', 'minecraft:husk')).toBe('Husk');
		expect(vanillaHint('custom', 'minecraft:jump')).toBe('');
		expect(vanillaHint('mined', 'minecraft:stone')).toContain('Stone');
	});

	it('icons', () => {
		expect(vanillaIcon('custom', 'minecraft:jump')).toBe('minecraft:rabbit_foot');
		expect(vanillaIcon('custom', 'minecraft:walk_under_water_one_cm')).toBe('minecraft:turtle_helmet');
		expect(vanillaIcon('custom', 'somemod:thing')).toBe('somemod:thing');
		expect(vanillaIcon('killed', 'minecraft:zombie')).toBe('entity:minecraft:zombie');
		expect(vanillaIcon('mined', 'minecraft:stone')).toBe('minecraft:stone');
	});

	it('sorts by name, then by ID', () => {
		useNames('en', { block: { 'minecraft:b': 'Same', 'minecraft:a': 'Same', 'minecraft:c': 'Alpha' } });
		expect(sortVanillaIds('mined', ['minecraft:b', 'minecraft:c', 'minecraft:a'])).toEqual([
			'minecraft:c',
			'minecraft:a',
			'minecraft:b'
		]);
	});

	it('searches the name, the ID and the short ID', () => {
		useNames('en', { block: { 'minecraft:stone': 'Smooth Rock' } });
		expect(matchesVanillaQuery('mined', 'minecraft:stone', '')).toBe(true);
		expect(matchesVanillaQuery('mined', 'minecraft:stone', 'ROCK')).toBe(true);
		expect(matchesVanillaQuery('mined', 'minecraft:stone', 'minecraft:st')).toBe(true);
		expect(matchesVanillaQuery('mined', 'minecraft:stone', 'stone')).toBe(true);
		expect(matchesVanillaQuery('mined', 'minecraft:stone', 'dirt')).toBe(false);
	});
});

// API v2 leaves out the statistics a player never raised; the website fills them in from /api/stats.
describe("a player's rows", () => {
	it('every listed statistic, zeros in their unit and without a rank', () => {
		const rows = vanillaRows(
			[
				{ id: 'minecraft:aviate_one_cm', unit: 'km' },
				{ id: 'minecraft:jump', leader: { name: 'A', uuid: 'a', value: 9 } },
				{ id: 'minecraft:walk_one_cm', unit: 'km' }
			],
			{ 'minecraft:walk_one_cm': { value: 1181.4, unit: 'km', rank: 3 } }
		);
		expect(rows).toEqual([
			{ id: 'minecraft:aviate_one_cm', value: 0, unit: 'km' },
			{ id: 'minecraft:jump', value: 0, unit: 'count' },
			{ id: 'minecraft:walk_one_cm', value: 1181.4, unit: 'km', rank: 3 }
		]);
	});

	it("keeps a statistic the list doesn't have yet", () => {
		expect(vanillaRows([], { 'minecraft:jump': { value: 2, unit: 'count', rank: 1 } })).toEqual([
			{ id: 'minecraft:jump', value: 2, unit: 'count', rank: 1 }
		]);
		expect(vanillaRows(undefined, undefined)).toEqual([]);
	});

	it('shows the highest first, zeros only when asked, and every match while searching', () => {
		useNames('en', { block: { 'minecraft:a': 'Beta', 'minecraft:b': 'Alpha' } });
		const rows = [
			{ id: 'minecraft:c', value: 0, unit: 'count' },
			{ id: 'minecraft:a', value: 5, unit: 'count', rank: 1 },
			{ id: 'minecraft:b', value: 5, unit: 'count', rank: 2 },
			{ id: 'minecraft:d', value: 9, unit: 'count', rank: 1 }
		];
		const ids = (query: string, hideZeros: boolean) =>
			visibleVanilla('mined', rows, query, hideZeros).map((row) => row.id);
		expect(ids('', true)).toEqual(['minecraft:d', 'minecraft:b', 'minecraft:a']);
		expect(ids('', false)).toEqual(['minecraft:d', 'minecraft:b', 'minecraft:a', 'minecraft:c']);
		expect(ids('minecraft:c', true)).toEqual(['minecraft:c']);
		expect(ids('alpha', true)).toEqual(['minecraft:b']);
	});
});
