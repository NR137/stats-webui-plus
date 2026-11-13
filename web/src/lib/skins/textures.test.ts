import { describe, expect, it } from 'vitest';
import { forwarded, isOpaque, normaliseUuid, parseProfile } from './textures';

function base64(value: unknown): string {
	return btoa(JSON.stringify(value));
}

describe('normaliseUuid', () => {
	it('adds dashes and lower-cases', () => {
		expect(normaliseUuid('069A79F444E94726A5BEFCA90E38AAF5')).toBe('069a79f4-44e9-4726-a5be-fca90e38aaf5');
		expect(normaliseUuid('069a79f4-44e9-4726-a5be-fca90e38aaf5')).toBe('069a79f4-44e9-4726-a5be-fca90e38aaf5');
	});

	it.each(['', 'steve', '069a79f444e94726a5befca90e38aaf', '069a79f444e94726a5befca90e38aaf5a', 'g69a79f444e94726a5befca90e38aaf5'])(
		'rejects %j',
		(value) => {
			expect(() => normaliseUuid(value)).toThrow();
		}
	);
});

describe('forwarded', () => {
	it('sends Mojang through the server', () => {
		expect(forwarded('https://sessionserver.mojang.com/session/minecraft/profile/x')).toBe(
			'/mojang/session/minecraft/profile/x'
		);
		expect(forwarded('https://textures.minecraft.net/texture/abc')).toBe('/mojang/textures/texture/abc');
	});

	it('keeps other addresses', () => {
		expect(forwarded('/mojang/default/steve.png')).toBe('/mojang/default/steve.png');
		expect(forwarded('https://example.com/skin.png')).toBe('https://example.com/skin.png');
	});
});

describe('parseProfile', () => {
	const profile = (textures: unknown) => ({
		name: 'Notch',
		properties: [{ name: 'textures', value: base64({ textures }) }]
	});

	it('reads skin, model and cape', () => {
		expect(
			parseProfile(profile({ SKIN: { url: 's', metadata: { model: 'slim' } }, CAPE: { url: 'c' } }))
		).toEqual({ name: 'Notch', skinUrl: 's', capeUrl: 'c', slim: true });
		expect(parseProfile(profile({ SKIN: { url: 's' } }))).toEqual({
			name: 'Notch',
			skinUrl: 's',
			capeUrl: null,
			slim: false
		});
	});

	it('fails without textures or skin', () => {
		expect(() => parseProfile({ name: 'x', properties: [] })).toThrow();
		expect(() => parseProfile(profile({ CAPE: { url: 'c' } }))).toThrow();
	});
});

describe('isOpaque', () => {
	it('needs every alpha at 128 or above', () => {
		expect(isOpaque([0, 0, 0, 255, 0, 0, 0, 128])).toBe(true);
		expect(isOpaque([0, 0, 0, 255, 0, 0, 0, 127])).toBe(false);
	});
});
