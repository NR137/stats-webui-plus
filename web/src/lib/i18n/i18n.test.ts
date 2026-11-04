import { afterEach, describe, expect, it, vi } from 'vitest';
import { STORAGE_KEY } from './locales';

// CHANGELOG 1.3.0: detection checks the browser's whole preference list, falls back to the server's defaultLanguage
// (English by default), and only remembers a language picked in the switcher.

function browser(languages: string[], stored: string | null = null) {
	const storage = new Map<string, string>(stored ? [[STORAGE_KEY, stored]] : []);
	const localStorage = {
		getItem: vi.fn((key: string) => storage.get(key) ?? null),
		setItem: vi.fn((key: string, value: string) => void storage.set(key, value)),
		removeItem: vi.fn((key: string) => void storage.delete(key))
	};
	vi.stubGlobal('navigator', { languages, language: languages[0] ?? '' });
	vi.stubGlobal('localStorage', localStorage);
	return localStorage;
}

// The language is picked when the module loads, so every test loads a fresh copy.
async function load() {
	vi.resetModules();
	return import('./i18n.svelte');
}

afterEach(() => {
	vi.unstubAllGlobals();
});

describe('language detection', () => {
	it.each([
		[['de-DE', 'en'], 'de'],
		[['fr-CA'], 'fr'],
		[['es-MX', 'en'], 'es'],
		[['EN-gb'], 'en']
	])('browser %j gives %s', async (languages, expected) => {
		browser(languages);
		expect((await load()).i18n.locale).toBe(expected);
	});

	it('checks the whole list, not only the first language', async () => {
		browser(['nl-NL', 'ja', 'de']);
		expect((await load()).i18n.locale).toBe('de');
	});

	it('falls back to English, not Spanish', async () => {
		browser(['nl', 'ja']);
		expect((await load()).i18n.locale).toBe('en');
	});

	it("then to the server's defaultLanguage", async () => {
		browser(['nl', 'ja']);
		const { i18n, applyLocaleSettings } = await load();
		applyLocaleSettings('fr', ['de', 'en', 'es', 'fr']);
		expect(i18n.locale).toBe('fr');
	});

	it('a browser language the server turned off is skipped', async () => {
		browser(['es', 'de']);
		const { i18n, applyLocaleSettings, localeSettings } = await load();
		applyLocaleSettings('en', ['de', 'en']);
		expect(localeSettings.enabled).toEqual(['de', 'en']);
		expect(i18n.locale).toBe('de');
	});

	it('a defaultLanguage the server turned off is not used', async () => {
		browser(['nl']);
		const { i18n, applyLocaleSettings } = await load();
		applyLocaleSettings('es', ['de', 'fr']);
		expect(['de', 'fr']).toContain(i18n.locale);
	});

	it('unknown languages in the config are ignored', async () => {
		browser(['nl']);
		const { applyLocaleSettings, localeSettings } = await load();
		applyLocaleSettings('en', ['xx', 'en', 'de']);
		expect(localeSettings.enabled).toEqual(['en', 'de']);
	});
});

describe('remembering the language', () => {
	it('detection alone stores nothing', async () => {
		const storage = browser(['de']);
		const { applyLocaleSettings } = await load();
		applyLocaleSettings('en', ['de', 'en', 'es', 'fr']);
		expect(storage.setItem).not.toHaveBeenCalled();
	});

	it('picking a language in the switcher is stored and wins over the browser', async () => {
		const storage = browser(['de']);
		const { i18n, setLocale } = await load();
		setLocale('fr');
		expect(i18n.locale).toBe('fr');
		expect(storage.setItem).toHaveBeenCalledWith(STORAGE_KEY, 'fr');

		// Next visit.
		browser(['de'], 'fr');
		expect((await load()).i18n.locale).toBe('fr');
	});

	it('a stored language the server turned off is not used', async () => {
		browser(['de'], 'es');
		const { i18n, applyLocaleSettings } = await load();
		applyLocaleSettings('en', ['de', 'en']);
		expect(i18n.locale).toBe('de');
	});

	it('garbage in storage is ignored', async () => {
		browser(['fr'], 'klingon');
		expect((await load()).i18n.locale).toBe('fr');
	});
});
