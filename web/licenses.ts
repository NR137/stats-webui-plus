/**
 * The licenses page's data, made by the build: our own license, the font's, and every npm package whose code ends up
 * in the web UI's JS (not the build tools around it). It is collected from the modules of the browser bundle itself,
 * so a dependency that gets added or dropped shows up or disappears without anyone keeping a list.
 *
 * Writes licenses.json (for /licenses) and THIRD-PARTY-NOTICES.txt next to index.html; both end up in the mod JAR.
 * Fails the build when a bundled package has a license that isn't on the allow list or no license text.
 */
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Plugin } from 'vite';

const WEB = dirname(fileURLToPath(import.meta.url));
const NODE_MODULES = join(WEB, 'node_modules');
export const PROJECT_LICENSE = resolve(WEB, '..', 'LICENSE');
export const FONT_LICENSE = join(WEB, 'src', 'lib', 'assets', 'fonts', 'OFL.txt');

/** Licenses that only ask for their notice to be passed on. Anything else needs a look before it ships. */
// OFL-1.1: fonts (the site heading's Silkscreen), which may be bundled with software as long as their license goes along.
export const ALLOWED = new Set(['MIT', 'Apache-2.0', 'ISC', 'BSD-2-Clause', 'BSD-3-Clause', '0BSD', 'OFL-1.1']);

/** Packages offered under a choice of licenses ("A OR B"): the one we use them under. */
const CHOSEN: Record<string, string> = {
	// (MPL-2.0 OR Apache-2.0); Apache-2.0 only asks for its text (DOMPurify has no NOTICE file) and we change nothing.
	dompurify: 'Apache-2.0'
};

/** Bundled modules that aren't files of a package: Vite's and Rolldown's helpers, SvelteKit's generated start-up code. */
export function packageOfVirtual(id: string): string | null {
	if (id.startsWith('\0vite/')) return 'vite';
	if (id.startsWith('\0rolldown/')) return 'rolldown';
	if (id.includes('/.svelte-kit/generated/') || id.includes('virtual:__sveltekit')) return '@sveltejs/kit';
	return null;
}

/** The package a bundled module belongs to: the last node_modules folder in its path (nested copies are their own). */
export function packageOfModule(id: string): string | null {
	const virtual = packageOfVirtual(id);
	if (virtual) return virtual;
	const parts = id.replace(/\\/g, '/').split('/node_modules/');
	if (parts.length < 2) return null;
	const rest = parts[parts.length - 1].split('/');
	return rest[0].startsWith('@') ? `${rest[0]}/${rest[1]}` : rest[0];
}

export type PackageLicense = {
	name: string;
	version: string;
	license: string;
	url: string;
	text: string;
};

export type Licenses = {
	project: { name: string; url: string; license: string; text: string };
	font: { name: string; author: string; url: string; license: string; text: string };
	packages: PackageLicense[];
};

export function mitText(copyright: string) {
	return `MIT License

${copyright}

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
`;
}

/** The license a package is used under: its own, or the one picked from an "A OR B" choice. */
export function licenseOf(name: string, declared: string) {
	const chosen = CHOSEN[name];
	if (chosen) {
		if (!declared.split(/\s+OR\s+|[()]/).includes(chosen)) {
			throw new Error(`${name} is "${declared}" now, which doesn't offer ${chosen} any more; check its license`);
		}
		return chosen;
	}
	return declared;
}

/** A license file's text without the licenses of the tool's own dependencies, which some tools append to theirs
 * (Vite: "# Licenses of bundled dependencies"); those dependencies are build-time code, not in our bundle. */
export function trimLicenseText(text: string) {
	const appended = text.search(/^#+\s*Licenses of bundled dependencies/im);
	return (appended > 0 ? text.slice(0, appended) : text).trim() + '\n';
}

/** Whether a license text names its copyright holder ("Copyright (c) ...", "Copyright 2020 ..."; not Apache's
 * "Copyright [yyyy] [name of copyright owner]" template line). */
export function hasCopyright(text: string) {
	return /^\s*copyright\s+(\(c\)|©|\d{4})/im.test(text);
}

/** The holder from a "/*! @license name version | (c) holder | ..." banner (DOMPurify's license file is the bare
 * Apache text; its copyright is only in the banner of its code). */
export function copyrightFromBanner(code: string): string | null {
	const banner = code.slice(0, 2000).match(/\/\*![^]*?@license[^]*?\*\//);
	const holder = banner?.[0].match(/(?:\(c\)|©)\s*([^|*\n]+)/i);
	return holder ? `Copyright (c) ${holder[1].trim()}` : null;
}

/** The copyright line from the banners of a package's entry files, for license files that have none. */
function bannerCopyright(dir: string, meta: Record<string, unknown>): string | null {
	for (const field of ['module', 'main', 'browser']) {
		const entry = meta[field];
		if (typeof entry !== 'string' || !existsSync(join(dir, entry))) continue;
		const found = copyrightFromBanner(readFileSync(join(dir, entry), 'utf8'));
		if (found) return found;
	}
	return null;
}

/** package.json's author ("Name <mail> (url)" or { name }) without mail and URL. */
export function authorOf(author: unknown): string {
	const raw = typeof author === 'string' ? author : ((author as { name?: string } | undefined)?.name ?? '');
	return raw.replace(/<[^>]*>|\([^)]*\)/g, '').trim();
}

function readPackage(name: string): PackageLicense {
	const dir = join(NODE_MODULES, name);
	const meta = JSON.parse(readFileSync(join(dir, 'package.json'), 'utf8'));
	const declared = typeof meta.license === 'string' ? meta.license : (meta.license?.type ?? '');
	const license = licenseOf(name, declared);
	if (!ALLOWED.has(license)) {
		throw new Error(`${name} ${meta.version} is licensed "${declared}", which isn't on the allow list (licenses.ts)`);
	}
	const file = readdirSync(dir).find((entry) => /^(licen[cs]e|copying)(\.(md|txt))?$/i.test(entry));
	let text = file ? trimLicenseText(readFileSync(join(dir, file), 'utf8')) : '';
	// MIT without a file (e.g. @daidr/minecraft-skin-renderer: package.json and README say MIT, the repo has no
	// LICENSE): the MIT text is the same for everyone apart from the copyright line, made from package.json's author.
	const author = authorOf(meta.author);
	if (!text.trim() && license === 'MIT' && author) {
		text = mitText(`Copyright (c) ${author}`);
	}
	if (!text.trim()) {
		throw new Error(`${name} ${meta.version} has no license text and no MIT author to make one from; check it`);
	}
	if (!hasCopyright(text)) {
		const copyright = bannerCopyright(dir, meta);
		if (!copyright) {
			throw new Error(`${name} ${meta.version}: its license text names no copyright holder and its code has no banner`);
		}
		text = `${copyright}\n\n${text}`;
	}
	const repository = typeof meta.repository === 'string' ? meta.repository : meta.repository?.url;
	const url = meta.homepage ?? repository?.replace(/^git\+/, '').replace(/^git:/, 'https:').replace(/\.git$/, '') ?? '';
	return { name, version: meta.version, license, url, text };
}

export function collectLicenses(packageNames: Iterable<string>): Licenses {
	const packages = [...new Set(packageNames)].sort().map(readPackage);
	return {
		project: {
			name: 'Stats WebUI Plus',
			url: 'https://github.com/NR137/stats-webui-plus',
			license: 'MIT',
			text: readFileSync(PROJECT_LICENSE, 'utf8')
		},
		font: {
			name: "mac's Minecraft",
			author: 'macimas',
			url: 'https://github.com/macimas-fonts/macsMinecraft',
			license: 'OFL-1.1',
			text: readFileSync(FONT_LICENSE, 'utf8')
		},
		packages
	};
}

/** THIRD-PARTY-NOTICES.txt: the same as the page, as one plain file. */
export function noticesText(licenses: Licenses) {
	const rule = '='.repeat(78);
	const sections = [
		`${licenses.font.name} by ${licenses.font.author} (font, ${licenses.font.license})\n${licenses.font.url}\n\n${licenses.font.text.trim()}`,
		...licenses.packages.map(
			(entry) => `${entry.name} ${entry.version} (${entry.license})\n${entry.url}\n\n${entry.text.trim()}`
		)
	];
	return (
		`Third-party notices of the ${licenses.project.name} web UI\n\n` +
		`The web UI contains the font and the npm packages below. ${licenses.project.name} itself is ` +
		`${licenses.project.license}-licensed (LICENSE).\n\n` +
		sections.map((section) => `${rule}\n${section}\n`).join('\n')
	);
}

/** Without a bundle to look at (yarn dev), the packages the UI depends on plus the build's own helpers. */
function devPackages() {
	const meta = JSON.parse(readFileSync(join(WEB, 'package.json'), 'utf8'));
	return [...Object.keys(meta.dependencies ?? {}), 'svelte', '@sveltejs/kit', 'vite', 'rolldown'].filter((name) =>
		existsSync(join(NODE_MODULES, name, 'package.json'))
	);
}

export function licenses(): Plugin {
	return {
		name: 'stats-webui-licenses',
		configureServer(server) {
			server.middlewares.use((request, response, next) => {
				const path = request.url?.split('?')[0];
				if (path !== '/licenses.json' && path !== '/THIRD-PARTY-NOTICES.txt') {
					return next();
				}
				const data = collectLicenses(devPackages());
				const json = path === '/licenses.json';
				response.setHeader('Content-Type', json ? 'application/json' : 'text/plain; charset=utf-8');
				response.end(json ? JSON.stringify(data) : noticesText(data));
			});
		},
		generateBundle(_options, bundle) {
			// The server build (prerendering) never reaches browsers; only the client bundle counts.
			if (this.environment?.name !== 'client') {
				return;
			}
			const names = new Set<string>();
			for (const output of Object.values(bundle)) {
				if (output.type !== 'chunk') continue;
				for (const id of Object.keys(output.modules)) {
					const name = packageOfModule(id);
					if (name) names.add(name);
				}
			}
			const data = collectLicenses(names);
			this.emitFile({ type: 'asset', fileName: 'licenses.json', source: JSON.stringify(data) });
			this.emitFile({ type: 'asset', fileName: 'THIRD-PARTY-NOTICES.txt', source: noticesText(data) });
		}
	};
}
