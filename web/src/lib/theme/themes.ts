import type { IconName } from '#lib/components/iconNames.ts';
import type { MessageKey } from '#lib/i18n/messages/en.ts';

/** What the browser's own controls and scrollbars follow, and what components may key on (`html[data-mode]`). */
export type ThemeMode = 'light' | 'dark';

export type Theme = {
	// Also its CSS name: html[data-theme='<id>'] in themes/<id>.css.
	id: string;
	mode: ThemeMode;
	icon: IconName;
	label: MessageKey;
};

// The order of the theme switcher. A new theme is one entry here plus its file under themes/, imported by theme.css;
// app.html's start script repeats the ids, modes and defaults (themes.test.ts checks that they match).
export const themes = [
	{ id: 'birch', mode: 'light', icon: 'sun', label: 'settings.theme.birch' },
	{ id: 'deepslate', mode: 'dark', icon: 'moon', label: 'settings.theme.deepslate' }
] as const satisfies readonly Theme[];

export type ThemeId = (typeof themes)[number]['id'];

/** The visitor's choice: a theme, or the device's light/dark setting. */
export type ThemePreference = 'system' | ThemeId;

/** The theme "System" shows for each of the device's settings. */
export const defaultThemes: Record<ThemeMode, ThemeId> = {
	light: 'birch',
	dark: 'deepslate'
};

export function isThemeId(value: unknown): value is ThemeId {
	return themes.some((theme) => theme.id === value);
}

export function isThemePreference(value: unknown): value is ThemePreference {
	return value === 'system' || isThemeId(value);
}

/** The theme a preference shows. A device that says nothing (or isn't asked) counts as dark. */
export function resolveTheme(preference: ThemePreference, prefersLight = false): ThemeId {
	if (preference === 'system') {
		return defaultThemes[prefersLight ? 'light' : 'dark'];
	}
	return preference;
}

export function themeMode(id: ThemeId): ThemeMode {
	return themes.find((theme) => theme.id === id)?.mode ?? 'dark';
}
