<script lang="ts">
	import {
		advancementDescription,
		advancementHref,
		advancementTitle
	} from '$lib/advancements';
	import AdvancementIcon from '$lib/components/AdvancementIcon.svelte';
	import Tooltip from '$lib/components/Tooltip.svelte';
	import { t } from '$lib/i18n/i18n.svelte';

	let {
		item,
		dim = false,
		obtained = true,
		showHolders = false,
		when
	}: {
		item: {
			id: string;
			title?: string;
			description?: string;
			icon: string;
			frame?: string;
			holders?: number;
		};
		// Fades the texts (not the icon), e.g. not obtained by this player.
		dim?: boolean;
		obtained?: boolean;
		showHolders?: boolean;
		// When the player completed it, already formatted.
		when?: string;
	} = $props();
</script>

<div class="cell">
	<Tooltip text={advancementDescription(item.id, item.description ?? '')}>
		<a class="tile slot" class:dim href={advancementHref(item.id)}>
			<span class="glyph">
				<AdvancementIcon id={item.id} icon={item.icon} frame={item.frame} {obtained} />
			</span>
			<!-- Coloured like the game's chat announces them: challenges purple, tasks and goals green. -->
			<span class="label" class:challenge={item.frame === 'challenge'}
				>{advancementTitle(item.id, item.title)}</span
			>
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

	.tile.dim .label,
	.tile.dim .holders,
	.tile.dim .when {
		opacity: 0.45;
	}

	.glyph {
		display: grid;
		place-items: center;
		margin-bottom: 0.15rem;
	}

	.label,
	.holders,
	.when {
		color: var(--color-muted);
		font-size: 0.75rem;
		line-height: 1.25;
	}

	.label {
		font-size: 0.875rem;
	}

	/* The title carries the colour; date and holders stay neutral below it. */
	.holders,
	.when {
		color: var(--color-text);
	}

	.label {
		color: var(--color-accent);
	}

	.label.challenge {
		color: var(--color-challenge);
	}

	/* Nobody has it yet: fainter than the label. */
	.holders.none {
		color: var(--color-muted);
		opacity: 0.55;
	}

	.when {
		font-variant-numeric: tabular-nums;
	}
</style>
