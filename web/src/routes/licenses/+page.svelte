<script lang="ts">
	import { onMount } from 'svelte';
	import { branding, localizedText, pageTitleTag } from '#lib/branding.svelte.ts';
	import {
		BUNDLED_FONTS,
		fontPackagesInUse,
		isBundledFont,
		packagesShown,
		type BundledFont
	} from '#lib/bundledFonts.ts';
	import LinkedText from '#lib/components/LinkedText.svelte';
	import TextPage from '#lib/components/TextPage.svelte';
	import { languageOrder, pageTitle, t } from '#lib/i18n/i18n.svelte.ts';
	import type { Licenses } from '../../../licenses';

	// Made by the build from the browser bundle (web/licenses.ts), so the list is always what this website contains.
	let licenses = $state<Licenses | null>(null);
	let failed = $state(false);

	onMount(async () => {
		try {
			const response = await fetch('/licenses.json');
			if (!response.ok) {
				throw new Error(`HTTP ${response.status}`);
			}
			licenses = await response.json();
		} catch {
			failed = true;
		}
	});

	const links = $derived({
		// The guidelines in the visitor's language (minecraft.net has en-us, de-de, fr-fr, es-es).
		guidelines: { label: t('licenses.guidelines'), url: t('licenses.guidelinesUrl') },
		plus: { label: 'Stats WebUI Plus', url: 'https://github.com/NR137/stats-webui-plus' },
		font: { label: "mac's Minecraft", url: 'https://github.com/macimas-fonts/macsMinecraft' },
		file: { label: 'THIRD-PARTY-NOTICES.txt', url: '/THIRD-PARTY-NOTICES.txt' }
	});

	const siteFont = $derived(branding.siteHeadingFont);
	const titleFont = $derived(branding.pageTitleFont === 'minecraft' ? null : branding.pageTitleFont);
	// Only the fonts this server's settings use are named, and only their packages listed (see bundledFonts).
	const packages = $derived(
		packagesShown(licenses?.packages ?? [], fontPackagesInUse(branding.siteHeadingFont, branding.pageTitleFont))
	);
	// The custom font's license text, read when its "Show license text" is opened.
	let customLicenseText = $state<string | null>(null);

	async function readCustomLicense(event: Event) {
		const url = branding.customFont?.licenseText;
		if (!(event.currentTarget as HTMLDetailsElement).open || customLicenseText !== null || !url) {
			return;
		}
		try {
			const response = await fetch(url);
			customLicenseText = response.ok ? await response.text() : t('licenses.error');
		} catch {
			customLicenseText = t('licenses.error');
		}
	}

	const customLicense = $derived(
		siteFont === 'custom' && branding.customFont ? localizedText(branding.customFont.license, languageOrder()) : ''
	);

	/** A sentence about a bundled font, with its link. */
	function fontSentence(key: 'licenses.fontBoth' | 'licenses.fontSite' | 'licenses.fontTitles', font: BundledFont) {
		const { author, link } = BUNDLED_FONTS[font];
		return { text: t(key, { author }), links: { ...links, font: link } };
	}

	const fontSentences = $derived.by(() => {
		if (siteFont === titleFont && isBundledFont(siteFont)) {
			return [fontSentence('licenses.fontBoth', siteFont)];
		}
		return [
			...(isBundledFont(siteFont) ? [fontSentence('licenses.fontSite', siteFont)] : []),
			...(titleFont ? [fontSentence('licenses.fontTitles', titleFont)] : [])
		];
	});
</script>

<svelte:head>
	<title>{pageTitle(t('menu.licenses'))}</title>
</svelte:head>

<TextPage>
	<svelte:element this={pageTitleTag()} class="page-title">{t('licenses.heading')}</svelte:element>
	<p>{t('licenses.lead')}</p>

	<section class="panel" id="minecraft">
		<h2>{t('licenses.minecraftHeading')}</h2>
		<p class="disclaimer">{t('footer.disclaimer')}</p>
		<p><LinkedText text={t('licenses.minecraft')} {links} /></p>
		<p>{t('licenses.download')}</p>
		<p>{t('licenses.skins')}</p>
	</section>

	<section class="panel" id="project">
		<h2>{t('licenses.projectHeading')}</h2>
		<p><LinkedText text={t('licenses.project')} {links} /></p>
		{#if licenses}
			<details>
				<summary>{t('licenses.showText')}</summary>
				<pre>{licenses.project.text}</pre>
			</details>
		{/if}
	</section>

	<section class="panel" id="font">
		<h2>{t('licenses.fontHeading')}</h2>
		<p><LinkedText text={t('licenses.font')} {links} /></p>
		{#if licenses}
			<details>
				<summary>{t('licenses.showText')}</summary>
				<pre>{licenses.font.text}</pre>
			</details>
		{/if}
		{#if siteFont === 'custom' && branding.customFont}
			<p>{t('licenses.fontCustom', { name: branding.customFont.name || '–' })}</p>
			{#if customLicense}
				<p class="custom-license">{customLicense}</p>
			{/if}
			{#if branding.customFont.licenseText}
				<details ontoggle={readCustomLicense}>
					<summary>{t('licenses.showText')}</summary>
					<pre>{customLicenseText ?? t('licenses.loading')}</pre>
				</details>
			{/if}
		{/if}
		{#each fontSentences as sentence (sentence.text)}
			<p><LinkedText text={sentence.text} links={sentence.links} /></p>
		{/each}
	</section>

	<section class="panel" id="packages">
		<h2>{t('licenses.packagesHeading')}</h2>
		<p><LinkedText text={t('licenses.packages')} {links} /></p>
		{#if licenses}
			<ul class="packages">
				{#each packages as entry (entry.name)}
					<li>
						<details>
							<summary>
								<span class="what"
									><span class="name">{entry.name}</span> <span class="version">{entry.version}</span></span
								>
								<span class="license">{entry.license}</span>
							</summary>
							{#if entry.url}
								<p class="url">
									<a href={entry.url} target="_blank" rel="noopener noreferrer">{entry.url}</a>
								</p>
							{/if}
							<pre>{entry.text}</pre>
						</details>
					</li>
				{/each}
			</ul>
		{:else if failed}
			<p class="muted">{t('licenses.error')}</p>
		{:else}
			<p class="muted">{t('licenses.loading')}</p>
		{/if}
	</section>
</TextPage>

<style>
	.panel p + details,
	.panel details + p,
	.panel p + .packages {
		margin-top: 0.6rem;
	}

	/* The server owner's words about their font, with their line breaks. */
	.custom-license {
		white-space: pre-line;
	}

	.disclaimer {
		color: var(--color-fg);
	}

	.muted {
		color: var(--color-muted);
	}

	/* :global: most links come out of LinkedText. */
	.panel :global(a) {
		border-bottom: 1px dotted currentColor;
		color: var(--color-fg);
	}

	.panel :global(a:hover),
	.panel :global(a:focus-visible) {
		color: var(--color-hover);
	}

	summary {
		color: var(--color-text);
		cursor: pointer;
	}

	summary:hover {
		color: var(--color-hover);
	}

	.packages {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.packages li + li {
		margin-top: 0.45rem;
	}

	/* Marker, name and version (wrapping on phones), license on the right. The marker's column is fixed and the marker
	   centred in it: ▶ and ▼ differ in width, and the name moved when a package was opened. */
	.packages summary {
		display: grid;
		grid-template-columns: 0.8rem 1fr auto;
		gap: 0.6rem;
		align-items: baseline;
	}

	.packages summary::before {
		color: var(--color-gold);
		font-size: 0.8em;
		text-align: center;
		content: '▶';
	}

	.packages details[open] summary::before {
		content: '▼';
	}

	.packages summary::-webkit-details-marker {
		display: none;
	}

	.packages summary {
		list-style: none;
	}

	.name {
		color: var(--color-fg);
		overflow-wrap: anywhere;
	}

	.version {
		color: var(--color-muted);
	}

	.license {
		color: var(--color-gold);
	}

	/* A little apart from the package's line above it. */
	.url {
		margin-top: 0.4rem;
		font-size: 0.85rem;
		overflow-wrap: anywhere;
	}

	/* License texts as written (line breaks matter), in a scroll box so a long one doesn't stretch the page. */
	pre {
		max-height: 18rem;
		margin: 0.5rem 0 0;
		padding: 0.6rem 0.7rem;
		overflow: auto;
		border: 2px solid var(--color-border);
		background: var(--color-tooltip);
		color: var(--color-text);
		font-family: ui-monospace, 'Cascadia Mono', 'DejaVu Sans Mono', monospace;
		font-size: 0.75rem;
		line-height: 1.4;
		white-space: pre-wrap;
		overflow-wrap: anywhere;
	}
</style>
