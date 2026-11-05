<script lang="ts">
	import fallbackFavicon from '$lib/assets/favicon.svg';
	import Icon from '$lib/components/Icon.svelte';
	import { closeMenu, headerMenu, registerMenuRoot, toggleMenu } from '$lib/components/headerMenu.svelte';
	import LanguageSwitcher from '$lib/components/LanguageSwitcher.svelte';
	import StatusBar from '$lib/components/StatusBar.svelte';
	import ThemeSwitcher from '$lib/components/ThemeSwitcher.svelte';
	import { branding, hasSiteHeading, linkLabel, loadBranding, localizedText } from '$lib/branding.svelte';
	import { applyLocaleSettings, i18n, localeSettings, t } from '$lib/i18n/i18n.svelte';
	import { loadNames } from '$lib/mc/names.svelte';
	import '$lib/theme/theme.css';
	import '$lib/theme/theme.svelte';
	import { afterNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { untrack } from 'svelte';

	let { children } = $props();

	let faviconHref = $state('/server-icon.png');
	let headerImageFailed = $state(false);

	$effect(() => {
		void branding.headerImage;
		headerImageFailed = false;
	});

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

	$effect(() => {
		loadNames(i18n.locale);
	});

	$effect(() => {
		const image = new Image();
		image.onerror = () => {
			faviconHref = fallbackFavicon;
		};
		image.src = '/server-icon.png';
	});

	const siteHeading = $derived(localizedText(branding.siteHeading, i18n.locale));
	const tagline = $derived(localizedText(branding.siteHeadingTagline, i18n.locale));
	const slogan = $derived(localizedText(branding.siteHeadingSlogan, i18n.locale));
	// Without a site heading there is nothing to put the image between, so it goes on top.
	const imagePosition = $derived(hasSiteHeading() ? branding.headerImagePosition : 'top');

	// The tagline is laid out like Minecraft's splash text (SplashRenderer, LogoRenderer). The title logo is 44 GUI
	// pixels high; here one line of the heading stands in for it. The splash is centred on a point 5 pixels in from
	// the logo's right end and 5 above its bottom, turned by -20 degrees, and drawn at 1.8 * 100 / (width + 32) times
	// the font size, so long texts come out smaller and every splash about as wide.
	const LOGO_HEIGHT = 44;
	const SPLASH_INSET = 5;
	const SPLASH_ANGLE = (20 * Math.PI) / 180;
	let headingElement = $state<HTMLElement>();
	let splashElement = $state<HTMLElement>();
	let splashLayout = $state({ size: 0, inset: 0, below: 0 });
	// The room under the heading is kept for the longest tagline of all languages, so switching the language never
	// changes the header's height.
	const allTaglines = $derived(
		localeSettings.enabled.map((locale) => localizedText(branding.siteHeadingTagline, locale)).filter(Boolean)
	);
	const MEASURE_SIZE = 80;
	const measureContext = typeof document === 'undefined' ? null : document.createElement('canvas').getContext('2d');

	$effect(() => {
		void tagline;
		const taglines = allTaglines;
		const heading = headingElement;
		const splash = splashElement;
		if (!heading || !splash) {
			return;
		}
		const fit = () => {
			const unit = parseFloat(getComputedStyle(heading).lineHeight) / LOGO_HEIGHT;
			const style = getComputedStyle(splash);
			const current = parseFloat(style.fontSize);
			const inset = SPLASH_INSET * unit;
			// Width in Minecraft font pixels: the font draws them 1/8 em wide.
			const layout = (width: number) => {
				// Never below 10px, where the pixel font stops being readable.
				const size = Math.max(10, (8 * unit * 1.8 * 100) / (width + 32));
				// The turned text's left end hangs below the heading; that much room is kept free under it.
				return { size, below: Math.max(0, ((width * size) / 8 / 2) * Math.sin(SPLASH_ANGLE) - inset) };
			};
			const { size } = layout((splash.offsetWidth * 8) / current);
			// Measured at one fixed size: the splash's own size depends on its text, and the pixel font's widths don't
			// scale exactly, so measuring at that size would give each language a slightly different result.
			let below = layout((splash.offsetWidth * 8) / current).below;
			if (measureContext) {
				measureContext.font = `${MEASURE_SIZE}px ${style.fontFamily}`;
				below = Math.max(
					...taglines.map((text) => layout((measureContext.measureText(text).width * 8) / MEASURE_SIZE).below)
				);
			}
			splashLayout = { size, inset, below };
		};
		const observer = new ResizeObserver(fit);
		observer.observe(heading);
		document.fonts?.ready.then(fit);
		fit();
		return () => observer.disconnect();
	});

	// Credit line: "{plus}" and "{original}" in the text become links to this fork and to the original project.
	const CREDIT_LINKS = {
		'\u0001': { label: 'Stats WebUI Plus', url: 'https://github.com/NR137/stats-webui-plus' },
		'\u0002': { label: 'mc-stats-webui', url: 'https://github.com/borrageiros/mc-stats-webui' }
	} as const;
	const credit = $derived(
		t('footer.credit', { plus: '\u0001', original: '\u0002' })
			.split(/([\u0001\u0002])/)
			.filter(Boolean)
			.map((part) => CREDIT_LINKS[part as keyof typeof CREDIT_LINKS] ?? part)
	);

	const links = $derived([
		{ href: '/', label: t('nav.ranking') },
		{ href: '/advancements', label: t('nav.advancements') },
		{ href: '/players', label: t('nav.players') },
		{ href: '/leaderboards', label: t('nav.leaderboards') },
		{ href: '/compare', label: t('nav.compare') }
	]);

	function active(href: string) {
		if (href === '/') {
			return page.url.pathname === '/';
		}
		return page.url.pathname === href || page.url.pathname.startsWith(`${href}/`);
	}

	// On phones the pages are behind one button that names the current page; it closes after choosing one.
	const menuOpen = $derived(headerMenu.open === 'nav');
	const currentPage = $derived(links.find((link) => active(link.href))?.label ?? t('nav.menu'));

	// Custom link buttons are all as wide as the longest label in any enabled language (between 8 and 16rem), so a
	// language switch never changes the row; only labels longer than that wrap. The labels come from the server's
	// config, so this is measured in the browser, in the buttons' font (0.85rem, see .links below).
	const linkLabels = $derived(
		branding.links.flatMap((link) => localeSettings.enabled.map((locale) => linkLabel(link, locale)))
	);
	let linkWidth = $state(0);

	$effect(() => {
		const labels = linkLabels;
		if (!measureContext || labels.length === 0) {
			return;
		}
		const measure = () => {
			const rem = parseFloat(getComputedStyle(document.documentElement).fontSize);
			measureContext.font = `${0.85 * rem}px ${getComputedStyle(document.body).fontFamily}`;
			const text = Math.max(...labels.map((label) => measureContext.measureText(label).width));
			// 0.8rem padding and a 2px border on each side, and a pixel to spare against rounding.
			const width = Math.ceil(text + 1.6 * rem + 4 + 1);
			linkWidth = Math.min(16 * rem, Math.max(8 * rem, width));
		};
		measure();
		document.fonts?.ready.then(measure);
	});

	afterNavigate(() => {
		if (headerMenu.open === 'nav') {
			closeMenu();
		}
	});
</script>

<svelte:head>
	<link rel="icon" href={faviconHref} />
	<title>{siteHeading || t('home.heading')}</title>
	{#if slogan}
		<meta name="description" content={slogan} />
	{/if}
</svelte:head>

<div class="shell">
	<header class="top">
		{#snippet headerImage()}
			{#if branding.headerImage && !headerImageFailed}
				<img
					class="header-image"
					src={branding.headerImage}
					alt=""
					onerror={() => {
						headerImageFailed = true;
					}}
				/>
			{/if}
		{/snippet}
		{#if imagePosition === 'top'}
			{@render headerImage()}
		{/if}
		{#if hasSiteHeading()}
			<div class="hero">
				<div
					class="title"
					style:--splash-size="{splashLayout.size}px"
					style:--splash-inset="{splashLayout.inset}px"
					style:margin-bottom={tagline ? `${splashLayout.below}px` : undefined}
				>
					<h1 class="site-heading" bind:this={headingElement}>{siteHeading}</h1>
					{#if tagline}
						<p
							class="splash"
							class:ready={splashLayout.size > 0}
							class:pulse={branding.siteHeadingTaglinePulse}
							bind:this={splashElement}
						>{tagline}</p>
					{/if}
				</div>
				{#if imagePosition === 'between'}
					{@render headerImage()}
				{/if}
				{#if slogan}
					<p class="lead">{slogan}</p>
				{/if}
			</div>
		{/if}
		{#if imagePosition === 'bottom'}
			{@render headerImage()}
		{/if}
		{#if branding.loaded && branding.statusBar}
			<StatusBar />
		{/if}
		<div
			class="bar"
			class:open={menuOpen}
			style:--link-width={linkWidth ? `${linkWidth}px` : undefined}
			use:registerMenuRoot
		>
			<button
				type="button"
				class="mc-btn toggle"
				aria-expanded={menuOpen}
				aria-controls="site-menu"
				aria-label="{t('nav.menu')}: {currentPage}"
				onclick={() => toggleMenu('nav')}
			>
				<Icon name="menu" />
				<span>{currentPage}</span>
			</button>
			<div class="pages" id="site-menu">
				<nav class="menu" aria-label={t('nav.menu')}>
					{#each links as link}
						<a
							class="mc-btn"
							class:on={active(link.href)}
							aria-current={active(link.href) ? 'page' : undefined}
							href={link.href}>{link.label}</a
						>
					{/each}
				</nav>
				{#if branding.links.length > 0}
					<nav class="links" aria-label={t('nav.links')}>
						{#each branding.links as link}
							<a
								class="mc-btn"
								href={link.url}
								target={link.newTab ? '_blank' : undefined}
								rel={link.newTab ? 'noopener noreferrer' : undefined}
								onclick={closeMenu}>{linkLabel(link, i18n.locale)}</a
							>
						{/each}
					</nav>
				{/if}
			</div>
			<div class="settings">
				<LanguageSwitcher compact />
				<ThemeSwitcher compact />
			</div>
		</div>
	</header>
	<main>
		{@render children()}
	</main>
	<footer class="credit">
		<p class="footer-links"><a href="/about">{t('nav.about')}</a></p>
		<p>{#each credit as part}{#if typeof part === 'string'}{part}{:else}<a
					href={part.url}
					target="_blank"
					rel="noopener noreferrer">{part.label}</a
				>{/if}{/each}</p>
	</footer>
</div>

<style>
	.shell {
		min-height: 100vh;
		display: flex;
		flex-direction: column;
	}

	.top {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 1rem;
		padding: 1.5rem 1rem 1.25rem;
	}

	/* At most 7.5rem high and the content width, whatever the file's resolution, so a sharper file only looks sharper.
	   Never scaled up, never stretched: the browser keeps the aspect ratio within both limits. */
	.header-image {
		display: block;
		width: auto;
		height: auto;
		max-width: min(52rem, 100%);
		max-height: clamp(4rem, 16vw, 7.5rem);
	}

	.hero {
		text-align: center;
	}

	/* Between heading and slogan: the same space above and below as the header's other parts. */
	.hero .header-image {
		margin: 1rem auto 0.55rem;
	}

	.title {
		position: relative;
		display: inline-block;
	}

	.site-heading {
		margin: 0;
		/* Long single words (e.g. German compounds) wrap instead of overflowing narrow screens. */
		overflow-wrap: anywhere;
		font-size: clamp(1.6rem, 4vw, 2.4rem);
		line-height: 1.1;
	}

	/* Minecraft's splash text, see the script: its bottom centre sits on the anchor point, turned around it, and it
	   pulses in size twice a second (1.8 - |sin| * 0.1). Its shadow is one font pixel off, like all Minecraft text. */
	.splash {
		position: absolute;
		right: var(--splash-inset);
		bottom: var(--splash-inset);
		z-index: 1;
		margin: 0;
		color: var(--color-splash);
		font-size: var(--splash-size, 1rem);
		line-height: 1;
		white-space: nowrap;
		text-shadow: calc(var(--splash-size, 8px) / 8) calc(var(--splash-size, 8px) / 8) 0 var(--color-splash-shadow);
		transform-origin: 50% 100%;
		translate: 50% 0;
		rotate: -20deg;
		visibility: hidden;
		pointer-events: none;
	}

	.splash.pulse {
		animation: splash-pulse 500ms linear infinite;
	}

	.splash.ready {
		visibility: visible;
	}

	/* scale = (1.8 - |sin(2πt)| * 0.1) / 1.8, sampled every 1/16 of the 500 ms period. */
	@keyframes splash-pulse {
		0%,
		100% {
			scale: 1;
		}
		12.5%,
		87.5% {
			scale: 0.9787;
		}
		25%,
		75% {
			scale: 0.9607;
		}
		37.5%,
		62.5% {
			scale: 0.9487;
		}
		50% {
			scale: 0.9444;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.splash.pulse {
			animation: none;
		}
	}

	.lead {
		margin: 0.45rem 0 0;
		color: var(--color-muted);
	}

	/* The pages in three equal columns (two on narrower screens; a label never changes a button's width), an
	   unfinished last row centred.
	   Custom links (Map, Discord, ...) follow in a row of their own, as wide as their labels. */
	.bar {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 0.5rem;
		width: min(49rem, 100%);
	}

	.toggle {
		display: none;
	}

	.pages {
		display: contents;
	}

	.menu,
	.links {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 0.5rem;
	}

	.menu {
		flex: 0 0 100%;
	}

	.menu .mc-btn {
		flex: 0 0 calc((100% - 1rem) / 3);
	}

	/* The second row (custom links, language and theme as icon buttons) is smaller than the pages above it, and like
	   them its buttons have a fixed width, so a language switch moves nothing. */
	.links .mc-btn,
	.settings :global(.trigger) {
		min-height: 2rem;
		padding: 0.15rem 0.8rem;
		font-size: 0.85rem;
	}

	.links .mc-btn {
		flex: 0 0 var(--link-width, 8rem);
	}

	/* When a long link label wraps onto two lines, every button in the row grows with it. */
	.settings :global(.wrap) {
		display: flex;
	}

	/* Below that, a third of the width is too narrow for the longest labels ("Classement général"). */
	@media (max-width: 819px) {
		.bar {
			width: min(33rem, 100%);
		}

		.menu .mc-btn {
			flex-basis: calc((100% - 0.5rem) / 2);
		}
	}

	/* Language and theme: after the custom links; on phones next to the menu button. */
	.settings {
		display: flex;
		gap: 0.5rem;
	}

	main {
		width: min(52rem, calc(100% - 1.5rem));
		margin: 0 auto;
		padding: 0.85rem 0 3rem;
		flex: 1;
	}

	.credit p {
		margin: 0;
	}

	.credit .footer-links {
		margin-bottom: 1.25rem;
		font-size: 0.9rem;
	}

	.credit {
		padding: 0 1rem 1.5rem;
		color: var(--color-muted);
		font-size: 0.8rem;
		text-align: center;
	}

	.credit a {
		color: var(--color-text);
		border-bottom: 1px dotted currentColor;
	}

	.credit a:hover,
	.credit a:focus-visible {
		color: var(--color-hover);
	}

	/* Phones: one button with the current page opens the pages below it; language and theme sit next to it. */
	@media (max-width: 640px) {
		.bar {
			display: grid;
			grid-template-columns: 1fr auto;
			grid-template-areas:
				'toggle settings'
				'pages pages';
			width: min(24rem, 100%);
		}

		.toggle {
			grid-area: toggle;
			display: inline-flex;
		}

		.settings {
			grid-area: settings;
		}

		.pages {
			grid-area: pages;
			display: none;
			flex-direction: column;
			gap: 0.5rem;
		}

		.bar.open .pages {
			display: flex;
		}

		.menu {
			flex: none;
			flex-direction: column;
		}

		.menu .mc-btn {
			flex: none;
		}

		.links .mc-btn {
			flex: 1 1 0;
			min-width: 0;
			min-height: 2.5rem;
			font-size: 1rem;
		}

		.settings :global(.trigger) {
			min-height: 2.5rem;
			padding: 0;
		}

		/* Plain icon squares here, to leave the menu button as much room as possible. */
		.bar .settings :global(.trigger) {
			width: 2.5rem;
		}

		.settings :global(.chevron) {
			display: none;
		}
	}
</style>
