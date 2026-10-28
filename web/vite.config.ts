/// <reference types="vitest/config" />
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import { serverIcon } from './server-icon-plugin.js';

// The running mod; in compose.yaml the dev server's container.
const api = process.env.STATS_API_URL ?? 'http://127.0.0.1:25580';

export default defineConfig({
	server: {
		port: 5173,
		proxy: {
			// The mod's API and its Mojang proxy (skin cache, default skins for players without one of their own).
			'/api': api,
			'/mojang': api
		}
	},
	preview: {
		port: 4173
	},
	test: {
		include: ['src/**/*.test.ts']
	},
	plugins: [
		serverIcon(),
		sveltekit({
			compilerOptions: {
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			paths: {
				relative: false
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
