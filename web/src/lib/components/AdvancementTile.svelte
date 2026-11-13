<script lang="ts">
	import { advancementDescription, advancementHref, advancementTitle } from '#lib/advancements.ts';
	import AdvancementIcon from '#lib/components/AdvancementIcon.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { t } from '#lib/i18n/i18n.svelte.ts';

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
		// Not obtained by this player: the texts fade, the icon doesn't.
		dim?: boolean;
		// The frame's look.
		obtained?: boolean;
		showHolders?: boolean;
		// The completion time, already formatted.
		when?: string;
		href?: string;
		// E.g. "23 / 56".
		progress?: string;
	} = $props();

	const holders = $derived(item.holders ?? 0);
</script>

<div class="cell">
	<Tooltip text={advancementDescription(item.id, item.description ?? '')} fill>
		<a class="tile slot" class:dim href={href ?? advancementHref(item.id)}>
			<span class="glyph">
				<AdvancementIcon id={item.id} icon={item.icon} frame={item.frame} {obtained} />
			</span>
			<span class="label" class:challenge={item.frame === 'challenge'}>{advancementTitle(item.id, item.title ?? item.id)}</span>
			{#if showHolders}
				<span class="holders" class:nobody={holders === 0}>
					{holders === 1 ? t('advancements.holder') : t('advancements.holders', { count: holders })}
				</span>
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
	/* The tile fills its grid cell, and so does the tooltip around it: the whole cell is the link. */
	.cell {
		width: 100%;
		height: 100%;
	}

	/* See-through in every theme, so the tab's background (stone, netherrack, ...) shows, as in the game's window. */
	.tile {
		--slot-bg: #00000070;
		--slot-hover: #ffffff17;
		min-height: 8.2rem;
	}

	.glyph {
		display: grid;
		place-items: center;
		margin-bottom: 0.15rem;
	}

	/* In the colours of the game's notifications: tasks and goals yellow, challenges pink. */
	.label {
		color: var(--color-advancement);
		font-size: 0.875rem;
		line-height: 1.25;
	}

	.label.challenge {
		color: var(--color-challenge);
	}

	.holders,
	.when,
	.progress {
		color: var(--color-text);
		font-size: 0.75rem;
		line-height: 1.25;
	}

	.when,
	.progress {
		font-variant-numeric: tabular-nums;
	}

	.holders.nobody {
		color: var(--color-muted);
		opacity: 0.55;
	}

	/* Not obtained: the title goes grey instead of fading, which was hard to read; the small lines fade. */
	.dim .label {
		color: var(--color-muted);
	}

	.dim .holders,
	.dim .when {
		opacity: 0.45;
	}

	/* The tile stays dark like the game's advancement screen, so the titles keep the game's yellow and pink, which
	   would be unreadable on a light page. See-through like the dark themes' slots. */
	:global(html[data-mode='light']) .tile {
		--color-advancement: #ffff00;
		--color-challenge: #ff88ff;
		--color-muted: #a0a0a0;
		--color-text: #e0e0e0;
		--color-hover: #ffffa0;
		--slot-hover: #ffffff14;
		--slot-edge: none;
		--text-shadow: 2px 2px 0 #3f3f3f;
		background: #2b1e14b3;
		text-shadow: var(--text-shadow);
	}
</style>
