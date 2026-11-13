<script lang="ts">
	import type { Snippet } from 'svelte';

	let { children }: { children: Snippet } = $props();
</script>

<!-- A plain block (no margin, padding or border), so the margins of what's inside behave as if it weren't there. -->
<div class="text-page">{@render children()}</div>

<style>
	/* The look shared by the pages that are mostly text. :where() keeps the wrapper out of the specificity, so a page's
	   own scoped rules (p.muted, .disclaimer, ...) still win over these, as when these rules were in the page itself. */
	:where(.text-page) :global(.page-title) {
		margin: 0.67em 0;
		/* Same look whether this is the page's h1 or an h2 below the site heading. */
		font-size: 2rem;
		text-align: center;
	}

	:where(.text-page) :global(p) {
		max-width: 42rem;
		margin-left: auto;
		margin-right: auto;
		color: var(--color-text);
		line-height: 1.45;
	}

	:where(.text-page) :global(.panel) {
		max-width: 42rem;
		margin: 1.1rem auto 0;
		padding: 0.85rem 1rem;
	}

	:where(.text-page) :global(.panel p) {
		max-width: none;
		margin: 0;
	}

	:where(.text-page) :global(.panel p + p) {
		margin-top: 0.6rem;
	}

	:where(.text-page) :global(h2:not(.page-title)) {
		margin: 0 0 0.6rem;
		color: var(--color-gold);
		font-size: 1rem;
	}
</style>
