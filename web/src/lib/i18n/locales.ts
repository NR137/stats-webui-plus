export const locales = ['es', 'en', 'fr', 'de'] as const;

export type Locale = (typeof locales)[number];

// Only a language the visitor picked in the switcher is saved here.
export const STORAGE_KEY = 'mc-stats-locale-choice';

export function isLocale(value: string | null | undefined): value is Locale {
	return (locales as readonly string[]).includes(value ?? '');
}
