<script lang="ts">
	import AdvancementTile from '$lib/components/AdvancementTile.svelte';

	let {
		items,
		dim,
		obtained,
		showHolders = false,
		when,
		href,
		progress
	}: {
		items: Array<{
			id: string;
			title?: string;
			description?: string;
			icon: string;
			frame?: string;
			holders?: number;
			hidden?: boolean;
		}>;
		dim?: (item: { id: string; hidden?: boolean }) => boolean;
		// Gold frame; without this function every tile is obtained (e.g. what one player has and the other not).
		obtained?: (item: { id: string; holders?: number }) => boolean;
		showHolders?: boolean;
		when?: (item: { id: string }) => string | undefined;
		// Where a tile leads; the advancement's own page by default.
		href?: (item: { id: string }) => string;
		// E.g. "23 / 56" for an unfinished advancement with several parts.
		progress?: (item: { id: string }) => string | undefined;
	} = $props();
</script>

<div class="tiles">
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
</style>
