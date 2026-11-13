<script lang="ts" generics="Entry extends PlayerRef">
	import { mcFile } from '#lib/mc/files.ts';
	import { playerPath } from '#lib/players.ts';
	import PlayerAvatar from '#lib/components/PlayerAvatar.svelte';
	import RankingScore from '#lib/components/RankingScore.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import type { Emote } from '#lib/skins/emotes.ts';
	import type { PlayerRef } from '#lib/types.ts';

	let {
		entries,
		display,
		coin = false,
		hint = '',
		href = (entry: Entry) => playerPath(entry.name, entry.uuid)
	}: {
		entries: Entry[];
		// A place's value as text: a board's value in its unit, or when the player did an advancement.
		display: (entry: Entry) => string;
		coin?: boolean;
		hint?: string;
		// Where a place leads: the player's page, or e.g. their page for the advancement the podium is about.
		href?: (entry: Entry) => string;
	} = $props();

	type Place = { rank: number; medal: 'gold' | 'silver' | 'bronze'; block: string; blocks: number; emote: Emote };

	// Left to right as on a real podium.
	const places: Place[] = [
		{ rank: 2, medal: 'silver', block: 'iron_block', blocks: 2, emote: 'dab' },
		{ rank: 1, medal: 'gold', block: 'gold_block', blocks: 3, emote: 'cheer' },
		{ rank: 3, medal: 'bronze', block: 'copper_block', blocks: 1, emote: 'wave' }
	];

	// Phones get the flat heads: three 3D views are too small there to be worth what they cost.
	let phone = $state(false);

	$effect(() => {
		const query = window.matchMedia('(max-width: 640px)');
		const apply = () => {
			phone = query.matches;
		};
		apply();
		query.addEventListener('change', apply);
		return () => query.removeEventListener('change', apply);
	});
</script>

<section class="podium" class:phone aria-label={t('ranking.podium')}>
	<div class="row">
		{#each places as place (place.rank)}
			{@const entry = entries[place.rank - 1]}
			<svelte:element
				this={entry ? 'a' : 'div'}
				class="place {place.medal}"
				class:empty={!entry}
				href={entry ? href(entry) : undefined}
				style:--texture="url({mcFile(`/mc/block/${place.block}.png`)})"
				style:--height={place.blocks}
			>
				<div class="figure">
					{#if entry}
						{#if phone}
							<PlayerAvatar uuid={entry.uuid} variant="head" scale={16} />
						{:else}
							<PlayerAvatar uuid={entry.uuid} variant="body" presentation="podium" emote={place.emote} controls={false} />
						{/if}
					{/if}
				</div>
				<div class="pillar">
					{#if !phone}
						{@render plate(entry)}
					{/if}
				</div>
				{#if phone}
					{@render plate(entry)}
				{/if}
			</svelte:element>
		{/each}
	</div>
</section>

{#snippet plate(entry: Entry | undefined)}
	<div class="plate">
		{#if entry}
			<p class="name">{entry.name}</p>
			<p class="value">
				<Tooltip text={hint}>
					{#if coin}
						<RankingScore value={display(entry)} />
					{:else}
						<span class="number">{display(entry)}</span>
					{/if}
				</Tooltip>
			</p>
		{:else}
			<p class="name">{t('ranking.nobody')}</p>
		{/if}
	</div>
{/snippet}

<style>
	/* The podium is 12 blocks wide, so each pillar is four whole blocks. Blocks are whole pixels, so every edge lands on
	   a pixel the same way. Players are drawn larger than in the game (1.8 blocks), so they read well. */
	/* At most 60rem wide: with the wide content the blocks, and the players on them, would get very big. */
	.podium {
		max-width: 60rem;
		margin: 0 auto 1.6rem;
		container-type: inline-size;
	}

	.row {
		--block: round(down, 100cqw / 12, 1px);
		--top: round(var(--block) / 4, 1px);
		display: grid;
		grid-template-columns: repeat(3, calc(var(--block) * 4));
		justify-content: center;
		align-items: end;
	}

	.place {
		display: flex;
		flex-direction: column;
		min-width: 0;
		color: inherit;
		text-decoration: none;
	}

	/* The canvas shows 46 units for the player's 32: 7 above for raised arms, so 7 below too (the camera looks at the
	   player's middle). The feet stand in the middle of the block's top. */
	.figure {
		--figure: calc(var(--block) * 2.6 / 32);
		position: relative;
		z-index: 1;
		height: calc(var(--figure) * 46);
		margin-bottom: round(var(--top) / -2 - var(--figure) * 7, 1px);
		pointer-events: none;
	}

	.pillar {
		position: relative;
		height: calc(var(--block) * var(--height));
		margin-top: var(--top);
		box-shadow: inset 2px 0 rgb(0 0 0 / 0.25);
		/* The front face a little darker than the top, as the game shades them; the medal colour where the game's
		   files are missing. */
		background:
			linear-gradient(rgb(0 0 0 / 0.18), rgb(0 0 0 / 0.18)),
			var(--texture) left bottom / var(--block) var(--block),
			var(--medal);
		image-rendering: pixelated;
	}

	.pillar::before {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 100%;
		height: var(--top);
		background:
			linear-gradient(rgb(255 255 255 / 0.08), rgb(255 255 255 / 0.08)),
			var(--texture) left bottom / var(--block) var(--top),
			var(--medal);
		image-rendering: pixelated;
		content: '';
	}

	.gold {
		--medal: var(--color-plate-gold);
		--edge: var(--color-gold);
	}

	.silver {
		--medal: var(--color-plate-silver);
		--edge: var(--color-silver);
	}

	.bronze {
		--medal: var(--color-plate-bronze);
		--edge: var(--color-bronze);
	}

	/* Within the top block, so it fits on the one-block pillar too. */
	.plate {
		position: absolute;
		top: 0.3rem;
		left: 0.5rem;
		right: 0.5rem;
		box-sizing: border-box;
		max-height: calc(var(--block) - 0.6rem);
		padding: 0.2rem 0.5rem;
		border: 2px solid var(--edge);
		box-shadow: inset 0 0 0 2px var(--button-shadow);
		background: var(--color-card);
		text-align: center;
	}

	.name {
		margin: 0;
		overflow: hidden;
		font-size: 0.95rem;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.value {
		display: flex;
		justify-content: center;
		margin: 0.1rem 0 0;
	}

	.number {
		color: var(--color-gold);
		font-variant-numeric: tabular-nums;
	}

	a.place:hover .name,
	a.place:focus-visible .name {
		color: var(--color-hover);
	}

	.empty .name {
		color: var(--color-muted);
	}

	.empty .pillar {
		opacity: 0.6;
	}

	/* Phones: the same pillars with the players' heads on them, drawn two blocks wide so they read on a small screen;
	   the plates below, as the lower pillars are too small for them. */
	.phone .figure {
		display: flex;
		justify-content: center;
		align-items: flex-end;
		height: auto;
		margin-bottom: round(var(--top) / -2, 1px);
	}

	.phone .figure :global(.head .stage),
	.phone .figure :global(.head canvas) {
		width: calc(var(--block) * 2);
		height: calc(var(--block) * 2);
	}

	.phone .plate {
		position: static;
		max-height: none;
		margin: 0.45rem 0.15rem 0;
		padding: 0.15rem 0.3rem;
	}

	.phone .name,
	.phone .value {
		font-size: 0.75rem;
	}
</style>
