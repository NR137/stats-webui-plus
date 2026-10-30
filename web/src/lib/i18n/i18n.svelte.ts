import { en } from './messages/en';
import { es, type MessageKey } from './messages/es';
import { fr } from './messages/fr';
import { de } from './messages/de';
import { STORAGE_KEY, isLocale, locales, type Locale } from './locales';
import { formatWindowTitle } from '$lib/branding.svelte';

const dictionaries: Record<Locale, Record<MessageKey, string>> = { es, en, fr, de };

// Used when the visitor hasn't picked a language and none of their browser languages is supported.
// Replaced by the server's defaultLanguage once /api/status has loaded (see applyLocaleSettings).
let fallbackLocale: Locale = 'en';

// Languages the server offers (config "languages"); all of them until /api/status says otherwise.
export const localeSettings = $state<{ enabled: Locale[] }>({ enabled: [...locales] });

function isEnabled(locale: Locale): boolean {
	return localeSettings.enabled.includes(locale);
}

/** First supported language in the browser's preference list (e.g. ["de-DE", "en-US"] -> "en"). */
function browserLocale(): Locale | null {
	if (typeof navigator === 'undefined') {
		return null;
	}
	const preferred = navigator.languages?.length ? navigator.languages : [navigator.language];
	for (const tag of preferred) {
		const language = tag?.slice(0, 2).toLowerCase();
		if (isLocale(language) && isEnabled(language)) {
			return language;
		}
	}
	return null;
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

function initialLocale(): Locale {
	return readStored() ?? browserLocale() ?? fallbackLocale;
}

export const i18n = $state({
	locale: initialLocale()
});

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
		fallbackLocale = defaultLanguage;
	} else {
		fallbackLocale = isEnabled('en') ? 'en' : localeSettings.enabled[0];
	}
	const next = readStored() ?? browserLocale() ?? fallbackLocale;
	if (i18n.locale !== next) {
		i18n.locale = next;
		if (typeof document !== 'undefined') {
			document.documentElement.lang = next;
		}
	}
}

export function setLocale(next: Locale) {
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
	return formatWindowTitle(page, i18n.locale);
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
