<script lang="ts">
	import { branding, pageLabel } from '#lib/branding.svelte.ts';
	import LinkedText from '#lib/components/LinkedText.svelte';
	import { languageOrder, t } from '#lib/i18n/i18n.svelte.ts';
	import { isActive } from '#lib/menu.ts';
	import { page } from '$app/state';

	// Under every page: How we rank, the owner's footer pages (imprint, privacy policy, ...) and Licenses; the credit,
	// the disclaimer the Minecraft Usage Guidelines ask for, and whose the game's things and the font are.

	const footerPages = $derived(
		branding.pages.filter((entry) => entry.place === 'footer' && entry.id !== branding.startPage)
	);

	// The credit line's projects; the font in the line about the game's things.
	const footerLinks = {
		plus: { label: 'Stats WebUI Plus', url: 'https://github.com/NR137/stats-webui-plus' },
		original: { label: 'mc-stats-webui', url: 'https://github.com/borrageiros/mc-stats-webui' },
		font: { label: "mac's Minecraft", url: 'https://github.com/macimas-fonts/macsMinecraft' }
	};

	const current = (href: string) => (isActive(href, page.url.pathname) ? 'page' : undefined);
</script>

<footer class="credit">
	<p class="footer-links">
		<a href="/about" aria-current={current('/about')}>{t('menu.about')}</a>
		{#each footerPages as entry (entry.id)}
			<a href="/pages/{entry.id}" aria-current={current(`/pages/${entry.id}`)}>{pageLabel(entry, languageOrder())}</a>
		{/each}
		<a href="/licenses" aria-current={current('/licenses')}>{t('menu.licenses')}</a>
	</p>
	<p><LinkedText text={t('footer.credit')} links={footerLinks} /></p>
	<p class="disclaimer">{t('footer.disclaimer')}</p>
	<p class="assets"><LinkedText text={t('footer.assets')} links={footerLinks} /></p>
</footer>

<style>
	.credit {
		padding: 0 1rem 1.5rem;
		color: var(--color-muted);
		font-size: 0.8rem;
		text-align: center;
	}

	.credit p {
		margin: 0;
	}

	/* The disclaimer and whose the game's things and the font are: below the credit, a little apart from it. */
	.credit .disclaimer {
		margin-top: 0.6rem;
	}

	.credit .assets {
		margin-top: 0.2rem;
	}

	/* How we rank and the server owner's footer pages (imprint, privacy policy, ...), wrapping on phones. */
	.credit .footer-links {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem 1.5rem;
		justify-content: center;
		margin-bottom: 1.25rem;
		font-size: 0.9rem;
	}

	/* :global: most footer links come out of LinkedText. */
	.credit :global(a) {
		border-bottom: 1px dotted currentColor;
		color: var(--color-text);
	}

	.credit :global(a:hover),
	.credit :global(a:focus-visible) {
		color: var(--color-hover);
	}
</style>
