import { formatWindowTitle } from '#lib/branding.svelte.ts';
import { flatten } from './flatten';
import { isLocale, locales, STORAGE_KEY, type Locale } from './locales';
import { de } from './messages/de';
import { en, type MessageKey } from './messages/en';
import { es } from './messages/es';
import { fr } from './messages/fr';

export { locales, type Locale } from './locales';
export type { MessageKey } from './messages/en';

// Flattened once at load: every t() call is then a single lookup.
const messages: Record<Locale, Record<string, string>> = {
	en: flatten(en),
	es: flatten(es),
	fr: flatten(fr),
	de: flatten(de)
};
const english = messages.en;

/** The server's language settings: every language and English until /api/status says otherwise. */
export const localeSettings = $state<{ enabled: Locale[]; fallback: Locale }>({
	enabled: [...locales],
	fallback: 'en'
});

// Every access to the browser is guarded: the pages are also prerendered, where there is no window.
function storedPick(): Locale | null {
	let stored: string | null = null;
	try {
		stored = typeof localStorage === 'undefined' ? null : localStorage.getItem(STORAGE_KEY);
	} catch {
		// Storage can be blocked (private mode, site settings); then nothing is remembered.
	}
	return isLocale(stored) && localeSettings.enabled.includes(stored) ? stored : null;
}

function browserLanguages(): string[] {
	if (typeof navigator === 'undefined') {
		return [];
	}
	const list = navigator.languages?.length ? navigator.languages : [navigator.language];
	return list
		.map((tag) => (tag ?? '').split('-')[0].toLowerCase())
		.filter((code) => /^[a-z]{2,3}$/.test(code));
}

/**
 * The one order in which languages are tried everywhere (the UI, config texts, custom pages): the switcher's pick, the
 * browser's languages, the server's default, English.
 */
export function languageOrder(picked: Locale | null = i18n.picked): string[] {
	const order = [picked ?? '', ...browserLanguages(), localeSettings.fallback, 'en'];
	return [...new Set(order.filter(Boolean))];
}

function resolveLocale(picked: Locale | null): Locale {
	const enabled = localeSettings.enabled;
	const found = languageOrder(picked).find((code): code is Locale => isLocale(code) && enabled.includes(code));
	return found ?? enabled[0];
}

function setDocumentLang(locale: Locale): void {
	if (typeof document !== 'undefined') {
		document.documentElement.lang = locale;
	}
}

const initialPick = storedPick();

/** The website's language, and the one picked in the switcher (null when detected). */
export const i18n = $state<{ locale: Locale; picked: Locale | null }>({
	locale: resolveLocale(initialPick),
	picked: initialPick
});

setDocumentLang(i18n.locale);

/** The server's settings (config defaultLanguage and languages). */
export function applyLocaleSettings(defaultLanguage: string, languages: string[]): void {
	const enabled = languages.filter(isLocale);
	localeSettings.enabled = enabled.length > 0 ? enabled : [...locales];
	const allowed = localeSettings.enabled;
	localeSettings.fallback =
		isLocale(defaultLanguage) && allowed.includes(defaultLanguage)
			? defaultLanguage
			: allowed.includes('en')
				? 'en'
				: allowed[0];
	// A stored pick the server has turned off no longer counts.
	i18n.picked = storedPick();
	const next = resolveLocale(i18n.picked);
	if (next !== i18n.locale) {
		i18n.locale = next;
		setDocumentLang(next);
	}
}

/** The visitor picked a language in the switcher: the only case that is remembered. */
export function setLocale(next: Locale): void {
	i18n.picked = next;
	i18n.locale = next;
	try {
		if (typeof localStorage !== 'undefined') {
			localStorage.setItem(STORAGE_KEY, next);
		}
	} catch {
		// Blocked storage: the pick holds for this visit only.
	}
	setDocumentLang(next);
}

/** The text in the current language, else the English one, else the key; `{name}` replaced by `vars.name`. */
export function t(key: MessageKey, vars?: Record<string, string | number>): string {
	let text = messages[i18n.locale]?.[key] ?? english[key] ?? key;
	if (vars) {
		for (const [name, value] of Object.entries(vars)) {
			text = text.replaceAll(`{${name}}`, String(value));
		}
	}
	return text;
}

/** For keys put together at runtime (board IDs, units): the text if English has one, else the fallback. */
export function tOptional(key: string, fallback: string): string {
	return Object.hasOwn(english, key) ? t(key as MessageKey) : fallback;
}

const comparers = new Map<Locale, (a: string, b: string) => number>();

/** Compares names in the current language, ignoring case and accents ("Eiche…" sorts under E in German). */
export function nameComparer(): (a: string, b: string) => number {
	const locale = i18n.locale;
	let compare = comparers.get(locale);
	if (!compare) {
		compare = new Intl.Collator(locale, { sensitivity: 'base' }).compare;
		comparers.set(locale, compare);
	}
	return compare;
}

export function unitText(unit: string, part: 'name' | 'hint', fallback: string): string {
	return tOptional(`units.${unit}.${part}`, fallback);
}

/** The board's own hint, else its unit's, else none. */
export function boardHint(id: string, unit?: string): string {
	const own = tOptional(`boards.${id}.hint`, '');
	if (own) {
		return own;
	}
	return unit ? unitText(unit, 'hint', '') : '';
}

/** The browser tab's title for a page, from the server's template. */
export function pageTitle(page: string): string {
	return formatWindowTitle(page, languageOrder());
}

const whenFormats = new Map<Locale, Intl.DateTimeFormat>();

/** A completion time as date and time in the current language; 0 or missing (time unknown) gives the fallback. */
export function formatWhen(ms: number | null | undefined, fallback = '✓'): string {
	if (!ms) {
		return fallback;
	}
	const locale = i18n.locale;
	let format = whenFormats.get(locale);
	if (!format) {
		format = new Intl.DateTimeFormat(locale, {
			year: 'numeric',
			month: 'short',
			day: 'numeric',
			hour: '2-digit',
			minute: '2-digit',
			hourCycle: 'h23'
		});
		whenFormats.set(locale, format);
	}
	return format.format(ms);
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

// Largest first: the first one the distance reaches is used.
const relativeUnits: [Intl.RelativeTimeFormatUnit, number][] = [
	['year', 365 * DAY],
	['month', 30 * DAY],
	['week', 7 * DAY],
	['day', DAY],
	['hour', HOUR],
	['minute', MINUTE]
];

const relativeFormats = new Map<Locale, Intl.RelativeTimeFormat>();

/** "3 days ago" / "vor 3 Tagen" in the current language; under a minute "this minute". */
export function formatRelative(ms: number): string {
	const locale = i18n.locale;
	let format = relativeFormats.get(locale);
	if (!format) {
		format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
		relativeFormats.set(locale, format);
	}
	const distance = ms - Date.now();
	for (const [unit, length] of relativeUnits) {
		if (Math.abs(distance) >= length) {
			return format.format(Math.round(distance / length), unit);
		}
	}
	return format.format(0, 'minute');
}
