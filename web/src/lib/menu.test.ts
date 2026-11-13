import { describe, expect, it } from 'vitest';
import { isActive, mainMenu, subMenu, tabAttributes } from './menu.ts';
import type { CustomPage, MenuLink } from './types.ts';

// README, Menu links and Custom pages: entries of the top menu with "place": "main", and the start page.
const label = (key: string) => key.replace('menu.', '');
const home: CustomPage = { id: 'home', label: { en: 'Home', de: 'Start' }, place: 'main', icon: 'minecraft:oak_door' };
const rules: CustomPage = { id: 'rules', label: 'Rules', place: 'main' };
const imprint: CustomPage = { id: 'imprint', label: 'Imprint', place: 'footer' };
const map: MenuLink = {
	label: 'Map',
	url: 'https://map.example.com',
	newTab: true,
	place: 'main',
	icon: 'minecraft:filled_map'
};
const discord: MenuLink = { label: 'Discord', url: 'https://discord.example.com', place: 'menu' };

describe('mainMenu', () => {
	it("the site's pages, the ranking as the start page", () => {
		const menu = mainMenu({ pages: [], links: [], startPage: '' }, label, ['en']);
		expect(menu.map((entry) => entry.href)).toEqual([
			'/',
			'/players',
			'/advancements',
			'/leaderboards',
			'/stats',
			'/compare'
		]);
		expect(menu[0]).toEqual({ href: '/', label: 'ranking', icon: 'minecraft:nether_star' });
	});

	it('a start page of your own comes first; the ranking moves to /ranking', () => {
		const menu = mainMenu({ pages: [home, rules], links: [], startPage: 'home' }, label, ['de']);
		expect(menu[0]).toEqual({ href: '/', label: 'Start', icon: 'minecraft:oak_door' });
		expect(menu[1]).toMatchObject({ href: '/ranking', label: 'ranking' });
		// Only once: not again among the owner's pages.
		expect(menu.filter((entry) => entry.label === 'Start')).toHaveLength(1);
	});

	it("then the owner's pages and links placed in the top menu, with their icons or the defaults", () => {
		const menu = mainMenu({ pages: [home, rules, imprint], links: [map, discord], startPage: '' }, label, ['en']);
		expect(menu.slice(6)).toEqual([
			{ href: '/pages/home', label: 'Home', icon: 'minecraft:oak_door' },
			{ href: '/pages/rules', label: 'Rules', icon: 'minecraft:book' },
			{ href: 'https://map.example.com', label: 'Map', icon: 'minecraft:filled_map', newTab: true }
		]);
	});

	it('a link without a label is named by its address; a page without one by its id', () => {
		const menu = mainMenu(
			{ pages: [{ id: 'team', place: 'main' }], links: [{ label: '', url: '/map', place: 'main' }], startPage: '' },
			label,
			['en']
		);
		expect(menu.slice(6).map((entry) => [entry.label, entry.icon])).toEqual([
			['team', 'minecraft:book'],
			['/map', 'minecraft:paper']
		]);
	});
});

describe('isActive', () => {
	it('the start page only for itself', () => {
		expect(isActive('/', '/')).toBe(true);
		expect(isActive('/', '/players')).toBe(false);
	});

	it('every other page also for what is below it', () => {
		expect(isActive('/players', '/players')).toBe(true);
		expect(isActive('/players', '/players/Steve/advancements')).toBe(true);
		expect(isActive('/players', '/playersfoo')).toBe(false);
		expect(isActive('/ranking', '/')).toBe(false);
	});
});

describe('subMenu', () => {
	it("the owner's other links, then their pages for the row; only pages can be the current one", () => {
		const page: CustomPage = { id: 'rules', label: 'Rules', place: 'menu', icon: 'minecraft:lectern' };
		const local: MenuLink = { label: 'Map', url: '/pages/rules', place: 'menu' };
		const settings = { pages: [page, imprint, home], links: [map, discord, local], startPage: '' };
		const row = subMenu(settings, ['en'], '/pages/rules');
		expect(row).toEqual([
			{ href: 'https://discord.example.com', label: 'Discord', icon: 'minecraft:paper', newTab: undefined, active: false },
			{ href: '/pages/rules', label: 'Map', icon: 'minecraft:paper', newTab: undefined, active: false },
			{ href: '/pages/rules', label: 'Rules', icon: 'minecraft:lectern', active: true }
		]);
	});

	it('not the start page', () => {
		const start: CustomPage = { id: 'home', label: 'Home', place: 'menu' };
		expect(subMenu({ pages: [start], links: [], startPage: 'home' }, ['en'], '/')).toEqual([]);
	});
});

describe('tabAttributes', () => {
	it('a new tab without access to this one; otherwise nothing', () => {
		expect(tabAttributes(true)).toEqual({ target: '_blank', rel: 'noopener noreferrer' });
		expect(tabAttributes(false)).toEqual({});
		expect(tabAttributes(undefined)).toEqual({});
	});
});
