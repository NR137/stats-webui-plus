<script lang="ts">
	import StatusBar from '#lib/components/StatusBar.svelte';
	import StickyBar from '#lib/components/StickyBar.svelte';
	import { closeMenu, headerMenu } from '#lib/components/headerMenu.svelte.ts';
	import SiteFooter from '#lib/components/site/SiteFooter.svelte';
	import SiteHeading from '#lib/components/site/SiteHeading.svelte';
	import SiteMenu from '#lib/components/site/SiteMenu.svelte';
	import { branding, loadBranding } from '#lib/branding.svelte.ts';
	import { applyLocaleSettings, i18n, languageOrder, t } from '#lib/i18n/i18n.svelte.ts';
	import { loadIcons } from '#lib/mc/items.svelte.ts';
	import { loadNames } from '#lib/mc/names.svelte.ts';
	import { isActive, mainMenu, subMenu } from '#lib/menu.ts';
	import { rememberScrollBefore, scrollToContentAfter } from '#lib/scrollToContent.ts';
	import '@fontsource/jacquard-12/400.css';
	import '@fontsource/press-start-2p/400.css';
	import '@fontsource/tiny5/400.css';
	import '#lib/theme/theme.css';
	import '#lib/theme/theme.svelte.ts';
	import { afterNavigate, beforeNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { untrack, type Snippet } from 'svelte';

	let { children }: { children: Snippet } = $props();

	$effect(() => {
		loadBranding();
	});

	$effect(() => {
		const defaultLanguage = branding.defaultLanguage;
		const languages = branding.languages;
		// Re-run only when the server's settings change. applyLocaleSettings reads and writes the current
		// locale and the enabled languages; tracking those here would make this effect trigger itself forever.
		untrack(() => applyLocaleSettings(defaultLanguage, languages));
	});

	// The game's texts in the visitor's language and the icon list, both from /mc/ (the server makes them from the
	// game's files). A failed icon list is asked for again with the next language switch.
	$effect(() => {
		loadNames(i18n.locale);
		loadIcons();
	});

	// The page titles' font (config pageTitleFont, see base.css); app.html starts with the default.
	$effect(() => {
		if (branding.loaded) {
			document.documentElement.dataset.titleFont = branding.pageTitleFont;
		}
	});

	// The top menu and the row below it (menu.ts), for the header's menu and the sticky bar.
	const links = $derived(
		mainMenu(branding, t, languageOrder()).map((link) => ({ ...link, active: isActive(link.href, page.url.pathname) }))
	);
	const subEntries = $derived(subMenu(branding, languageOrder(), page.url.pathname));

	// The current page and its icon, for the menu buttons on phones.
	const current = $derived(links.find((link) => link.active) ?? subEntries.find((entry) => entry.active) ?? null);
	const currentPage = $derived(current?.label ?? t('menu.open'));

	beforeNavigate(rememberScrollBefore);

	afterNavigate((navigation) => {
		if (headerMenu.open === 'nav' || headerMenu.open === 'sticky') {
			closeMenu();
		}
		scrollToContentAfter(navigation);
	});

	// The sticky bar is only for when the header's menu has scrolled out of view.
	let menuVisible = $state(true);
</script>

<!-- No <title> here: each page sets its own (pageTitle). One here overwrote theirs once the settings had loaded. -->
<div class="shell">
	<StickyBar {links} {currentPage} currentIcon={current?.icon ?? null} {subEntries} headerMenuVisible={menuVisible} />
	<header class="top">
		<SiteHeading />
		{#if branding.loaded && branding.statusBar}
			<StatusBar />
		{/if}
		<SiteMenu {links} {subEntries} {current} {currentPage} bind:visible={menuVisible} />
	</header>
	<main>
		{@render children()}
	</main>
	<SiteFooter />
</div>

<style>
	/* At least the window's height, so main can grow and the footer stays at the bottom. */
	.shell {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
	}

	.top {
		display: flex;
		flex-direction: column;
		gap: 1rem;
		align-items: center;
		padding: 1.5rem 1rem 1.25rem;
	}

	/* The padding above leaves room for the slim menu bar over the content once the header's menu has scrolled away;
	   pages open scrolled that far (scrollToContent.ts). */
	main {
		flex: 1;
		width: min(68rem, calc(100% - 1.5rem));
		margin: 0 auto;
		padding: 2.4rem 0 3rem;
	}
</style>
