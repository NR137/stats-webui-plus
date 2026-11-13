import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { branding, formatWindowTitle, linkLabel, loadBranding, localizedText } from './branding.svelte';

describe('localizedText', () => {
	it('one text for all languages', () => {
		expect(localizedText('My Server', ['de'])).toBe('My Server');
	});

	it('picks the visitor language', () => {
		expect(localizedText({ en: 'My Server', de: 'Mein Server' }, ['de'])).toBe('Mein Server');
	});
});

// README, Text per language: visitors see their language (the switcher's, else the browser's in order); without a
// text in it, the one in defaultLanguage, then English, then the first one given.
describe('linkLabel', () => {
	beforeEach(() => {
		branding.defaultLanguage = 'en';
	});

	const link = (label: string | Record<string, string>) => ({ label, url: '/map', newTab: true });

	it('uses the visitor language', () => {
		expect(linkLabel(link({ en: 'Map', de: 'Karte' }), ['de'])).toBe('Karte');
	});

	it('falls back to English', () => {
		expect(linkLabel(link({ en: 'Map', de: 'Karte' }), ['fr'])).toBe('Map');
	});

	it("falls back to the server's default language first", () => {
		branding.defaultLanguage = 'de';
		expect(linkLabel(link({ en: 'Map', de: 'Karte' }), ['fr'])).toBe('Karte');
		// Without a text in that language, English.
		branding.defaultLanguage = 'es';
		expect(linkLabel(link({ en: 'Map', de: 'Karte' }), ['fr'])).toBe('Map');
	});

	it("tries the visitor's languages in order before the server's default", () => {
		branding.defaultLanguage = 'en';
		// The browser prefers French, then German: no French label, so the German one, not English.
		expect(linkLabel(link({ en: 'Map', de: 'Karte' }), ['fr', 'de', 'en'])).toBe('Karte');
	});

	it('then to the first one given', () => {
		expect(linkLabel(link({ de: 'Karte', es: 'Mapa' }), ['fr'])).toBe('Karte');
	});

	it('plain text for all', () => {
		expect(linkLabel(link('Discord'), ['es'])).toBe('Discord');
	});
});

// README: windowTitle, "each must contain {page} (the page name)".
describe('formatWindowTitle', () => {
	beforeEach(() => {
		branding.windowTitle = '{page} | Test';
	});

	it('puts the page name in', () => {
		expect(formatWindowTitle('Ranking', ['en'])).toBe('Ranking | Test');
	});

	it('per language', () => {
		branding.windowTitle = { en: '{page} - EN', de: '{page} - DE' };
		expect(formatWindowTitle('Spieler', ['de'])).toBe('Spieler - DE');
	});

	it('a language the server blanked still gets a title with the page', () => {
		branding.windowTitle = { en: '{page} - EN', de: '' };
		const title = formatWindowTitle('Spieler', ['de']);
		expect(title).toContain('Spieler');
		expect(title).not.toContain('{page}');
	});

	it('also takes <page>, like the server', () => {
		branding.windowTitle = '<page> | Test';
		expect(formatWindowTitle('Ranking', ['en'])).toBe('Ranking | Test');
	});
});

// README, Configuration: what the site makes of the settings in /api/status, also of values it doesn't know.
describe('loadBranding', () => {
	function status(branding: Record<string, unknown>, extra: Record<string, unknown> = {}) {
		const body = { branding, languages: ['en'], links: [], pages: [], startPage: null, customFont: null, ...extra };
		vi.stubGlobal(
			'fetch',
			vi.fn(async () => new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } }))
		);
	}

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('takes the settings as given', async () => {
		status(
			{
				siteHeadingFont: 'gothic',
				siteHeadingSloganStyle: 'ribbon',
				pageTitleFont: 'minecraft',
				avatarRotate: false,
				headerImagePosition: 'bottom',
				serverAddress: ' play.example.com '
			},
			{ startPage: 'home' }
		);
		await loadBranding();
		expect(branding).toMatchObject({
			siteHeadingFont: 'gothic',
			siteHeadingSloganStyle: 'ribbon',
			pageTitleFont: 'minecraft',
			avatarRotate: false,
			headerImagePosition: 'bottom',
			serverAddress: 'play.example.com',
			startPage: 'home',
			loaded: true
		});
	});

	it('unknown or missing values get the defaults', async () => {
		status({
			siteHeadingFont: 'comic',
			siteHeadingSloganStyle: 'neon',
			pageTitleFont: 'huge',
			headerImagePosition: 'left'
		});
		await loadBranding();
		expect(branding).toMatchObject({
			siteHeadingFont: 'blocky',
			siteHeadingSloganStyle: 'line',
			pageTitleFont: 'tiny',
			avatarRotate: true,
			statusBar: true,
			headerImagePosition: 'top',
			startPage: ''
		});
	});

	it('a custom heading font only with a file to load', async () => {
		const font = { name: 'Title', license: '', licenseText: null, sources: [] as { url: string; format: string }[] };
		status({ siteHeadingFont: 'custom' }, { customFont: font });
		await loadBranding();
		expect(branding.siteHeadingFont).toBe('blocky');
		const sources = [{ url: '/heading-font/0', format: 'woff2' }];
		status({ siteHeadingFont: 'custom' }, { customFont: { ...font, sources } });
		await loadBranding();
		expect(branding.siteHeadingFont).toBe('custom');
	});

	it('without an answer, the defaults', async () => {
		vi.stubGlobal(
			'fetch',
			vi.fn(async () => {
				throw new TypeError('offline');
			})
		);
		await loadBranding();
		expect(branding).toMatchObject({ siteHeadingFont: 'blocky', pageTitleFont: 'tiny', startPage: '', loaded: true });
	});
});
