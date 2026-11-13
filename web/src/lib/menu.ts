import { linkLabel, pageLabel } from '#lib/branding.svelte.ts';
import type { CustomPage, MenuLink } from '#lib/types.ts';

export type MenuEntry = { href: string; label: string; icon: string; newTab?: boolean };

/** The names of the site's own pages in the top menu (message keys). */
export type SitePageKey =
	| 'menu.ranking'
	| 'menu.players'
	| 'menu.advancements'
	| 'menu.leaderboards'
	| 'menu.stats'
	| 'menu.compare';

/**
 * The top menu: the start page if it is one of the server owner's pages (config startPage; the ranking then moves to
 * /ranking), the site's pages, then the owner's pages and links placed there ("place": "main"), each with an item
 * icon. Links to other sites open as configured (newTab).
 */
export function mainMenu(
	settings: { pages: CustomPage[]; links: MenuLink[]; startPage: string },
	label: (key: SitePageKey) => string,
	languages: readonly string[]
): MenuEntry[] {
	const startPage = settings.pages.find((entry) => entry.id === settings.startPage) ?? null;
	return [
		...(startPage
			? [{ href: '/', label: pageLabel(startPage, languages), icon: startPage.icon ?? 'minecraft:oak_door' }]
			: []),
		{ href: startPage ? '/ranking' : '/', label: label('menu.ranking'), icon: 'minecraft:nether_star' },
		{ href: '/players', label: label('menu.players'), icon: 'minecraft:armor_stand' },
		{ href: '/advancements', label: label('menu.advancements'), icon: 'minecraft:knowledge_book' },
		{ href: '/leaderboards', label: label('menu.leaderboards'), icon: 'minecraft:oak_sign' },
		{ href: '/stats', label: label('menu.stats'), icon: 'minecraft:writable_book' },
		{ href: '/compare', label: label('menu.compare'), icon: 'minecraft:spyglass' },
		...settings.pages
			.filter((entry) => entry.place === 'main' && entry.id !== settings.startPage)
			.map((entry) => ({
				href: `/pages/${entry.id}`,
				label: pageLabel(entry, languages),
				icon: entry.icon ?? 'minecraft:book'
			})),
		...settings.links
			.filter((link) => link.place === 'main')
			.map((link) => ({
				href: link.url,
				label: linkLabel(link, languages),
				icon: link.icon ?? 'minecraft:paper',
				newTab: link.newTab
			}))
	];
}

/**
 * Whether a menu entry is the current page: the start page only for itself, every other page also for everything
 * below it (a player, a board).
 */
export function isActive(href: string, path: string): boolean {
	return href === '/' ? path === '/' : path === href || path.startsWith(`${href}/`);
}

/**
 * The row below the top menu (and the sticky bar's list on phones): the owner's other links, then their pages placed
 * there ("place": "menu"). Links are never marked as the current page; pages are when they are.
 */
export function subMenu(
	settings: { pages: CustomPage[]; links: MenuLink[]; startPage: string },
	languages: readonly string[],
	path: string
): (MenuEntry & { active: boolean })[] {
	return [
		...settings.links
			.filter((link) => link.place !== 'main')
			.map((link) => ({
				href: link.url,
				label: linkLabel(link, languages),
				icon: link.icon ?? 'minecraft:paper',
				newTab: link.newTab,
				active: false
			})),
		...settings.pages
			.filter((entry) => entry.place === 'menu' && entry.id !== settings.startPage)
			.map((entry) => ({
				href: `/pages/${entry.id}`,
				label: pageLabel(entry, languages),
				icon: entry.icon ?? 'minecraft:book',
				active: isActive(`/pages/${entry.id}`, path)
			}))
	];
}

/** A link that opens in a new tab, without giving that tab access to this one. */
export function tabAttributes(newTab: boolean | undefined): { target?: string; rel?: string } {
	return newTab ? { target: '_blank', rel: 'noopener noreferrer' } : {};
}
