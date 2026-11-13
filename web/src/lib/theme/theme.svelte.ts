import {
	isThemePreference,
	resolveTheme,
	themeMode,
	type ThemeId,
	type ThemePreference
} from '#lib/theme/themes.ts';

// The same key as app.html's start script, which applies the stored theme before the page is drawn.
const STORAGE_KEY = 'stats-webui-plus-theme';

/** The visitor's choice and the theme it shows. */
export const theme = $state<{ preference: ThemePreference; id: ThemeId }>({
	preference: 'system',
	id: resolveTheme('system')
});

// Server side and while prerendering there is no window; the start script sets the real theme in the browser.
const lightQuery =
	typeof window !== 'undefined' && typeof window.matchMedia === 'function'
		? window.matchMedia('(prefers-color-scheme: light)')
		: null;

function readPreference(): ThemePreference {
	try {
		const stored = localStorage.getItem(STORAGE_KEY);
		if (isThemePreference(stored)) {
			return stored;
		}
		localStorage.setItem(STORAGE_KEY, 'system');
	} catch {
		// Storage can be blocked (private mode, site settings): then nothing is remembered.
	}
	return 'system';
}

function apply() {
	theme.id = resolveTheme(theme.preference, lightQuery?.matches ?? false);
	const mode = themeMode(theme.id);
	const root = document.documentElement;
	root.dataset.theme = theme.id;
	root.dataset.mode = mode;
	root.dataset.themePreference = theme.preference;
	root.style.colorScheme = mode;
}

export function setThemePreference(preference: ThemePreference) {
	theme.preference = preference;
	try {
		localStorage.setItem(STORAGE_KEY, preference);
	} catch {
		// Blocked storage: the choice lasts until the page is closed.
	}
	if (typeof document !== 'undefined') {
		apply();
	}
}

if (typeof window !== 'undefined') {
	theme.preference = readPreference();
	apply();
	// "System" follows the device at once when its setting changes.
	lightQuery?.addEventListener('change', () => {
		if (theme.preference === 'system') {
			apply();
		}
	});
}
