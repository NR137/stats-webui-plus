import { getStatus } from '$lib/api';
import type { LocalizedText, MenuLink, StatusResponse } from '$lib/types';

export type SiteBranding = {
	// Browser tab title template with {page}, per language possible (config windowTitle).
	windowTitle: LocalizedText;
	// Heading under the header image on every page (config siteHeading); '' for none.
	siteHeading: LocalizedText;
	// Rotated text next to the site heading and the line under it; '' hides them.
	siteHeadingTagline: LocalizedText;
	siteHeadingSlogan: LocalizedText;
	// Whether the tagline pulses like Minecraft's splash text (config siteHeadingTaglinePulse).
	siteHeadingTaglinePulse: boolean;
	// Player page avatar (config avatarPose / avatarAngle in degrees, 0 = facing the viewer / avatarRotate).
	avatarPose: string;
	avatarAngle: number;
	avatarRotate: boolean;
	// Status bar under the site heading (config statusBar / serverAddress).
	statusBar: boolean;
	serverAddress: string;
	// Server's fallback UI language (config defaultLanguage); applied in +layout.svelte.
	defaultLanguage: string;
	// Languages offered in the UI (config languages); empty means all.
	languages: string[];
	// Custom menu buttons (config links).
	links: MenuLink[];
	// URL of the header image, or '' for none.
	headerImage: string;
	// Where it goes (config headerImagePosition): above the site heading, between heading and slogan, or below.
	headerImagePosition: 'top' | 'between' | 'bottom';
	// True once the server's values (or, if it can't be reached, the defaults) are in.
	loaded: boolean;
};

const defaults: SiteBranding = {
	windowTitle: {
		en: '{page} - My Server',
		de: '{page} - Mein Server',
		es: '{page} - Mi servidor',
		fr: '{page} - Mon serveur'
	},
	siteHeading: { en: 'My Server', de: 'Mein Server', es: 'Mi servidor', fr: 'Mon serveur' },
	siteHeadingTagline: {
		en: 'For all friends',
		de: 'Für alle Freunde',
		es: 'Para todos los amigos',
		fr: 'Pour tous les amis'
	},
	siteHeadingSlogan: {
		en: 'The best server in the world.',
		de: 'Der beste Server der Welt.',
		es: 'El mejor servidor del mundo.',
		fr: 'Le meilleur serveur du monde.'
	},
	siteHeadingTaglinePulse: true,
	avatarPose: 'walk',
	avatarAngle: 0,
	avatarRotate: true,
	statusBar: true,
	serverAddress: '',
	defaultLanguage: 'en',
	languages: [],
	links: [],
	headerImage: '',
	headerImagePosition: 'top',
	loaded: true
};

// Texts start empty so a text the server hides never flashes up before /api/status answers.
export const branding = $state<SiteBranding>({
	...defaults,
	siteHeading: '',
	siteHeadingTagline: '',
	siteHeadingSlogan: '',
	loaded: false
});

// Live values for the status bar, refreshed by refreshServerStatus().
export const serverStatus = $state<{ online: boolean; players: number | null; maxPlayers: number | null }>({
	online: true,
	players: null,
	maxPlayers: null
});

function applyServerStatus(status: StatusResponse) {
	serverStatus.online = true;
	serverStatus.players = typeof status.onlinePlayers === 'number' ? status.onlinePlayers : null;
	serverStatus.maxPlayers = typeof status.maxPlayers === 'number' ? status.maxPlayers : null;
}

/** Asks the server again; the web server only runs with the Minecraft server, so no answer means offline. */
export function refreshServerStatus(): Promise<void> {
	return getStatus()
		.then(applyServerStatus)
		.catch(() => {
			serverStatus.online = false;
		});
}

let loading: Promise<void> | null = null;

export function loadBranding(): Promise<void> {
	if (loading) {
		return loading;
	}
	loading = getStatus()
		.then((status) => {
			branding.defaultLanguage = status.defaultLanguage?.trim() || defaults.defaultLanguage;
			branding.languages = status.languages ?? [];
			branding.links = status.links ?? [];
			branding.headerImage = status.headerImage || '';
			const next = status.branding;
			branding.windowTitle = next?.windowTitle ?? defaults.windowTitle;
			branding.headerImagePosition =
				next?.headerImagePosition === 'between' || next?.headerImagePosition === 'bottom'
					? next.headerImagePosition
					: 'top';
			branding.siteHeading = next?.siteHeading ?? '';
			branding.siteHeadingTagline = next?.siteHeadingTagline ?? defaults.siteHeadingTagline;
			branding.siteHeadingSlogan = next?.siteHeadingSlogan ?? defaults.siteHeadingSlogan;
			branding.siteHeadingTaglinePulse = next?.siteHeadingTaglinePulse !== false;
			branding.avatarPose = next?.avatarPose ?? defaults.avatarPose;
			branding.avatarAngle = typeof next?.avatarAngle === 'number' ? next.avatarAngle : defaults.avatarAngle;
			branding.avatarRotate = next?.avatarRotate !== false;
			branding.statusBar = next?.statusBar !== false;
			branding.serverAddress = next?.serverAddress?.trim() ?? '';
			applyServerStatus(status);
			branding.loaded = true;
		})
		.catch(() => {
			Object.assign(branding, defaults);
		})
		.finally(() => {
			loading = null;
		});
	return loading;
}

/** Whether a site heading is configured (it then shows on every page). */
export function hasSiteHeading(): boolean {
	const heading = branding.siteHeading;
	return localizedText(heading, 'en') !== '';
}

/** Tag for a page's own title: the site heading is the page's h1 when there is one. */
export function pageTitleTag(): 'h1' | 'h2' {
	return hasSiteHeading() ? 'h2' : 'h1';
}

/** Config text in the given language, falling back to English, then to the first language given. */
export function localizedText(text: LocalizedText, locale: string): string {
	if (typeof text === 'string') {
		return text;
	}
	// Empty entries mean "not set" (the default config lists every language with an empty heading).
	const filled = (value: string | undefined) => (value?.trim() ? value : undefined);
	return filled(text[locale]) ?? filled(text.en) ?? Object.values(text).find((value) => value.trim()) ?? '';
}

export function linkLabel(link: MenuLink, locale: string): string {
	return localizedText(link.label, locale) || link.url;
}

/** The browser tab title in the given language (the locale is passed in: i18n imports this module). */
export function formatWindowTitle(page: string, locale: string): string {
	const template = localizedText(branding.windowTitle, locale) || localizedText(defaults.windowTitle, locale);
	return template.replaceAll('{page}', page).replaceAll('<page>', page);
}
