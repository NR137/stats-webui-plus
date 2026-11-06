import advancementLang from '$lib/mc/advancementLang.json';
import { i18n, nameComparer, type Locale } from '$lib/i18n/i18n.svelte';
import { hasIcon } from '$lib/mc/items';

const dictionaries = advancementLang as Record<string, Record<string, string>>;

function langKey(id: string, kind: 'title' | 'description') {
	const colon = id.indexOf(':');
	if (colon <= 0) {
		return '';
	}
	const namespace = id.slice(0, colon);
	const path = id.slice(colon + 1);
	if (namespace !== 'minecraft' || !path) {
		return '';
	}
	return `advancements.${path.replaceAll('/', '.')}.${kind}`;
}

function lookup(key: string, locale: Locale) {
	if (!key) {
		return '';
	}
	return dictionaries[locale]?.[key] || dictionaries.en?.[key] || '';
}

export function advancementTitle(id: string, fallback = id) {
	return lookup(langKey(id, 'title'), i18n.locale) || fallback;
}

export function advancementDescription(id: string, fallback = '') {
	return lookup(langKey(id, 'description'), i18n.locale) || fallback;
}

/**
 * Compares two advancements by their title in the current language, the way stat names are sorted. Lists of
 * advancements are short (a tab holds a few dozen), so looking the titles up per comparison is fine here.
 */
export function compareAdvancementTitles(
	a: { id: string; title: string },
	b: { id: string; title: string }
): number {
	return nameComparer()(advancementTitle(a.id, a.title), advancementTitle(b.id, b.title)) || a.id.localeCompare(b.id);
}

export function advancementTabTitle(tab: string, fallback = tab) {
	return lookup(`advancements.${tab}.root.title`, i18n.locale) || fallback;
}

// Some advancements show a special item with a name of its own (the Ominous Banner, a white banner with a pattern);
// the texture script stores those icons under "advancement:<id>".
export function advancementIcon(id: string, icon: string): string {
	const own = `advancement:${id}`;
	return hasIcon(own) ? own : icon;
}

/** A player's own page for an advancement: their progress on it. */
export function playerAdvancementHref(name: string, id: string) {
	return `/players/${encodeURIComponent(name)}${advancementHref(id)}`;
}

export function advancementHref(id: string) {
	return `/advancements/${id
		.replace(':', '/')
		.split('/')
		.map((part) => encodeURIComponent(part))
		.join('/')}`;
}

export function matchesAdvancementQuery(
	id: string,
	fallbackTitle: string,
	fallbackDescription: string,
	query: string
) {
	const needle = query.trim().toLowerCase();
	if (!needle) {
		return true;
	}
	const title = advancementTitle(id, fallbackTitle).toLowerCase();
	const description = advancementDescription(id, fallbackDescription).toLowerCase();
	return title.includes(needle) || description.includes(needle) || id.toLowerCase().includes(needle);
}
