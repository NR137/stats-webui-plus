import type { Locale } from '#lib/i18n/locales.ts';
import { mcFile } from '#lib/mc/files.ts';

// The game's own texts per language: block/item/entity/biome/trim names, the general statistics' names ("minecraft:jump"),
// and the advancement titles and descriptions
// (keyed like the game's language files, "advancements.story.root.title"). The server makes /mc/lang/<locale>.json
// from the game's language files; loaded on demand for the current language only.
export type NameKind = 'item' | 'block' | 'entity' | 'biome' | 'trim' | 'stat' | 'advancement';

type NameFile = Record<NameKind, Record<string, string>>;

// Raw: the file is only ever replaced as a whole, so deep reactivity would only add a proxy to every lookup.
let loaded = $state.raw<{ locale: Locale | null; names: NameFile | null }>({ locale: null, names: null });
let requested: Locale | null = null;

export function loadNames(locale: Locale): void {
	if (requested === locale) {
		return;
	}
	requested = locale;
	fetch(mcFile(`/mc/lang/${locale}.json`))
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

/** A text of the game in this language, or undefined while another language's file (or none) is loaded. */
export function officialText(kind: NameKind, key: string, locale: Locale): string | undefined {
	return loaded.locale === locale ? loaded.names?.[kind]?.[key] : undefined;
}

/** Uses these texts (tests). */
export function useNames(locale: Locale, names: Partial<NameFile>): void {
	requested = locale;
	loaded = { locale, names: names as NameFile };
}
