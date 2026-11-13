import { getStatus } from '#lib/api.ts';
import type { CustomFont, CustomPage, LocalizedText, MenuLink, SloganStyle, StatusResponse } from '#lib/types.ts';

const sloganStyles: SloganStyle[] = ['line', 'plate', 'above', 'ribbon', 'quote'];

/** The server owner's settings for the site, from /api/status. */
export type SiteBranding = {
	// A browser tab title template with {page}.
	windowTitle: LocalizedText;
	siteHeading: LocalizedText;
	siteHeadingSlogan: LocalizedText;
	siteHeadingFont: 'blocky' | 'gothic' | 'custom';
	// With siteHeadingFont 'custom'.
	customFont: CustomFont | null;
	siteHeadingSloganStyle: SloganStyle;
	pageTitleFont: 'tiny' | 'blocky' | 'minecraft';
	avatarPose: string;
	// Degrees.
	avatarAngle: number;
	avatarRotate: boolean;
	statusBar: boolean;
	serverAddress: string;
	defaultLanguage: string;
	languages: string[];
	links: MenuLink[];
	pages: CustomPage[];
	// The custom page that is the start page (/), or '' for the ranking (then also at /ranking).
	startPage: string;
	nameClashes: string[];
	// An address, or '' for none.
	headerImage: string;
	headerImagePosition: 'top' | 'between' | 'bottom';
	loaded: boolean;
};

function defaults(): SiteBranding {
	return {
		windowTitle: {
			en: '{page} - My Server',
			de: '{page} - Mein Server',
			es: '{page} - Mi servidor',
			fr: '{page} - Mon serveur'
		},
		siteHeading: { en: 'My Server', de: 'Mein Server', es: 'Mi servidor', fr: 'Mon serveur' },
		siteHeadingSlogan: {
			en: 'The best server in the world.',
			de: 'Der beste Server der Welt.',
			es: 'El mejor servidor del mundo.',
			fr: 'Le meilleur serveur du monde.'
		},
		siteHeadingFont: 'blocky',
		siteHeadingSloganStyle: 'line',
		customFont: null,
		pageTitleFont: 'tiny',
		avatarPose: 'walk',
		avatarAngle: 0,
		avatarRotate: true,
		statusBar: true,
		serverAddress: '',
		defaultLanguage: 'en',
		languages: [],
		links: [],
		pages: [],
		startPage: '',
		nameClashes: [],
		headerImage: '',
		headerImagePosition: 'top',
		loaded: true
	};
}

// Empty texts until /api/status answers: a text the server hides must never flash up first.
export const branding = $state<SiteBranding>({
	...defaults(),
	siteHeading: '',
	siteHeadingSlogan: '',
	loaded: false
});

export const serverStatus = $state<{ online: boolean; players: number | null; maxPlayers: number | null }>({
	online: true,
	players: null,
	maxPlayers: null
});

function applyStatus(status: StatusResponse): void {
	serverStatus.online = true;
	serverStatus.players = typeof status.onlinePlayers === 'number' ? status.onlinePlayers : null;
	serverStatus.maxPlayers = typeof status.maxPlayers === 'number' ? status.maxPlayers : null;
	// A player who joined meanwhile may share a name with another now.
	if (Array.isArray(status.nameClashes)) {
		branding.nameClashes = status.nameClashes;
	}
}

export async function refreshServerStatus(): Promise<void> {
	try {
		applyStatus(await getStatus());
	} catch {
		// The web server only runs with the Minecraft server, so no answer means it is offline.
		serverStatus.online = false;
	}
}

let pending: Promise<void> | null = null;

/** Reads the settings; concurrent calls share one request, a call after it settled asks again. */
export function loadBranding(): Promise<void> {
	pending ??= getStatus()
		.then(
			(status) => {
				const base = defaults();
				const style = status.branding ?? {};
				Object.assign(branding, {
					defaultLanguage: status.defaultLanguage?.trim() || base.defaultLanguage,
					languages: status.languages ?? base.languages,
					links: status.links ?? base.links,
					pages: status.pages ?? base.pages,
					startPage: status.startPage ?? '',
					nameClashes: status.nameClashes ?? base.nameClashes,
					headerImage: status.headerImage ?? '',
					windowTitle: style.windowTitle ?? base.windowTitle,
					headerImagePosition:
						style.headerImagePosition === 'between' || style.headerImagePosition === 'bottom'
							? style.headerImagePosition
							: 'top',
					siteHeading: style.siteHeading ?? '',
					siteHeadingSlogan: style.siteHeadingSlogan ?? base.siteHeadingSlogan,
					// Custom only with at least one file to load.
					siteHeadingFont:
						style.siteHeadingFont === 'gothic'
							? 'gothic'
							: style.siteHeadingFont === 'custom' && status.customFont?.sources.length
								? 'custom'
								: 'blocky',
					customFont: status.customFont ?? null,
					siteHeadingSloganStyle: sloganStyles.includes(style.siteHeadingSloganStyle as SloganStyle)
						? (style.siteHeadingSloganStyle as SloganStyle)
						: 'line',
					pageTitleFont:
						style.pageTitleFont === 'minecraft' || style.pageTitleFont === 'blocky' ? style.pageTitleFont : 'tiny',
					avatarPose: style.avatarPose ?? base.avatarPose,
					avatarAngle: typeof style.avatarAngle === 'number' ? style.avatarAngle : base.avatarAngle,
					avatarRotate: style.avatarRotate !== false,
					statusBar: style.statusBar !== false,
					serverAddress: style.serverAddress?.trim() ?? ''
				} satisfies Partial<SiteBranding>);
				applyStatus(status);
				branding.loaded = true;
			},
			() => {
				Object.assign(branding, defaults());
			}
		)
		.finally(() => {
			pending = null;
		});
	return pending;
}

function filled(text: string | undefined): text is string {
	return typeof text === 'string' && text.trim() !== '';
}

/**
 * A config text in the first of these languages it has, then the server's default language, then English, then any.
 * Empty counts as not set: the default config lists every language with an empty heading.
 */
export function localizedText(text: LocalizedText | undefined, languages: readonly string[]): string {
	if (typeof text === 'string') {
		return text;
	}
	if (!text) {
		return '';
	}
	for (const code of [...languages, branding.defaultLanguage, 'en']) {
		if (filled(text[code])) {
			return text[code];
		}
	}
	return Object.values(text).find(filled) ?? '';
}

export function hasSiteHeading(): boolean {
	return localizedText(branding.siteHeading, ['en']).trim() !== '';
}

/** h2 when the site heading is there (it is the page's h1 then), else h1. */
export function pageTitleTag(): 'h1' | 'h2' {
	return hasSiteHeading() ? 'h2' : 'h1';
}

export function linkLabel(link: MenuLink, languages: readonly string[]): string {
	return localizedText(link.label, languages) || link.url;
}

export function pageLabel(page: CustomPage, languages: readonly string[]): string {
	return localizedText(page.label ?? '', languages) || page.id;
}

/** The browser tab's title; languages are passed in because i18n imports this module. */
export function formatWindowTitle(page: string, languages: readonly string[]): string {
	const template = localizedText(branding.windowTitle, languages) || localizedText(defaults().windowTitle, languages);
	return template.replaceAll('{page}', page).replaceAll('<page>', page);
}
