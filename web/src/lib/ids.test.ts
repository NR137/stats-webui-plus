import { describe, expect, it } from 'vitest';
import { idToPath, pathToId } from './ids';

describe('game IDs in addresses', () => {
	it("leaves out the game's own namespace", () => {
		expect(idToPath('minecraft:story/root')).toBe('story/root');
		expect(idToPath('minecraft:jump')).toBe('jump');
		expect(pathToId('story/root')).toBe('minecraft:story/root');
		expect(pathToId('jump')).toBe('minecraft:jump');
	});

	it("keeps other namespaces with a plain colon", () => {
		expect(idToPath('somemod:quests/first')).toBe('somemod:quests/first');
		expect(pathToId('somemod:quests/first')).toBe('somemod:quests/first');
	});

	it('encodes what an address cannot hold, part by part', () => {
		expect(idToPath('somemod:a b/c#d')).toBe('somemod:a%20b/c%23d');
	});
});
