import { afterEach, describe, expect, it } from 'vitest';
import { branding } from '#lib/branding.svelte.ts';
import { playerKey, playerPath } from '#lib/players.ts';

// README, Floodgate support: players sharing a name (Floodgate cuts long Bedrock gamertags) are linked by UUID.
describe('playerPath', () => {
	afterEach(() => {
		branding.nameClashes = [];
	});

	it('links by name normally', () => {
		expect(playerPath('.Bedrock Pal', '00000000-0000-0000-0009-01f12bc1a5f3')).toBe('/players/.Bedrock%20Pal');
	});

	it('links by UUID where the name is shared, whatever its case', () => {
		branding.nameClashes = ['.longgamertagnam'];
		const uuid = '00000000-0000-0000-0009-01f12bc1a5f3';
		expect(playerPath('.LongGamertagNam', uuid)).toBe(`/players/${uuid}`);
		expect(playerKey('.LongGamertagNam', uuid)).toBe(uuid);
		expect(playerKey('Alex', 'x')).toBe('Alex');
	});
});
