import { describe, expect, it } from 'vitest';
import { humanizeId, parseVanillaBoardId, shortId, vanillaBoardHref, vanillaGroups } from './vanillaStats';

describe('vanilla leaderboard ids', () => {
	it.each(vanillaGroups.map((group) => [group]))('%s round trips', (group) => {
		const parsed = parseVanillaBoardId(`${group}/minecraft:stone`);
		expect(parsed).toEqual({ group, id: 'minecraft:stone' });
		expect(vanillaBoardHref(group, 'minecraft:stone')).toBe(`/leaderboards/${group}/minecraft%3Astone`);
	});

	it('rejects unknown groups and ids without a group', () => {
		expect(parseVanillaBoardId('nonsense/minecraft:stone')).toBeNull();
		expect(parseVanillaBoardId('minecraft:stone')).toBeNull();
		expect(parseVanillaBoardId('/minecraft:stone')).toBeNull();
		expect(parseVanillaBoardId('')).toBeNull();
	});

	it('shortId drops the namespace', () => {
		expect(shortId('minecraft:stone')).toBe('stone');
		expect(shortId('stone')).toBe('stone');
	});

	it('humanizeId gives readable text', () => {
		const text = humanizeId('minecraft:diamond_ore');
		expect(text).not.toContain('_');
		expect(text).not.toContain('minecraft:');
		expect(text.toLowerCase()).toBe('diamond ore');
	});
});
