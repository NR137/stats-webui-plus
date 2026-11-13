<script lang="ts">
	import GroupTitle from '#lib/components/player/GroupTitle.svelte';
	import ListHead from '#lib/components/player/ListHead.svelte';
	import StatTile from '#lib/components/player/StatTile.svelte';
	import TabBar from '#lib/components/TabBar.svelte';
	import { formatValue } from '#lib/format.ts';
	import { boardHint, i18n, t, tOptional, type MessageKey } from '#lib/i18n/i18n.svelte.ts';
	import { cappedResults, GrowingLimit } from '#lib/search.svelte.ts';
	import type { LeaderboardsResponse, PlayerDetail, StatsResponse } from '#lib/types.ts';
	import {
		vanillaBoardHref,
		vanillaGroupIcons,
		vanillaGroups,
		vanillaHint,
		vanillaIcon,
		vanillaLabel,
		vanillaRows,
		visibleVanilla,
		type VanillaGroup,
		type VanillaRow
	} from '#lib/vanillaStats.ts';

	let {
		player,
		stats,
		leaderboards,
		query,
		searching,
		hideZeros = $bindable(),
		group = $bindable()
	}: {
		player: PlayerDetail;
		stats: StatsResponse | null;
		leaderboards: LeaderboardsResponse | null;
		// Already deferred.
		query: string;
		searching: boolean;
		hideZeros: boolean;
		group: VanillaGroup;
	} = $props();

	type GroupView = {
		group: VanillaGroup;
		rows: VanillaRow[];
		matched: VanillaRow[];
		shown: VanillaRow[];
		done: number;
	};

	const views = $derived.by((): GroupView[] => {
		if (!stats) {
			return [];
		}
		return vanillaGroups.map((id) => {
			// Zeros included: the API leaves out what the player never raised.
			const rows = vanillaRows(stats.groups[id], player.statistics[id]);
			const matched = visibleVanilla(id, rows, query, hideZeros);
			return {
				group: id,
				rows,
				matched,
				shown: cappedResults(matched, searching),
				done: rows.filter((row) => row.value > 0).length
			};
		});
	});

	const current = $derived(views.find((view) => view.group === group) ?? views[0]);
	const withMatches = $derived(views.filter((view) => view.matched.length > 0));

	const barTabs = $derived(
		views.map((view) => ({
			id: view.group,
			title: t(`stats.groups.${view.group}.name` as MessageKey),
			icon: vanillaGroupIcons[view.group],
			short: t(`stats.groups.${view.group}.short` as MessageKey),
			count: `${view.done}/${view.rows.length}`
		}))
	);

	// A group has up to 1,600 statistics: its tiles are built over a few frames.
	const limit = new GrowingLimit(() => [group, hideZeros, query, searching]);

	function rankText(row: { value: number; rank?: number }): string {
		return row.value > 0 && row.rank ? t('player.rankOf', { rank: row.rank, total: player.trackedPlayers }) : '';
	}

	const categories = $derived.by(() => {
		if (stats || !leaderboards) {
			return [];
		}
		const byId = new Map(leaderboards.boards.map((board) => [board.id, board]));
		return leaderboards.categories.map((category) => ({
			id: category.id,
			boards: category.boards.flatMap((id) => {
				const board = byId.get(id);
				return board ? [board] : [];
			})
		}));
	});
</script>

{#if stats}
	<ListHead>
		<label class="check">
			<input type="checkbox" bind:checked={hideZeros} />
			{t('player.hideZeros')}
		</label>
	</ListHead>

	{#if !searching}
		{#if current}
			<TabBar
				tabs={barTabs}
				bind:selected={() => current.group, (id) => (group = id as VanillaGroup)}
				label={t('stats.groupTabs')}
				namesOnPhones
			/>
			<section class="pack">{@render content(current)}</section>
		{/if}
	{:else if withMatches.length > 0}
		{#each withMatches as view (view.group)}
			<section class="pack">
				<GroupTitle
					icon={vanillaGroupIcons[view.group]}
					title={t(`stats.groups.${view.group}.name` as MessageKey)}
					count={t('player.shown', { shown: view.shown.length, total: view.rows.length })}
				/>
				{@render content(view)}
			</section>
		{/each}
	{:else}
		<p class="muted">{t('player.noMatches')}</p>
	{/if}
{:else}
	<!-- Without the list of all statistics: the leaderboards instead. -->
	{#each categories as category (category.id)}
		<section class="pack">
			<h2>{tOptional(`boards.categories.${category.id}`, category.id)}</h2>
			<div class="grid">
				{#each category.boards as board (board.id)}
					{@const own = player.boards[board.id]}
					<StatTile
						href="/leaderboards/{board.id}"
						icon={board.icon}
						value={own ? formatValue(own.value, own.unit) : '0'}
						label={tOptional(`boards.${board.id}.name`, board.id)}
						rank={own && own.rank > 0
							? t('player.rankOf', { rank: own.rank, total: player.trackedPlayers })
							: ''}
						hint={boardHint(board.id, board.unit)}
						zero={!own || own.value <= 0}
					/>
				{/each}
			</div>
		</section>
	{/each}
{/if}

{#snippet content(view: GroupView)}
	{#key `${view.group}/${i18n.locale}`}
		{#if view.matched.length === 0}
			<p class="muted">{t('player.noMatches')}</p>
		{:else}
			<div class="grid">
				{#each view.shown.slice(0, limit.value) as row (row.id)}
					<StatTile
						href={vanillaBoardHref(view.group, row.id)}
						icon={vanillaIcon(view.group, row.id)}
						value={formatValue(row.value, row.unit)}
						label={vanillaLabel(view.group, row.id)}
						rank={rankText(row)}
						hint={vanillaHint(view.group, row.id)}
						zero={row.value <= 0}
					/>
				{/each}
			</div>
		{/if}
	{/key}
{/snippet}

<style>
	.pack {
		margin-top: 0.55rem;
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
		gap: 0.45rem;
	}

	h2 {
		margin: 0 0 0.7rem;
		color: var(--color-muted);
		font-size: 1rem;
	}

	.muted {
		color: var(--color-muted);
	}
</style>
