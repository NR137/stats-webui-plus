<script lang="ts">
	import fallbackFavicon from '$lib/assets/favicon.svg';
	import LanguageSwitcher from '$lib/components/LanguageSwitcher.svelte';
	import StatusBar from '$lib/components/StatusBar.svelte';
	import ThemeSwitcher from '$lib/components/ThemeSwitcher.svelte';
	import { branding, hasSiteHeading, linkLabel, loadBranding, localizedText } from '$lib/branding.svelte';
	import { applyLocaleSettings, i18n, t } from '$lib/i18n/i18n.svelte';
	import { loadNames } from '$lib/mc/names.svelte';
	import '$lib/theme/theme.css';
	import '$lib/theme/theme.svelte';
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

	$effect(() => {
		void tagline;
		const heading = headingElement;
		const splash = splashElement;
		if (!heading || !splash) {
			return;
		}
		const fit = () => {
			const unit = parseFloat(getComputedStyle(heading).lineHeight) / LOGO_HEIGHT;
			const current = parseFloat(getComputedStyle(splash).fontSize);
			// Width in Minecraft font pixels: the font draws them 1/8 em wide.
			const width = (splash.offsetWidth * 8) / current;
			// Never below 10px, where the pixel font stops being readable.
			const size = Math.max(10, (8 * unit * 1.8 * 100) / (width + 32));
			const inset = SPLASH_INSET * unit;
			// The turned text's left end hangs below the heading; that much room is kept free under it.
			const below = Math.max(0, ((width * size) / 8 / 2) * Math.sin(SPLASH_ANGLE) - inset);
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
		{ href: '/compare', label: t('nav.compare') },
		{ href: '/about', label: t('nav.about') }
	]);

	function active(href: string) {
		if (href === '/') {
			return page.url.pathname === '/';
		}
		return page.url.pathname === href || page.url.pathname.startsWith(`${href}/`);
	}
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
		<nav class="menu">
			{#each links as link}
				<a class="mc-btn" class:on={active(link.href)} href={link.href}>{link.label}</a>
			{/each}
			{#each branding.links as link}
				<a
					class="mc-btn"
					href={link.url}
					target={link.newTab ? '_blank' : undefined}
					rel={link.newTab ? 'noopener noreferrer' : undefined}>{linkLabel(link, i18n.locale)}</a
				>
			{/each}
			<LanguageSwitcher />
			<ThemeSwitcher />
		</nav>
	</header>
	<main>
		{@render children()}
	</main>
	<footer class="credit">
		{#each credit as part}{#if typeof part === 'string'}{part}{:else}<a
					href={part.url}
					target="_blank"
					rel="noopener noreferrer">{part.label}</a
				>{/if}{/each}
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

	.menu {
		display: grid;
		grid-template-columns: repeat(2, min(20rem, calc(50vw - 1.2rem)));
		gap: 0.5rem;
		justify-content: center;
	}

	.menu :global(.mc-btn) {
		width: 100%;
	}

	main {
		width: min(52rem, calc(100% - 1.5rem));
		margin: 0 auto;
		padding: 0.85rem 0 3rem;
		flex: 1;
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

	@media (max-width: 520px) {
		.menu {
			grid-template-columns: 1fr;
			width: min(20rem, 100%);
		}
	}
</style>
