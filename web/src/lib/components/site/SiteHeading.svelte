<script lang="ts">
	import { branding, hasSiteHeading, localizedText } from '#lib/branding.svelte.ts';
	import { languageOrder, t } from '#lib/i18n/i18n.svelte.ts';

	// The top of every page: the header image (config headerImage, headerImagePosition), the site heading in its font
	// (siteHeadingFont) and the slogan in its look (siteHeadingSloganStyle).

	let headerImageFailed = $state(false);

	$effect(() => {
		void branding.headerImage;
		headerImageFailed = false;
	});

	const siteHeading = $derived(localizedText(branding.siteHeading, languageOrder()));
	const slogan = $derived(localizedText(branding.siteHeadingSlogan, languageOrder()));
	const sloganStyle = $derived(branding.siteHeadingSloganStyle);

	// The server owner's font for the site heading (config siteHeadingCustomFont), loaded from its files, best first;
	// if none of them loads, the heading falls back to the default font.
	const CUSTOM_FONT_FAMILY = 'Stats WebUI Plus heading';
	let customFontFailed = $state(false);

	$effect(() => {
		const font = branding.siteHeadingFont === 'custom' ? branding.customFont : null;
		customFontFailed = false;
		if (!font) {
			return;
		}
		const face = new FontFace(
			CUSTOM_FONT_FAMILY,
			font.sources.map((source) => `url("${source.url}") format("${source.format}")`).join(', ')
		);
		let current = true;
		face.load().then(
			(loaded) => {
				if (current) {
					document.fonts.add(loaded);
				}
			},
			() => {
				if (current) {
					customFontFailed = true;
				}
			}
		);
		return () => {
			current = false;
			document.fonts.delete(face);
		};
	});

	const headingFont = $derived(
		branding.siteHeadingFont === 'custom' && customFontFailed ? 'blocky' : branding.siteHeadingFont
	);

	// Without a site heading there is nothing to put the image between, so it goes on top.
	const imagePosition = $derived(hasSiteHeading() ? branding.headerImagePosition : 'top');
</script>

<svelte:head>
	{#if slogan}
		<meta name="description" content={slogan} />
	{/if}
</svelte:head>

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
		{#if slogan && sloganStyle === 'above'}
			<p class="slogan above">{slogan}</p>
		{/if}
		<h1 class="site-heading {headingFont}">{siteHeading}</h1>
		{#if imagePosition === 'between'}
			{@render headerImage()}
		{/if}
		{#if slogan && sloganStyle !== 'above'}
			<p class="slogan {sloganStyle}" class:panel={sloganStyle === 'plate'}>
				{sloganStyle === 'quote' ? t('site.sloganQuote', { text: slogan }) : slogan}
			</p>
		{/if}
	</div>
{/if}
{#if imagePosition === 'bottom'}
	{@render headerImage()}
{/if}

<style>
	/* At most 7.5rem high and the content width, whatever the file's resolution, so a sharper file only looks sharper.
	   Never scaled up, never stretched: the browser keeps the aspect ratio within both limits. */
	.header-image {
		display: block;
		width: auto;
		height: auto;
		max-width: min(52rem, 100%);
		max-height: clamp(4rem, 16vw, 7.5rem);
	}

	/* The whole width, so a slogan between its lines isn't squeezed to the heading's width. */
	.hero {
		width: 100%;
		text-align: center;
	}

	/* Between heading and slogan: the same space above and below as the header's other parts. */
	.hero .header-image {
		margin: 1rem auto 0.55rem;
	}

	/* The server's name in a pixel font of its own, set apart from the Minecraft font of the rest of the page (config
	   siteHeadingFont). Long single words (German compounds) wrap instead of overflowing narrow screens. */
	.site-heading {
		margin: 0;
		overflow-wrap: anywhere;
	}

	/* Blocky pixel capitals (Press Start 2P), wide, so a little smaller. */
	.site-heading.blocky {
		font-family: 'Press Start 2P', var(--font-ui);
		font-size: clamp(1.15rem, 3.6vw, 2.1rem);
		line-height: 1.35;
		text-transform: uppercase;
	}

	/* Pixel blackletter (Jacquard 12), as written; its letters are small for their size. */
	.site-heading.gothic {
		font-family: 'Jacquard 12', var(--font-ui);
		font-size: clamp(2.2rem, 6vw, 3.6rem);
		line-height: 1.05;
	}

	/* The server owner's font (loaded in the script). Its size is set by its capital height (font-size-adjust), so any
	   font comes out about as big as the others, whatever its proportions. */
	.site-heading.custom {
		font-family: 'Stats WebUI Plus heading', var(--font-ui);
		font-size: clamp(1.6rem, 4.4vw, 2.6rem);
		font-size-adjust: cap-height 0.7;
		line-height: 1.2;
	}

	/* The slogan, in one of five looks (config siteHeadingSloganStyle). */
	.slogan {
		margin: 0.5rem 0 0;
	}

	/* Spaced capitals in gold between two short lines. */
	.slogan.line {
		display: flex;
		gap: 0.7rem;
		align-items: center;
		justify-content: center;
		color: var(--color-gold);
		font-size: 0.8rem;
		letter-spacing: 0.25em;
		text-transform: uppercase;
	}

	.slogan.line::before,
	.slogan.line::after {
		flex: 0 1 3rem;
		min-width: 1rem;
		height: 2px;
		background: currentColor;
		opacity: 0.6;
		content: '';
	}

	/* On a plate, like the podium's. */
	.slogan.plate {
		display: inline-block;
		margin-top: 0.6rem;
		padding: 0.25rem 0.8rem;
		color: var(--color-text);
	}

	/* Small and spaced, above the heading. */
	.slogan.above {
		margin: 0 0 0.4rem;
		color: var(--color-muted);
		font-size: 0.75rem;
		letter-spacing: 0.3em;
		text-transform: uppercase;
	}

	/* On a ribbon with notched ends. */
	.slogan.ribbon {
		display: inline-block;
		margin-top: 0.6rem;
		padding: 0.2rem 1.4rem;
		background: var(--color-ribbon);
		color: #ffffff;
		text-shadow: 2px 2px 0 rgb(0 0 0 / 0.35);
		clip-path: polygon(0 0, 100% 0, calc(100% - 0.6rem) 50%, 100% 100%, 0 100%, 0.6rem 50%);
	}

	/* In quotes (the language's own), italic. */
	.slogan.quote {
		color: var(--color-text);
		font-style: italic;
	}
</style>
