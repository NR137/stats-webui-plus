<script lang="ts">
	import { goto } from '$app/navigation';
	import { getPlayers } from '#lib/api.ts';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import Loader from '#lib/components/Loader.svelte';
	import PlayerAvatar from '#lib/components/PlayerAvatar.svelte';
	import RankingScore from '#lib/components/RankingScore.svelte';
	import SearchField from '#lib/components/SearchField.svelte';
	import { formatValue } from '#lib/format.ts';
	import { pageTitle, t, type MessageKey } from '#lib/i18n/i18n.svelte.ts';
	import { playerPath } from '#lib/players.ts';
	import type { PlayerSummary } from '#lib/types.ts';
	import { onMount } from 'svelte';

	type SortKey = 'hours' | 'rate' | 'score' | 'advancements' | 'player';
	type Direction = 'asc' | 'desc';

	const columns: { key: SortKey; label: MessageKey; numeric: boolean; wide: boolean }[] = [
		{ key: 'player', label: 'players.columns.player', numeric: false, wide: false },
		{ key: 'hours', label: 'players.columns.hours', numeric: true, wide: false },
		{ key: 'rate', label: 'players.columns.rate', numeric: true, wide: false },
		{ key: 'score', label: 'players.columns.score', numeric: true, wide: true },
		{ key: 'advancements', label: 'players.columns.advancements', numeric: true, wide: true }
	];

	const sortValue: Record<Exclude<SortKey, 'player'>, (player: PlayerSummary) => number> = {
		hours: (player) => player.playHours,
		rate: (player) => player.actionsPerHour,
		score: (player) => player.level,
		advancements: (player) => player.advancements.done
	};

	let players = $state.raw<PlayerSummary[] | null>(null);
	let failed = $state(false);
	let query = $state('');
	let sortKey = $state<SortKey>('hours');
	let direction = $state<Direction>('desc');

	onMount(() => {
		let cancelled = false;
		getPlayers()
			.then((answer) => {
				if (!cancelled) {
					players = answer.players;
				}
			})
			.catch(() => {
				if (!cancelled) {
					failed = true;
				}
			});
		return () => {
			cancelled = true;
		};
	});

	function defaultDirection(key: SortKey): Direction {
		return key === 'player' ? 'asc' : 'desc';
	}

	function sortBy(key: SortKey) {
		if (key === sortKey) {
			direction = direction === 'asc' ? 'desc' : 'asc';
			return;
		}
		sortKey = key;
		direction = defaultDirection(key);
	}

	function compareNames(a: PlayerSummary, b: PlayerSummary): number {
		return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
	}

	// Ranks are places in the sorted list of all players: the search only hides rows.
	const ranked = $derived.by(() => {
		if (!players) {
			return [];
		}
		const sign = direction === 'asc' ? 1 : -1;
		const key = sortKey;
		const sorted = [...players].sort((a, b) => {
			const primary = key === 'player' ? compareNames(a, b) : sortValue[key](a) - sortValue[key](b);
			return primary !== 0 ? primary * sign : compareNames(a, b);
		});
		return sorted.map((player, index) => ({ player, rank: index + 1 }));
	});

	const needle = $derived(query.trim().toLowerCase());
	const shown = $derived(needle ? ranked.filter((row) => row.player.name.toLowerCase().includes(needle)) : ranked);

	const countText = $derived.by(() => {
		const total = players?.length ?? 0;
		if (needle) {
			return t('player.shown', { shown: shown.length, total });
		}
		return total === 1 ? t('players.countOne') : t('players.count', { count: total });
	});

	function ariaSort(key: SortKey): 'ascending' | 'descending' | 'none' {
		if (key !== sortKey) {
			return 'none';
		}
		return direction === 'asc' ? 'ascending' : 'descending';
	}

	function open(player: PlayerSummary) {
		goto(playerPath(player.name, player.uuid));
	}

	// The whole row opens the player, but a link or button in it keeps its own click, and modified clicks (new tab)
	// stay with the browser.
	function onRowClick(event: MouseEvent, player: PlayerSummary) {
		if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
			return;
		}
		if (event.target instanceof Element && event.target.closest('a, button')) {
			return;
		}
		open(player);
	}

	function onRowKey(event: KeyboardEvent, player: PlayerSummary) {
		if (event.target !== event.currentTarget) {
			return;
		}
		if (event.key === 'Enter' || event.key === ' ') {
			event.preventDefault();
			open(player);
		}
	}
</script>

<svelte:head>
	<title>{pageTitle(t('menu.players'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('players.heading')}</svelte:element>
<p class="lead">{t('players.lead')}</p>

{#if failed}
	<p class="message">{t('site.loadError')}</p>
{:else if !players}
	<Loader />
{:else if players.length === 0}
	<p class="message">{t('players.empty')}</p>
{:else}
	<div class="search">
		<SearchField bind:value={query} placeholder={t('ranking.search')} />
	</div>
	<p class="count">{countText}</p>
	{#if shown.length === 0}
		<p class="message">{t('player.noMatches')}</p>
	{:else}
		<div class="scroller">
			<table>
				<thead>
					<tr>
						<th class="num rank-head" scope="col">#</th>
						{#each columns as column (column.key)}
							<th
								scope="col"
								class:num={column.numeric}
								class:wide={column.wide}
								class:hours={column.key === 'hours'}
								aria-sort={ariaSort(column.key)}
							>
								<button
									type="button"
									class="sort"
									class:on={column.key === sortKey}
									onclick={() => sortBy(column.key)}
								>
									{t(column.label)}
									<span aria-hidden="true">{column.key === sortKey ? (direction === 'desc' ? '▼' : '▲') : ''}</span>
								</button>
							</th>
						{/each}
					</tr>
				</thead>
				<tbody>
					{#each shown as { player, rank } (player.uuid)}
						<!-- The link in the row is the accessible way in; the row's click and keys are a shortcut. -->
						<tr
							tabindex="0"
							onclick={(event) => onRowClick(event, player)}
							onkeydown={(event) => onRowKey(event, player)}
						>
							<td class="num rank">{rank}</td>
							<td>
								<a class="player" href={playerPath(player.name, player.uuid)}>
									<PlayerAvatar uuid={player.uuid} variant="head" scale={5} />
									<span class="name">{player.name}</span>
								</a>
							</td>
							<td class="num hours">{formatValue(player.playHours, 'hours')}</td>
							<td class="num">{formatValue(player.actionsPerHour, 'actions_per_hour')}</td>
							<td class="num wide"><RankingScore value={formatValue(player.level, 'level')} /></td>
							<td class="num wide">
								{t('advancements.doneOf', {
									done: player.advancements.done,
									total: player.advancements.total
								})}
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}
{/if}

<style>
	.page-title {
		margin: 0 0 0.4rem;
		font-size: 2rem;
		text-align: center;
	}

	.search {
		margin: 0.9rem 0 0.65rem;
	}

	.count {
		margin: 0 0 0.75rem;
		color: var(--color-muted);
	}

	.scroller {
		overflow-x: auto;
	}

	table {
		width: 100%;
		border-collapse: separate;
		border-spacing: 0 0.35rem;
	}

	th {
		padding: 0 0.6rem 0.35rem;
		color: var(--color-muted);
		font-weight: normal;
		text-align: left;
		white-space: nowrap;
	}

	th.num {
		text-align: right;
	}

	/* The # over the ranks: gold and right-aligned like them. */
	th.rank-head {
		width: 2.4rem;
		color: var(--color-gold);
	}

	/* Plain text that sorts: none of the button's look. */
	.sort,
	.sort:hover,
	.sort:focus-visible {
		display: inline-flex;
		gap: 0.35rem;
		align-items: center;
		min-height: 0;
		margin: 0;
		padding: 0;
		border: 0;
		box-shadow: none;
		background: none;
		color: inherit;
		font: inherit;
		text-shadow: inherit;
		appearance: none;
		cursor: pointer;
	}

	th.num .sort {
		justify-content: flex-end;
		width: 100%;
	}

	.sort:hover,
	.sort:focus-visible,
	.sort.on {
		color: var(--color-gold);
	}

	/* A row is a slot of the game's inventory; a row can't have a shadow, so its cells share the slot's edge. */
	td {
		padding: 0.5rem 0.6rem;
		border-top: 2px solid var(--color-border);
		border-bottom: 2px solid var(--color-border);
		box-shadow:
			inset 0 2px 0 var(--slot-edge-dark),
			inset 0 -2px 0 var(--slot-edge-light);
		background: var(--slot-bg);
		white-space: nowrap;
		vertical-align: middle;
	}

	td:first-child {
		border-left: 2px solid var(--color-border);
		box-shadow:
			inset 2px 0 0 var(--slot-edge-dark),
			inset 0 2px 0 var(--slot-edge-dark),
			inset 0 -2px 0 var(--slot-edge-light);
	}

	td:last-child {
		border-right: 2px solid var(--color-border);
		box-shadow:
			inset 0 2px 0 var(--slot-edge-dark),
			inset 0 -2px 0 var(--slot-edge-light),
			inset -2px 0 0 var(--slot-edge-light);
	}

	tbody tr {
		cursor: pointer;
	}

	tbody tr:hover td {
		background: var(--slot-hover);
		color: var(--color-hover);
	}

	.num {
		font-variant-numeric: tabular-nums;
		text-align: right;
	}

	.rank {
		width: 2.4rem;
		color: var(--color-gold);
	}

	.hours {
		color: var(--color-gold);
	}

	.player {
		display: flex;
		gap: 0.7rem;
		align-items: center;
	}

	.name {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		hyphens: none;
	}

	@media (max-width: 640px) {
		.wide {
			display: none;
		}

		th,
		td {
			padding-right: 0.4rem;
			padding-left: 0.4rem;
		}


		/* The table scrolls sideways here; a long name ends in "…" instead of pushing the numbers out. */
		.name {
			max-width: 10rem;
		}
	}
</style>
