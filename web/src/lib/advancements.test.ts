import { describe, expect, it } from 'vitest';
import { advancementHref } from './advancements';

// Route src/routes/advancements/[...id]: the namespace and path become URL segments.
describe('advancementHref', () => {
	it('turns the id into a path', () => {
		expect(advancementHref('minecraft:story/mine_stone')).toBe('/advancements/minecraft/story/mine_stone');
	});

	it('encodes each segment', () => {
		expect(advancementHref('my mod:a b/c?d')).toBe('/advancements/my%20mod/a%20b/c%3Fd');
	});
});
