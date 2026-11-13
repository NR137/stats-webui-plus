<script lang="ts">
	import AdvancementTile from '#lib/components/AdvancementTile.svelte';
	import { mcFile } from '#lib/mc/files.ts';

	type Item = {
		id: string;
		title?: string;
		description?: string;
		icon: string;
		frame?: string;
		holders?: number;
		hidden?: boolean;
	};

	let {
		items,
		dim,
		obtained,
		showHolders = false,
		when,
		href,
		progress,
		background
	}: {
		items: Item[];
		dim?: (item: Item) => boolean;
		// Without it every tile is obtained.
		obtained?: (item: Item) => boolean;
		showHolders?: boolean;
		when?: (item: Item) => string | undefined;
		href?: (item: Item) => string;
		progress?: (item: Item) => string | undefined;
		// The tab's background texture's name ("stone"); given at all, even empty, the tiles sit on it.
		background?: string;
	} = $props();

	const backdrop = $derived(
		background === undefined ? undefined : `url(${mcFile(`/mc/advancement/backgrounds/${background || 'stone'}.png`)})`
	);
</script>

<div class="tiles" class:backdrop={backdrop !== undefined} style:background-image={backdrop}>
	{#each items as item (item.id)}
		<AdvancementTile
			{item}
			dim={dim?.(item) ?? false}
			obtained={obtained?.(item) ?? true}
			{showHolders}
			when={when?.(item)}
			href={href?.(item)}
			progress={progress?.(item)}
		/>
	{/each}
</div>

<style>
	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
		gap: 0.45rem;
	}

	/* The tab's background as in the game: 16-pixel blocks at 3×. */
	.backdrop {
		padding: 0.6rem;
		border: 2px solid var(--color-border);
		background-repeat: repeat;
		background-size: 48px 48px;
		image-rendering: pixelated;
	}
</style>
