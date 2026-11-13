import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { describe, expect, it } from 'vitest';
import {
	defaultThemes,
	isThemePreference,
	resolveTheme,
	themeMode,
	themes,
	type ThemePreference
} from './themes';

const ids = themes.map((theme) => theme.id);
const preferences: ThemePreference[] = ['system', ...ids];

describe('theme registry', () => {
	it('lists the themes in the switcher order', () => {
		expect(themes.map(({ id, mode, icon, label }) => ({ id, mode, icon, label }))).toEqual([
			{ id: 'birch', mode: 'light', icon: 'sun', label: 'settings.theme.birch' },
			{ id: 'deepslate', mode: 'dark', icon: 'moon', label: 'settings.theme.deepslate' }
		]);
	});

	it('has unique ids and a default of the right mode for each mode', () => {
		expect(new Set(ids).size).toBe(ids.length);
		expect(themeMode(defaultThemes.light)).toBe('light');
		expect(themeMode(defaultThemes.dark)).toBe('dark');
	});

	it('knows valid preferences', () => {
		for (const preference of preferences) {
			expect(isThemePreference(preference)).toBe(true);
		}
		for (const value of [null, undefined, '', 'System', 'sepia', 'constructor', 1]) {
			expect(isThemePreference(value)).toBe(false);
		}
	});

	it('resolves system by the device, dark when it says nothing', () => {
		expect(resolveTheme('system', true)).toBe('birch');
		expect(resolveTheme('system', false)).toBe('deepslate');
		expect(resolveTheme('system')).toBe('deepslate');
	});

	it('resolves a theme to itself whatever the device says', () => {
		for (const id of ids) {
			expect(resolveTheme(id, true)).toBe(id);
			expect(resolveTheme(id, false)).toBe(id);
		}
	});
});

// The start script can't import the registry, so it repeats it; these keep the two from drifting apart.
describe('app.html start script', () => {
	const html = readFileSync(new URL('../../app.html', import.meta.url), 'utf8');
	const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1] ?? '';
	const literal = (name: string) => {
		const text = script.match(new RegExp(`var ${name} = (\\{[^}]*\\});`))?.[1];
		return text === undefined ? undefined : runInNewContext(`(${text})`);
	};

	type Result = {
		attributes: Record<string, string>;
		colorScheme: string;
		stored: string | null;
	};

	function run(stored: string | null, prefersLight: boolean | null, storageThrows = false): Result {
		const store = new Map<string, string>();
		if (stored !== null) {
			store.set('stats-webui-plus-theme', stored);
		}
		const attributes: Record<string, string> = {};
		const root = {
			style: { colorScheme: '' },
			setAttribute: (name: string, value: string) => {
				attributes[name] = value;
			}
		};
		const blocked = () => {
			throw new Error('blocked');
		};
		const localStorage = storageThrows
			? { getItem: blocked, setItem: blocked }
			: {
					getItem: (key: string) => store.get(key) ?? null,
					setItem: (key: string, value: string) => store.set(key, value)
				};
		const window =
			prefersLight === null
				? {}
				: { matchMedia: (query: string) => ({ matches: query === '(prefers-color-scheme: light)' && prefersLight }) };
		runInNewContext(script, { window, localStorage, document: { documentElement: root } });
		return {
			attributes,
			colorScheme: root.style.colorScheme,
			stored: store.get('stats-webui-plus-theme') ?? null
		};
	}

	it('has a script', () => {
		expect(script).toContain('stats-webui-plus-theme');
	});

	it('lists the registry themes with their modes and the same defaults', () => {
		expect(literal('modes')).toEqual(Object.fromEntries(themes.map((theme) => [theme.id, theme.mode])));
		expect(literal('defaults')).toEqual(defaultThemes);
	});

	it.each(preferences.flatMap((preference) => [true, false].map((light) => [preference, light] as const)))(
		'applies %s like the registry (device prefers light: %s)',
		(preference, light) => {
			const id = resolveTheme(preference, light);
			const result = run(preference, light);
			expect(result.attributes).toEqual({
				'data-theme': id,
				'data-mode': themeMode(id),
				'data-theme-preference': preference
			});
			expect(result.colorScheme).toBe(themeMode(id));
			expect(result.stored).toBe(preference);
		}
	);

	it.each([null, 'sepia', 'constructor', ''])('treats %j as system and writes it back', (stored) => {
		const result = run(stored, true);
		expect(result.attributes['data-theme-preference']).toBe('system');
		expect(result.attributes['data-theme']).toBe(resolveTheme('system', true));
		expect(result.stored).toBe('system');
	});

	it('counts blocked storage as system', () => {
		const result = run(null, false, true);
		expect(result.attributes['data-theme-preference']).toBe('system');
		expect(result.attributes['data-theme']).toBe(resolveTheme('system', false));
	});

	it('counts a device without matchMedia as dark', () => {
		expect(run('system', null).attributes['data-theme']).toBe(resolveTheme('system'));
	});
});
