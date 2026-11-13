/**
 * The fonts that come with the mod for the site heading and the page titles (config siteHeadingFont, pageTitleFont):
 * the Licenses page names only those a server's settings use, and its packages list leaves out the font packages of
 * the others (a browser never loads them there).
 */
export const BUNDLED_FONTS = {
	blocky: {
		link: { label: 'Press Start 2P', url: 'https://fonts.google.com/specimen/Press+Start+2P' },
		author: 'CodeMan38',
		package: '@fontsource/press-start-2p'
	},
	gothic: {
		link: { label: 'Jacquard 12', url: 'https://fonts.google.com/specimen/Jacquard+12' },
		author: 'Soft Type Project',
		package: '@fontsource/jacquard-12'
	},
	tiny: {
		link: { label: 'Tiny5', url: 'https://fonts.google.com/specimen/Tiny5' },
		author: 'Gissio',
		package: '@fontsource/tiny5'
	}
};

export type BundledFont = keyof typeof BUNDLED_FONTS;

export const isBundledFont = (font: string): font is BundledFont => Object.hasOwn(BUNDLED_FONTS, font);

/** The font packages these settings load. */
export function fontPackagesInUse(siteHeadingFont: string, pageTitleFont: string): Set<string> {
	return new Set(
		[siteHeadingFont, pageTitleFont].filter(isBundledFont).map((font) => BUNDLED_FONTS[font].package)
	);
}

/** The bundled packages without the font packages these settings don't load. */
export function packagesShown<T extends { name: string }>(packages: T[], inUse: Set<string>): T[] {
	return packages.filter((entry) => !entry.name.startsWith('@fontsource/') || inUse.has(entry.name));
}
