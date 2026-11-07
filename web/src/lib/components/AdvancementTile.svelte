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
		when,
		href,
		progress
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
		href?: string;
		progress?: string;
	} = $props();
</script>

<div class="cell">
	<Tooltip text={advancementDescription(item.id, item.description ?? '')}>
		<a class="tile slot" class:dim href={href ?? advancementHref(item.id)}>
			<span class="glyph">
				<AdvancementIcon id={item.id} icon={item.icon} frame={item.frame} {obtained} />
			</span>
			<!-- Coloured like the game's notifications: challenges pink, tasks and goals yellow. -->
			<span class="label" class:challenge={item.frame === 'challenge'}
				>{advancementTitle(item.id, item.title)}</span
			>
			{#if showHolders}
				<span class="holders" class:none={!item.holders}
					>{item.holders === 1
						? t('advancements.holder')
						: t('advancements.holders', { count: item.holders ?? 0 })}</span
				>
			{/if}
			{#if when}
				<span class="when">{when}</span>
			{:else if progress}
				<span class="progress">{progress}</span>
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

	/* Light theme: the tile stays dark like the game's advancement screen, so the titles keep the game's yellow and
	   pink (on the light sand they were unreadable, and a darker yellow is no longer yellow). */
	:global(html[data-theme='light']) .tile {
		--color-advancement: #ffff00;
		--color-challenge: #ff88ff;
		--color-muted: #a0a0a0;
		--color-text: #e0e0e0;
		--color-hover: #ffffa0;
		--slot-hover: #ffffff14;
		--slot-edge: none;
		--text-shadow: 2px 2px 0 #3f3f3f;
		/* Like the dark theme's slots: see-through, so the sand shows a little. */
		background: #2b1e14b3;
		text-shadow: var(--text-shadow);
	}

	/* Not obtained (by this player; on the overview: hidden): the title grey instead of coloured. Not faded as well,
	   which made it hard to read; the silver frame says the rest. */
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
	.when,
	.progress {
		color: var(--color-muted);
		font-size: 0.75rem;
		line-height: 1.25;
	}

	.label {
		font-size: 0.875rem;
	}

	/* The title carries the colour; date and holders stay neutral below it. */
	.holders,
	.when,
	.progress {
		color: var(--color-text);
	}

	.label {
		color: var(--color-advancement);
	}

	.label.challenge {
		color: var(--color-challenge);
	}

	.tile.dim .label {
		color: var(--color-muted);
	}

	/* Nobody has it yet: fainter than the label. */
	.holders.none {
		color: var(--color-muted);
		opacity: 0.55;
	}

	.when,
	.progress {
		font-variant-numeric: tabular-nums;
	}
</style>
