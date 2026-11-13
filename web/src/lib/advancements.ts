import { i18n, nameComparer } from '#lib/i18n/i18n.svelte.ts';
import type { Locale } from '#lib/i18n/locales.ts';
import { idToPath } from '#lib/ids.ts';
import { hasIcon } from '#lib/mc/items.svelte.ts';
import { officialText } from '#lib/mc/names.svelte.ts';
import { playerPath } from '#lib/players.ts';

// Texts and addresses of advancements.

const GAME = 'minecraft:';

/** The game's own text of one of its advancements in a language, once its file is loaded; other advancements have none. */
function gameText(id: string, part: 'title' | 'description', locale: Locale): string | undefined {
	if (!id.startsWith(GAME)) {
		return undefined;
	}
	const path = id.slice(GAME.length);
	if (!path) {
		return undefined;
	}
	return officialText('advancement', `advancements.${path.replaceAll('/', '.')}.${part}`, locale);
}

/** The title in the current language, else the fallback (the server's own text, in English). */
export function advancementTitle(id: string, fallback = id): string {
	return gameText(id, 'title', i18n.locale) ?? fallback;
}

export function advancementDescription(id: string, fallback = ''): string {
	return gameText(id, 'description', i18n.locale) ?? fallback;
}

export function compareAdvancementTitles(a: { id: string; title: string }, b: { id: string; title: string }): number {
	const byTitle = nameComparer()(advancementTitle(a.id, a.title), advancementTitle(b.id, b.title));
	if (byTitle !== 0) {
		return byTitle;
	}
	return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

/** A tab's name: its root advancement's title in the current language, else the fallback. */
export function advancementTabTitle(tab: string, fallback = tab): string {
	return officialText('advancement', `advancements.${tab}.root.title`, i18n.locale) ?? fallback;
}

/** Some advancements show a special item (the Ominous Banner), listed under their own key in the icon list. */
export function advancementIcon(id: string, icon: string): string {
	const key = `advancement:${id}`;
	return hasIcon(key) ? key : icon;
}

export function advancementHref(id: string): string {
	return `/advancements/${idToPath(id)}`;
}

export function playerAdvancementHref(name: string, id: string, uuid?: string): string {
	return playerPath(name, uuid) + advancementHref(id);
}

/** Lower case without accents: "über" matches "uber", "Écho" "echo". */
function fold(text: string): string {
	return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

const WORD_CHAR = /[\p{L}\p{N}]/u;

function startsWord(text: string, word: string): boolean {
	for (let at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + 1)) {
		if (at === 0 || !WORD_CHAR.test(text[at - 1])) {
			return true;
		}
	}
	return false;
}

/**
 * Every word of the query is in the text. Words of one or two letters only where a word of the text starts ("ad"
 * finds "Adventuring", not "Upgrade"); longer ones anywhere, for German compounds ("drachen" in "Enderdrachen").
 */
export function textMatches(text: string, query: string): boolean {
	const folded = fold(text);
	return fold(query)
		.split(/\s+/)
		.filter(Boolean)
		.every((word) => (word.length <= 2 ? startsWord(folded, word) : folded.includes(word)));
}

/**
 * How well an advancement matches a search: 2 for its title (in the current language, or the English one players know
 * from guides), 1 for only its description, else 0. The ID is never searched: it is English, shows nowhere, and holds
 * the tab's name.
 */
export function advancementMatch(
	id: string,
	fallbackTitle: string,
	fallbackDescription: string,
	query: string
): 0 | 1 | 2 {
	if (!query.trim()) {
		return 2;
	}
	const englishTitle = gameText(id, 'title', 'en') ?? fallbackTitle;
	if (textMatches(advancementTitle(id, fallbackTitle), query) || textMatches(englishTitle, query)) {
		return 2;
	}
	return textMatches(advancementDescription(id, fallbackDescription), query) ? 1 : 0;
}

export function matchesAdvancementQuery(
	id: string,
	fallbackTitle: string,
	fallbackDescription: string,
	query: string
): boolean {
	return advancementMatch(id, fallbackTitle, fallbackDescription, query) > 0;
}
