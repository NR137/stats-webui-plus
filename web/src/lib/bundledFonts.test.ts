import { describe, expect, it } from 'vitest';
import { fontPackagesInUse, packagesShown } from './bundledFonts.ts';

// The Licenses page names only the fonts a server's settings use, and lists only their packages.
const packages = [
	{ name: '@fontsource/jacquard-12' },
	{ name: '@fontsource/press-start-2p' },
	{ name: '@fontsource/tiny5' },
	{ name: 'svelte' }
];
const shown = (siteHeadingFont: string, pageTitleFont: string) =>
	packagesShown(packages, fontPackagesInUse(siteHeadingFont, pageTitleFont)).map((entry) => entry.name);

describe('the fonts on the Licenses page', () => {
	it('the defaults: Press Start 2P for the heading, Tiny5 for the titles', () => {
		expect(shown('blocky', 'tiny')).toEqual(['@fontsource/press-start-2p', '@fontsource/tiny5', 'svelte']);
	});

	it('one font for both is listed once', () => {
		expect(shown('blocky', 'blocky')).toEqual(['@fontsource/press-start-2p', 'svelte']);
	});

	it("the Minecraft font and a server's own font come with no package", () => {
		expect(shown('custom', 'minecraft')).toEqual(['svelte']);
		expect(shown('gothic', 'minecraft')).toEqual(['@fontsource/jacquard-12', 'svelte']);
	});
});
