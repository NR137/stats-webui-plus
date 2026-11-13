<script lang="ts">
	import { branding, pageLabel, pageTitleTag } from '#lib/branding.svelte.ts';
	import { NotFoundError, getCustomPage } from '#lib/api.ts';
	import { renderCustomPage, type RenderedPage } from '#lib/customPage.ts';
	import Loader from '#lib/components/Loader.svelte';
	import { i18n, languageOrder, pageTitle, t } from '#lib/i18n/i18n.svelte.ts';
	import type { CustomPageResponse } from '#lib/types.ts';

	// A page of the server owner's own (config pages): an imprint, a privacy policy, the server rules, ... Its text is
	// a Markdown, HTML or text file per language in the server's config folder; it is loaded again when the visitor
	// switches the language, as there may be a file for that one.

	// The page's id: from the address (/pages/<id>), or the start page's.
	let { id }: { id: string } = $props();

	let data = $state.raw<CustomPageResponse | null>(null);
	let rendered = $state.raw<RenderedPage | null>(null);
	let failed = $state(false);
	let notFound = $state(false);


	$effect(() => {
		const pageId = id;
		const languages = languageOrder();
		let current = true;
		failed = false;
		notFound = false;
		getCustomPage(pageId, languages)
			.then(async (response) => {
				const html = await renderCustomPage(response);
				if (current) {
					data = response;
					rendered = html;
				}
			})
			.catch((error) => {
				if (current) {
					data = null;
					rendered = null;
					failed = true;
					notFound = error instanceof NotFoundError;
				}
			});
		return () => {
			current = false;
		};
	});

	// Shown from the menu entry's label before the text is in, so the title doesn't jump.
	const known = $derived(branding.pages.find((entry) => entry.id === id));
	const label = $derived(data ? pageLabel(data, languageOrder()) : known ? pageLabel(known, languageOrder()) : '');
	const title = $derived(rendered?.title ?? label);
</script>

<svelte:head>
	<title>{pageTitle(title || t('pages.notFoundTitle'))}</title>
</svelte:head>

{#if failed}
	<p class="empty">{t(notFound ? 'pages.notFound' : 'site.loadError')}</p>
{:else if !rendered || !data || data.id !== id}
	<!-- Also while another page's text is still shown (from one custom page to the next). -->
	<Loader />
{:else}
	<svelte:element this={pageTitleTag()} class="page-title">{title}</svelte:element>
	<!-- The text may be in another language than the site, when there is no file for the visitor's. -->
	<article class="custom-page panel" lang={data.language && data.language !== i18n.locale ? data.language : undefined}>
		<!-- Sanitized with DOMPurify in renderCustomPage. -->
		{@html rendered.html}
	</article>
{/if}

<style>
	.empty {
		color: var(--color-muted);
	}

	/* A reading width, like the site's other text pages, centred under a centred title. */
	.page-title {
		margin: 0.67em 0;
		font-size: 2rem;
		text-align: center;
	}

	.custom-page {
		max-width: 52rem;
		margin: 0 auto;
		padding: 0.4rem 1.25rem 1rem;
		line-height: 1.5;
		overflow-wrap: break-word;
	}

	/* The page's own headings below its title: one level down in looks, whatever the file uses. */
	.custom-page :global(h1),
	.custom-page :global(h2) {
		margin: 1.4rem 0 0.6rem;
		font-size: 1.3rem;
	}

	.custom-page :global(h3),
	.custom-page :global(h4),
	.custom-page :global(h5),
	.custom-page :global(h6) {
		margin: 1.2rem 0 0.5rem;
		font-size: 1.1rem;
		font-weight: 400;
	}

	.custom-page :global(p),
	.custom-page :global(ul),
	.custom-page :global(ol),
	.custom-page :global(blockquote),
	.custom-page :global(pre),
	.custom-page :global(table) {
		margin: 0.75rem 0;
	}

	.custom-page :global(a) {
		border-bottom: 1px dotted currentColor;
		color: var(--color-gold);
	}

	.custom-page :global(a:hover),
	.custom-page :global(a:focus-visible) {
		color: var(--color-hover);
	}

	.custom-page :global(li + li) {
		margin-top: 0.25rem;
	}

	.custom-page :global(blockquote) {
		padding: 0.1rem 0 0.1rem 0.9rem;
		border-left: 3px solid var(--color-rule);
		color: var(--color-muted);
	}

	.custom-page :global(pre) {
		padding: 0.6rem 0.8rem;
		overflow-x: auto;
		border: 2px solid var(--color-rule);
		background: var(--slot-bg);
	}

	.custom-page :global(hr) {
		margin: 1.2rem 0;
		border: 0;
		border-top: 2px solid var(--color-rule);
	}

	.custom-page :global(img) {
		max-width: 100%;
		height: auto;
	}

	/* Tables scroll sideways on their own instead of widening the page. */
	.custom-page :global(table) {
		display: block;
		max-width: 100%;
		overflow-x: auto;
		border-collapse: collapse;
	}

	.custom-page :global(th),
	.custom-page :global(td) {
		padding: 0.35rem 0.7rem;
		border: 2px solid var(--color-rule);
		text-align: left;
		vertical-align: top;
	}
</style>
