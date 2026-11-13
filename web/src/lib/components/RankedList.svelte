<script lang="ts" module>
	export type RankedEntry = { rank: number; name: string; uuid: string };
</script>

<script lang="ts" generics="Entry extends RankedEntry">
	import PlayerAvatar from '#lib/components/PlayerAvatar.svelte';
	import RankingScore from '#lib/components/RankingScore.svelte';
	import SearchField from '#lib/components/SearchField.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import { playerPath } from '#lib/players.ts';

	// The ranked players of the start page, a board and an advancement. Margins differ per page: they come in as
	// --ranked-search-margin and --ranked-list-margin (defaults: a board's page).
	let {
		entries,
		value,
		href = (entry: Entry) => playerPath(entry.name, entry.uuid),
		hint = '',
		coin = false,
		nameMinWidth = true
	}: {
		entries: Entry[];
		// The text shown as an entry's value.
		value: (entry: Entry) => string;
		href?: (entry: Entry) => string;
		// The tooltip of the values.
		hint?: string;
		// Values as coins (RankingScore) instead of plain text.
		coin?: boolean;
		// Keep room for about ten letters of a name (a long value wraps instead); off on the start page.
		nameMinWidth?: boolean;
	} = $props();

	let query = $state('');

	const shown = $derived.by(() => {
		const needle = query.trim().toLowerCase();
		return needle ? entries.filter((entry) => entry.name.toLowerCase().includes(needle)) : entries;
	});

	// From all entries, not only the shown ones, so the heads stay in place while searching.
	const rankDigits = $derived(entries.reduce((most, entry) => Math.max(most, String(entry.rank).length), 1));
</script>

<div class="search">
	<SearchField bind:value={query} placeholder={t('ranking.search')} />
</div>

{#if shown.length === 0}
	<p class="none">{t('player.noMatches')}</p>
{:else}
	<ol class="list" class:wide-name={nameMinWidth} style:--rank-digits={rankDigits}>
		{#each shown as entry (entry.uuid)}
			<li>
				<a class="slot row" href={href(entry)}>
					<span class="rank">{entry.rank}</span>
					<PlayerAvatar uuid={entry.uuid} variant="head" scale={5} />
					<span class="name">{entry.name}</span>
					<Tooltip text={hint} align="end">
						{#if coin}
							<RankingScore value={value(entry)} />
						{:else}
							<span class="plain">{value(entry)}</span>
						{/if}
					</Tooltip>
				</a>
			</li>
		{/each}
	</ol>
{/if}

<style>
	.search {
		margin: var(--ranked-search-margin, 1rem 0 0);
	}

	.none {
		margin: var(--ranked-list-margin, 1.25rem 0 0);
		color: var(--color-muted);
		text-align: center;
	}

	.list {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		margin: var(--ranked-list-margin, 1.25rem 0 0);
		padding: 0;
		overflow: visible;
		list-style: none;
	}

	/* The rank column as wide as the longest rank, so the heads follow right after it. */
	.row {
		display: grid;
		grid-template-columns: calc(var(--rank-digits, 2) * 1ch) 2.5rem 1fr auto;
		column-gap: 0.8rem;
		align-items: center;
		padding: 0.55rem 0.75rem;
		overflow: visible;
	}

	/* The name keeps room for about ten letters; a long value (a date on phones) wraps instead. */
	.wide-name .row {
		grid-template-columns: calc(var(--rank-digits, 2) * 1ch) 2.5rem minmax(7rem, 1fr) minmax(0, auto);
	}

	.rank {
		color: var(--color-gold);
		font-variant-numeric: tabular-nums;
		text-align: right;
	}

	/* A player name is no word: cut off, never hyphenated or wrapped. */
	.name {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		hyphens: none;
	}

	.plain {
		color: var(--color-accent);
		font-variant-numeric: tabular-nums;
	}
</style>
