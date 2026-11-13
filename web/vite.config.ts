/// <reference types="vitest/config" />
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import { licenses } from './licenses.ts';

// The running mod; in compose.yaml the dev server's container.
const api = process.env.STATS_API_URL ?? 'http://127.0.0.1:25580';

export default defineConfig({
	server: {
		port: 5173,
		proxy: {
			// The mod's API and its Mojang proxy (skin cache, default skins for players without one of their own).
			'/api': api,
			'/mojang': api,
			// The game's textures and icons: the mod draws them at server start (with their sizes for the UI).
			'/mc': api,
			// The configured header image (config headerImage) and the favicon (the Minecraft server's icon or ours).
			'/header-image': api,
			'^/favicon$': api
		}
	},
	preview: {
		port: 4173
	},
	test: {
		include: ['src/**/*.test.ts', '*.test.ts'],
		// No localStorage of Node's own (Node 25 and newer): tests that need one stub it, the rest run without, as in
		// a browser that blocks it.
		execArgv: ['--no-experimental-webstorage']
	},
	plugins: [
		// licenses.json and THIRD-PARTY-NOTICES.txt for /licenses, from the modules of the browser bundle.
		licenses(),
		sveltekit({
			compilerOptions: {
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			paths: {
				relative: false
			},
			// The mod version from the Gradle build (yarnBuild), so the same sources give the same files; SvelteKit's
			// default is the build time. yarn dev on its own keeps that default.
			version: {
				name: process.env.STATS_WEBUI_VERSION,
				// No polling for a new version (SvelteKit 3 would every hour; it still checks when the tab comes back).
				pollInterval: 0
			},
			prerender: {
				// /favicon is answered by the mod (the Minecraft server's icon or ours), not by a file of this build, so
				// the prerender's crawl can't find it; every other missing link still fails the build.
				handleHttpError: ({ path, message }) => {
					if (path === '/favicon') {
						return;
					}
					throw new Error(message);
				}
			},
			adapter: adapter({
				pages: 'build',
				assets: 'build',
				fallback: '200.html',
				precompress: false,
				strict: true
			})
		})
	]
});
