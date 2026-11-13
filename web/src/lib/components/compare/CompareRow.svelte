<script lang="ts">
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import RankingScore from '#lib/components/RankingScore.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import { fillPercent, toneColor, winner, type CompareRowData } from './compare';

	let { row }: { row: CompareRowData } = $props();

	let open = $state(false);

	const won = $derived(winner(row.a, row.b, row.lowerWins));
	const color = $derived(toneColor(row.tone));
	const hint = $derived(row.hint ?? '');
	const hasParts = $derived((row.parts?.length ?? 0) > 0);
</script>

{#snippet value(text: string)}
	{#if row.coin}<RankingScore value={text} />{:else}{text}{/if}
{/snippet}

<div class="stat slot" class:win-a={won === 'a'} class:win-b={won === 'b'}>
	<div class="label-line">
		{#if hasParts}
			<button
				type="button"
				class="toggle"
				class:open
				aria-expanded={open}
				aria-label="{t('compare.skillParts')}: {row.label}"
				onclick={() => (open = !open)}><span class="arrow" aria-hidden="true">▶</span></button
			>
		{/if}
		{#if row.href}
			<Tooltip text={hint} align="start">
				<a class="link" href={row.href}><ItemIcon id={row.icon} /><span>{row.label}</span></a>
			</Tooltip>
		{:else}
			<ItemIcon id={row.icon} />
			<Tooltip text={hint} align="start" focusable={Boolean(hint)}><span>{row.label}</span></Tooltip>
		{/if}
	</div>
	<div class="values">
		<!-- The tooltip is the grid's cell, the value inside it, as everywhere numbers carry a hint. -->
		<Tooltip text={hint} align="end">
			<span class="value a" class:win={won === 'a'} class:lose={won === 'b'}>{@render value(row.aText)}</span>
		</Tooltip>
		<!-- The bars grow towards each other: A's fills from the right, B's from the left. -->
		<span class="bars" aria-hidden="true">
			<span class="bar a"
				><span
					class="fill"
					style:width="{fillPercent(row.a, row.b, row.capped)}%"
					style:background={color}
				></span></span
			>
			<span class="bar b"
				><span
					class="fill"
					style:width="{fillPercent(row.b, row.a, row.capped)}%"
					style:background={color}
				></span></span
			>
		</span>
		<Tooltip text={hint} align="start">
			<span class="value b" class:win={won === 'b'} class:lose={won === 'a'}>{@render value(row.bText)}</span>
		</Tooltip>
	</div>
	{#if hasParts && open}
		<div class="parts">
			{#each row.parts ?? [] as part (part.id)}
				<span class="part-a" class:win={part.aValue > part.bValue}>{part.a}</span>
				<span class="part-name"
					>{part.label} <span class="target">{t('compare.partTarget', { target: part.target })}</span></span
				>
				<span class="part-b" class:win={part.bValue > part.aValue}>{part.b}</span>
			{/each}
		</div>
	{/if}
</div>

<style>
	.stat {
		display: grid;
		gap: 0.45rem;
		padding: 0.55rem 0.7rem;
	}

	.label-line {
		display: flex;
		gap: 0.55rem;
		align-items: center;
	}

	/* Only the arrow shows: none of the button's own look. */
	.toggle,
	.toggle:hover,
	.toggle:focus-visible {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 1.4rem;
		height: 1.4rem;
		min-height: 0;
		margin-right: -0.2rem;
		padding: 0;
		border: 0;
		box-shadow: none;
		background: none;
		cursor: pointer;
	}

	.arrow {
		display: inline-block;
		width: 1rem;
		color: var(--color-gold);
		transform: rotate(0deg);
		transition: transform 0.12s linear;
	}

	.toggle:hover .arrow,
	.toggle:focus-visible .arrow {
		color: var(--color-hover);
	}

	.open .arrow {
		transform: rotate(90deg);
	}

	.link {
		display: inline-flex;
		gap: 0.55rem;
		align-items: center;
		color: inherit;
		font: inherit;
		text-decoration: none;
		text-shadow: inherit;
	}

	.link:hover,
	.link:focus-visible {
		color: var(--color-hover);
	}

	.values,
	.parts {
		display: grid;
		grid-template-columns: minmax(4.5rem, auto) 1fr minmax(4.5rem, auto);
	}

	.values {
		gap: 0.55rem;
		align-items: center;
	}

	.value {
		font-variant-numeric: tabular-nums;
	}

	.value.a {
		text-align: right;
	}

	.win {
		color: var(--color-gold);
	}

	.lose {
		color: var(--color-muted);
	}

	.bars {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.2rem;
		height: 0.55rem;
	}

	.bar {
		display: flex;
		overflow: hidden;
		border: 2px solid var(--color-border);
		box-shadow: inset 0 0 0 1px var(--button-shadow);
		background: var(--slot-bg);
	}

	.bar.a {
		justify-content: flex-end;
	}

	.fill {
		display: block;
		height: 100%;
	}

	.parts {
		gap: 0.15rem 0.55rem;
		color: var(--color-muted);
		font-size: 0.82rem;
	}

	.part-a {
		text-align: right;
	}

	.part-name {
		text-align: center;
	}

	.target {
		margin-left: 0.3rem;
		opacity: 0.8;
	}

	.parts .win {
		color: var(--color-gold);
	}

	@media (max-width: 700px) {
		.values {
			grid-template-columns: 1fr;
		}

		.value.a,
		.part-a {
			text-align: left;
		}
	}
</style>
