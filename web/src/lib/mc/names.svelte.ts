import type { Locale } from '$lib/i18n/locales';

// Official Minecraft block/item/entity names per language, written by scripts/textures/extract_mc_textures.py to
// static/mc/lang/<locale>.json. Loaded on demand for the current language only (each file is ~150 KB).
export type NameKind = 'item' | 'block' | 'entity';

type NameFile = Record<NameKind, Record<string, string>>;

// Raw: the file is only ever replaced as a whole, so deep reactivity would only add a proxy to every lookup.
let loaded = $state.raw<{ locale: Locale | null; names: NameFile | null }>({ locale: null, names: null });
let requested: Locale | null = null;

export function loadNames(locale: Locale): void {
	if (requested === locale) {
		return;
	}
	requested = locale;
	fetch(`/mc/lang/${locale}.json`)
		.then((response) => (response.ok ? (response.json() as Promise<NameFile>) : null))
		.then((names) => {
			// Ignore a slow response for a language the visitor has already switched away from.
			if (names && requested === locale) {
				loaded = { locale, names };
			}
		})
		.catch(() => {
			if (requested === locale) {
				requested = null;
			}
		});
}

/** The official name, or undefined if it isn't loaded yet or the ID is unknown (e.g. from a removed mod). */
export function officialName(kind: NameKind, id: string): string | undefined {
	return loaded.names?.[kind]?.[id];
}
