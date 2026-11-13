<script lang="ts">
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { boardHint, t } from '#lib/i18n/i18n.svelte.ts';
	import { mcFile } from '#lib/mc/files.ts';

	// The ranking level as the game shows experience: a big green number over the experience bar (full from 100 on),
	// with the rank below. On the player page and the compare page.
	let {
		level,
		display,
		rank = '',
		place = 0,
		align = 'end'
	}: {
		level: number;
		display: string;
		rank?: string;
		// The rank as a number: the first three get the podium's gold, silver and bronze.
		place?: number;
		align?: 'start' | 'center' | 'end';
	} = $props();
</script>

<div class="level {align}">
	<Tooltip text={boardHint('legends')} align={align === 'start' ? 'start' : 'end'} focusable>
		<span class="number">
			<span class="label">{t('players.columns.score')}</span>
			{display}
		</span>
	</Tooltip>
	<span
		class="xp-bar"
		role="img"
		aria-label="{t('players.columns.score')} {display}"
		style:--fill="{Math.min(100, Math.max(0, level))}%"
		style:--background="url({mcFile('/mc/gui/experience_bar_background.png')})"
		style:--progress="url({mcFile('/mc/gui/experience_bar_progress.png')})"
	></span>
	{#if rank}
		<span class="rank" class:gold={place === 1} class:silver={place === 2} class:bronze={place === 3}>
			{#if place >= 1 && place <= 3}
				<!-- The podium's metals: gold, iron and copper. -->
				<ItemIcon id={['minecraft:gold_ingot', 'minecraft:iron_ingot', 'minecraft:copper_ingot'][place - 1]} compact />
			{/if}
			{rank}
		</span>
	{/if}
</div>

<style>
	.level {
		display: grid;
		gap: 0.3rem;
	}

	.start {
		justify-items: start;
	}

	.center {
		justify-items: center;
	}

	.end {
		justify-items: end;
	}

	/* A big number in the experience green, with the dark outline the game draws around it. Its size can be set from
	   outside (--level-size), so the player page keeps it beside the name. */
	.number {
		display: inline-flex;
		gap: 0.5rem;
		align-items: baseline;
		color: var(--color-xp);
		font-size: var(--level-size, 2.4rem);
		line-height: 1;
		font-variant-numeric: tabular-nums;
		text-shadow: var(--xp-outline);
	}

	.label {
		color: var(--color-muted);
		font-size: 0.9rem;
		text-shadow: none;
	}

	/* The game's experience bar (182 x 5 pixels) at twice its size, filled up to the level (full from 100 on). */
	.xp-bar {
		position: relative;
		display: block;
		width: min(364px, 100%);
		aspect-ratio: 182 / 5;
		background: var(--background) 0 0 / 100% 100%;
		image-rendering: pixelated;
	}

	.xp-bar::after {
		position: absolute;
		inset: 0;
		background: var(--progress) 0 0 / 100% 100%;
		clip-path: inset(0 calc(100% - var(--fill)) 0 0);
		image-rendering: pixelated;
		content: '';
	}

	.rank {
		display: inline-flex;
		gap: 0.35rem;
		align-items: center;
		color: var(--color-muted);
	}

	.rank.gold {
		color: var(--color-gold);
	}

	.rank.silver {
		color: var(--color-silver);
	}

	.rank.bronze {
		color: var(--color-bronze);
	}
</style>
