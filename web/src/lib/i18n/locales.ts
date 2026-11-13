// The languages of the web UI (the switcher sorts them by name).
export const locales = ['es', 'en', 'fr', 'de'] as const;

export type Locale = (typeof locales)[number];

// Only a language picked in the switcher is remembered here, never a detected one: the browser's preferences may change.
export const STORAGE_KEY = 'stats-webui-plus-language';

export function isLocale(value: unknown): value is Locale {
	return typeof value === 'string' && (locales as readonly string[]).includes(value);
}
