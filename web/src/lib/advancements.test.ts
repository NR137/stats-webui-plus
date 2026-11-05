import { describe, expect, it } from 'vitest';
import { advancementHref, advancementIcon } from './advancements';

// Route src/routes/advancements/[...id]: the namespace and path become URL segments.
describe('advancementHref', () => {
	it('turns the id into a path', () => {
		expect(advancementHref('minecraft:story/mine_stone')).toBe('/advancements/minecraft/story/mine_stone');
	});

	it('encodes each segment', () => {
		expect(advancementHref('my mod:a b/c?d')).toBe('/advancements/my%20mod/a%20b/c%3Fd');
	});
});

// CHANGELOG 1.4.0: Voluntary Exile and Hero of the Village show the Ominous Banner, as in the game, not a white banner.
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
