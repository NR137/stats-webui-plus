import { describe, expect, it } from 'vitest';
import {
	authorOf,
	collectLicenses,
	copyrightFromBanner,
	hasCopyright,
	licenseOf,
	noticesText,
	packageOfModule,
	trimLicenseText
} from './licenses.ts';

describe('packageOfModule', () => {
	it('names the package of a file in node_modules, scoped or not, nested or not', () => {
		expect(packageOfModule('/w/web/node_modules/svelte/src/internal/client/index.js')).toBe('svelte');
		expect(packageOfModule('/w/web/node_modules/@daidr/minecraft-skin-renderer/dist/index.js')).toBe(
			'@daidr/minecraft-skin-renderer'
		);
		expect(packageOfModule('/w/web/node_modules/a/node_modules/b/index.js')).toBe('b');
	});

	it('gives the helpers the build adds to their package', () => {
		expect(packageOfModule('\0vite/preload-helper.js')).toBe('vite');
		expect(packageOfModule('\0rolldown/runtime.js')).toBe('rolldown');
		expect(packageOfModule('/w/web/.svelte-kit/generated/client-optimized/app.js')).toBe('@sveltejs/kit');
	});

	it('skips our own code', () => {
		expect(packageOfModule('/w/web/src/routes/+page.svelte')).toBeNull();
	});
});

describe('licenseOf', () => {
	it('uses DOMPurify under Apache-2.0 out of its MPL-2.0 OR Apache-2.0 choice', () => {
		expect(licenseOf('dompurify', '(MPL-2.0 OR Apache-2.0)')).toBe('Apache-2.0');
	});

	it('fails when a chosen license is no longer offered', () => {
		expect(() => licenseOf('dompurify', 'MPL-2.0')).toThrow(/Apache-2.0/);
	});
});

describe('authorOf', () => {
	it("takes the name out of package.json's author, without mail and URL", () => {
		expect(authorOf('Dai Xuezhou <i@daidr.me>')).toBe('Dai Xuezhou');
		expect(authorOf('A B <a@b.c> (https://b.c)')).toBe('A B');
		expect(authorOf({ name: 'C D', email: 'c@d.e' })).toBe('C D');
		expect(authorOf(undefined)).toBe('');
	});
});

describe('trimLicenseText', () => {
	it("drops the licenses of a tool's own dependencies appended to its license", () => {
		const text = '# Vite core license\nMIT\n\n# Licenses of bundled dependencies\nlots';
		expect(trimLicenseText(text)).toBe('# Vite core license\nMIT\n');
		expect(trimLicenseText('MIT License\n\nCopyright (c) A\n')).toBe('MIT License\n\nCopyright (c) A\n');
	});
});

describe('copyright lines', () => {
	it("knows a copyright line from Apache's template line", () => {
		expect(hasCopyright('MIT License\n\nCopyright (c) 2020 A')).toBe(true);
		expect(hasCopyright('Copyright 2024 A')).toBe(true);
		expect(hasCopyright('   Copyright [yyyy] [name of copyright owner]')).toBe(false);
	});

	it('takes the holder from a license banner', () => {
		const code = '/*! @license DOMPurify 3.4.17 | (c) Cure53 and other contributors | Released under ... */\nfunction x() {}';
		expect(copyrightFromBanner(code)).toBe('Copyright (c) Cure53 and other contributors');
		expect(copyrightFromBanner('function x() {}')).toBeNull();
	});
});

describe('collectLicenses', () => {
	const data = collectLicenses(['marked', 'dompurify', '@daidr/minecraft-skin-renderer', 'svelte', 'vite']);

	it('has every package with a license from the allow list and its text', () => {
		expect(data.packages.map((entry) => entry.name)).toEqual([
			'@daidr/minecraft-skin-renderer',
			'dompurify',
			'marked',
			'svelte',
			'vite'
		]);
		for (const entry of data.packages) {
			expect(entry.text.length, entry.name).toBeGreaterThan(500);
			expect(entry.version, entry.name).toMatch(/^\d/);
		}
	});

	it('adds the copyright lines the license files lack', () => {
		const byName = Object.fromEntries(data.packages.map((entry) => [entry.name, entry]));
		expect(byName['dompurify'].license).toBe('Apache-2.0');
		expect(byName['dompurify'].text).toContain('Cure53');
		expect(byName['@daidr/minecraft-skin-renderer'].license).toBe('MIT');
		expect(byName['@daidr/minecraft-skin-renderer'].text).toContain('Copyright (c) Dai Xuezhou');
		expect(byName['vite'].text).not.toContain('bundled dependencies');
	});

	it("has our own license and the font's", () => {
		expect(data.project.text).toContain('MIT License');
		expect(data.font.text).toContain('SIL Open Font License');
		expect(data.font.text).toContain('macimas');
	});

	it('writes the plain notices with the font and every package', () => {
		const text = noticesText(data);
		expect(text).toContain("mac's Minecraft by macimas");
		for (const entry of data.packages) {
			expect(text).toContain(`${entry.name} ${entry.version} (${entry.license})`);
		}
	});
});
