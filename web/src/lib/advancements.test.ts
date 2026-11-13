import { afterEach, describe, expect, it } from 'vitest';
import { advancementHref, advancementIcon, advancementMatch, textMatches } from './advancements';
import { i18n } from './i18n/i18n.svelte';
import { useIcons } from './mc/items.svelte';

// The entries of the server's icon table (/mc/icons.json) these tests need: the special named items' own icons.
useIcons({
	'advancement:minecraft:adventure/voluntary_exile': '/mc/iso/ominous_banner.png',
	'advancement:minecraft:adventure/hero_of_the_village': '/mc/iso/ominous_banner.png'
});

// Route src/routes/advancements/[...id]: the namespace and path become URL segments.
describe('advancementHref', () => {
	it('turns the id into a path', () => {
		expect(advancementHref('minecraft:story/mine_stone')).toBe('/advancements/story/mine_stone');
	});

	it('encodes each segment', () => {
		expect(advancementHref('my mod:a b/c?d')).toBe('/advancements/my%20mod:a%20b/c%3Fd');
	});
});

// Voluntary Exile and Hero of the Village show the Ominous Banner, as in the game, not a white banner.
describe('advancementIcon', () => {
	it('prefers the icon of a special named item', () => {
		for (const id of ['minecraft:adventure/voluntary_exile', 'minecraft:adventure/hero_of_the_village']) {
			expect(advancementIcon(id, 'minecraft:white_banner')).toBe(`advancement:${id}`);
		}
	});

	it('uses the item otherwise', () => {
		expect(advancementIcon('minecraft:story/mine_stone', 'minecraft:wooden_pickaxe')).toBe('minecraft:wooden_pickaxe');
		expect(advancementIcon('mymod:some/advancement', 'minecraft:stone')).toBe('minecraft:stone');
	});
});

// the advancement search looks at titles and descriptions in the visitor's language and the English
// title; short words only where a word starts, title matches before description matches; the ID isn't searched.
describe('advancement search', () => {
	afterEach(() => {
		i18n.locale = 'en';
	});

	it('matches short words only at the start of a word', () => {
		expect(textMatches('Adventuring Time', 'ad')).toBe(true);
		expect(textMatches('Upgrade your Pickaxe', 'ad')).toBe(false);
		expect(textMatches('Upgrade your Pickaxe', 'grade')).toBe(true);
	});

	it('needs every word, ignoring case and accents', () => {
		expect(textMatches('Isn’t It Iron Pick?', 'iron pick')).toBe(true);
		expect(textMatches('Isn’t It Iron Pick?', 'iron diamond')).toBe(false);
		expect(textMatches('Übertreib es nicht', 'ubertreib')).toBe(true);
	});

	it('ranks a title match above a description match and leaves out the ID', () => {
		expect(advancementMatch('minecraft:story/iron_tools', 'Isn’t It Iron Pick?', 'Upgrade your Pickaxe', 'iron')).toBe(2);
		expect(advancementMatch('minecraft:story/smelt_iron', 'Acquire Hardware', 'Smelt an Iron Ingot', 'iron')).toBe(1);
		// "adventure" is only in the ID (minecraft:adventure/...).
		expect(advancementMatch('minecraft:adventure/throw_trident', 'A Throwaway Joke', 'Throw a Trident at something.', 'adventure')).toBe(0);
	});

	it('also finds the English title in another language', () => {
		i18n.locale = 'de';
		expect(advancementMatch('minecraft:story/iron_tools', 'Isn’t It Iron Pick?', '', 'iron pick')).toBe(2);
	});
});
