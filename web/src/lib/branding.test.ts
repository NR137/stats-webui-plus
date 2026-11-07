import { beforeEach, describe, expect, it } from 'vitest';
import { branding, formatWindowTitle, linkLabel, localizedText } from './branding.svelte';

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
