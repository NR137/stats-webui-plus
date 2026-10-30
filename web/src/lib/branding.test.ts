import { beforeEach, describe, expect, it } from 'vitest';
import { branding, formatWindowTitle, linkLabel, localizedText } from './branding.svelte';

describe('localizedText', () => {
	it('one text for all languages', () => {
		expect(localizedText('My Server', 'de')).toBe('My Server');
	});

	it('picks the visitor language', () => {
		expect(localizedText({ en: 'My Server', de: 'Mein Server' }, 'de')).toBe('Mein Server');
	});
});

// README, Menu links: "text per language (falls back to English, then the first one given)".
describe('linkLabel', () => {
	const link = (label: string | Record<string, string>) => ({ label, url: '/map', newTab: true });

	it('uses the visitor language', () => {
		expect(linkLabel(link({ en: 'Map', de: 'Karte' }), 'de')).toBe('Karte');
	});

	it('falls back to English', () => {
		expect(linkLabel(link({ en: 'Map', de: 'Karte' }), 'fr')).toBe('Map');
	});

	it('then to the first one given', () => {
		expect(linkLabel(link({ de: 'Karte', es: 'Mapa' }), 'fr')).toBe('Karte');
	});

	it('plain text for all', () => {
		expect(linkLabel(link('Discord'), 'es')).toBe('Discord');
	});
});

// README: windowTitle, "each must contain {page} (the page name)".
describe('formatWindowTitle', () => {
	beforeEach(() => {
		branding.windowTitle = '{page} | Test';
	});

	it('puts the page name in', () => {
		expect(formatWindowTitle('Ranking', 'en')).toBe('Ranking | Test');
	});

	it('per language', () => {
		branding.windowTitle = { en: '{page} - EN', de: '{page} - DE' };
		expect(formatWindowTitle('Spieler', 'de')).toBe('Spieler - DE');
	});

	it('a language the server blanked still gets a title with the page', () => {
		branding.windowTitle = { en: '{page} - EN', de: '' };
		const title = formatWindowTitle('Spieler', 'de');
		expect(title).toContain('Spieler');
		expect(title).not.toContain('{page}');
	});

	it('also takes <page>, like the server', () => {
		branding.windowTitle = '<page> | Test';
		expect(formatWindowTitle('Ranking', 'en')).toBe('Ranking | Test');
	});
});
