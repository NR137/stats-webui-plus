<script lang="ts">
	import {
		advancementDescription,
		advancementHref,
		advancementTitle
	} from '$lib/advancements';
	import McItem from '$lib/components/McItem.svelte';
	import Tooltip from '$lib/components/Tooltip.svelte';
	import { t } from '$lib/i18n/i18n.svelte';

	let {
		item,
		dim = false,
		showHolders = false,
		when
	}: {
		item: {
			id: string;
			title?: string;
			description?: string;
			icon: string;
			holders?: number;
		};
		dim?: boolean;
		showHolders?: boolean;
		// When the player completed it, already formatted.
		when?: string;
	} = $props();
</script>

<div class="cell">
	<Tooltip text={advancementDescription(item.id, item.description ?? '')}>
		<a class="tile slot" class:dim href={advancementHref(item.id)}>
			<span class="glyph">
				<McItem id={item.icon} />
			</span>
			<span class="label">{advancementTitle(item.id, item.title)}</span>
			{#if showHolders}
				<span class="holders" class:none={!item.holders}
					>{t('advancements.holders', { count: item.holders ?? 0 })}</span
				>
			{/if}
			{#if when}
				<span class="when">{when}</span>
			{/if}
		</a>
	</Tooltip>
</div>

<style>
	.cell,
	.cell :global(.wrap),
	.cell :global(.hit) {
		width: 100%;
		height: 100%;
	}

	.cell :global(.hit) {
		cursor: pointer;
	}

	.tile {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.28rem;
		width: 100%;
		height: 100%;
		padding: 0.7rem 0.45rem 0.55rem;
		text-align: center;
		min-height: 8.2rem;
	}

	.tile.dim {
		opacity: 0.45;
	}

	.glyph {
		display: grid;
		place-items: center;
		width: 2.6rem;
		height: 2.6rem;
		margin-bottom: 0.15rem;
	}

	.label,
	.holders,
	.when {
		color: var(--color-muted);
		font-size: 0.72rem;
		line-height: 1.25;
	}

	.holders,
	.when {
		color: var(--color-accent);
	}

	/* Nobody has it yet: fainter than the label, the accent colour is for advancements someone finished. */
	.holders.none {
		color: var(--color-muted);
		opacity: 0.55;
	}

	.when {
		font-size: 0.66rem;
		font-variant-numeric: tabular-nums;
	}
</style>
