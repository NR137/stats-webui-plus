import { en } from './messages/en';
import { es, type MessageKey } from './messages/es';
import { fr } from './messages/fr';
import { de } from './messages/de';
import { STORAGE_KEY, isLocale, locales, type Locale } from './locales';
import { formatWindowTitle } from '$lib/branding.svelte';

const dictionaries: Record<Locale, Record<MessageKey, string>> = { es, en, fr, de };

export const localeSettings = $state<{ enabled: Locale[]; fallback: Locale }>({
	// Languages the server offers (config "languages"); all of them until /api/status says otherwise.
	enabled: [...locales],
	// The server's defaultLanguage (see applyLocaleSettings); English until /api/status has loaded.
	fallback: 'en'
});

function isEnabled(locale: Locale): boolean {
	return localeSettings.enabled.includes(locale);
}

/** The browser's preferred languages in order, as language codes (["de-DE", "en-US"] -> ["de", "en"]). */
function browserLanguages(): string[] {
	if (typeof navigator === 'undefined') {
		return [];
	}
	const preferred = navigator.languages?.length ? navigator.languages : [navigator.language];
	return preferred.map((tag) => (tag ?? '').split(/[-_]/)[0].toLowerCase()).filter((code) => /^[a-z]{2,3}$/.test(code));
}

/**
 * The one order in which languages are tried, everywhere: the language picked in the switcher (or, with `picked`,
 * the one about to be), the browser's preferred languages, the server's defaultLanguage, English. The UI uses the
 * first of them it offers; config texts (site heading, link labels, ...) and custom pages the first they have.
 */
export function languageOrder(picked: string | null = i18n.picked): string[] {
	const order = [picked, ...browserLanguages(), localeSettings.fallback, 'en'];
	return [...new Set(order.filter((code): code is string => !!code))];
}

function readStored(): Locale | null {
	if (typeof localStorage === 'undefined') {
		return null;
	}
	const stored = localStorage.getItem(STORAGE_KEY);
	return isLocale(stored) && isEnabled(stored) ? stored : null;
}

function persist(next: Locale) {
	if (typeof localStorage !== 'undefined') {
		localStorage.setItem(STORAGE_KEY, next);
	}
	if (typeof document !== 'undefined') {
		document.documentElement.lang = next;
	}
}

/** The first language of languageOrder() the UI offers. */
function uiLocale(picked: Locale | null): Locale {
	const offered = languageOrder(picked).find((code): code is Locale => isLocale(code) && isEnabled(code));
	return offered ?? localeSettings.enabled[0];
}

export const i18n = $state<{ locale: Locale; picked: Locale | null }>({
	locale: 'en',
	// Only a language picked in the switcher (saved in the browser), else null.
	picked: readStored()
});
i18n.locale = uiLocale(i18n.picked);

if (typeof document !== 'undefined') {
	document.documentElement.lang = i18n.locale;
}

/**
 * Applies the server's language settings: only the enabled languages are offered, and defaultLanguage is used
 * unless the visitor picked an enabled language or one of their browser languages is enabled.
 */
export function applyLocaleSettings(defaultLanguage: string | undefined, languages: string[]) {
	const enabled = languages.filter(isLocale);
	localeSettings.enabled = enabled.length ? enabled : [...locales];
	if (isLocale(defaultLanguage) && isEnabled(defaultLanguage)) {
		localeSettings.fallback = defaultLanguage;
	} else {
		localeSettings.fallback = isEnabled('en') ? 'en' : localeSettings.enabled[0];
	}
	i18n.picked = readStored();
	const next = uiLocale(i18n.picked);
	if (i18n.locale !== next) {
		i18n.locale = next;
		if (typeof document !== 'undefined') {
			document.documentElement.lang = next;
		}
	}
}

export function setLocale(next: Locale) {
	i18n.picked = next;
	i18n.locale = next;
	persist(next);
}

export function t(key: MessageKey, vars?: Record<string, string | number>): string {
	let text = dictionaries[i18n.locale][key] ?? dictionaries.en[key] ?? key;
	if (!vars) {
		return text;
	}
	for (const [name, value] of Object.entries(vars)) {
		text = text.replaceAll(`{${name}}`, String(value));
	}
	return text;
}

export function tDynamic(
	prefix:
		| 'leaderboard'
		| 'leaderboard.hint'
		| 'category'
		| 'unit'
		| 'unit.hint'
		| 'player.grade'
		| 'player.group'
		| 'player.custom',
	id: string,
	fallback = id
): string {
	const key = `${prefix}.${id}`;
	if (key in dictionaries.es) {
		return t(key as MessageKey);
	}
	return fallback;
}

let collator: { locale: Locale; compare: Intl.Collator['compare'] } | null = null;

/**
 * Compares names in the current language, ignoring case and accents (so "Eiche…" sorts under E in German). Fetch it
 * once per sort, not per comparison.
 */
export function nameComparer(): Intl.Collator['compare'] {
	if (collator?.locale !== i18n.locale) {
		collator = { locale: i18n.locale, compare: new Intl.Collator(i18n.locale, { sensitivity: 'base' }).compare };
	}
	return collator.compare;
}

export function boardHint(id: string, unit?: string): string {
	return tDynamic('leaderboard.hint', id, '') || (unit ? tDynamic('unit.hint', unit, '') : '');
}

export function pageTitle(page: string): string {
	return formatWindowTitle(page, languageOrder());
}

export function formatWhen(ms: number, fallback = '✓'): string {
	if (!ms) {
		return fallback;
	}
	return new Date(ms).toLocaleString(i18n.locale, {
		year: 'numeric',
		month: 'short',
		day: 'numeric',
		hour: '2-digit',
		minute: '2-digit',
		hour12: false
	});
}

const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
	['year', 365 * 24 * 3600],
	['month', 30 * 24 * 3600],
	['week', 7 * 24 * 3600],
	['day', 24 * 3600],
	['hour', 3600],
	['minute', 60]
];

/** "3 days ago" / "vor 3 Tagen" in the current language. */
export function formatRelative(ms: number): string {
	const seconds = (ms - Date.now()) / 1000;
	const format = new Intl.RelativeTimeFormat(i18n.locale, { numeric: 'auto' });
	for (const [unit, size] of RELATIVE_UNITS) {
		if (Math.abs(seconds) >= size) {
			return format.format(Math.round(seconds / size), unit);
		}
	}
	return format.format(0, 'minute');
}

export { locales, type Locale } from './locales';
export type { MessageKey };
