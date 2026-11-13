<script lang="ts">
	import { getAdvancements, getLeaderboards, getPlayer, getStats, NotFoundError } from '#lib/api.ts';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import BackNav from '#lib/components/BackNav.svelte';
	import Loader from '#lib/components/Loader.svelte';
	import PlayerAdvancements from '#lib/components/player/PlayerAdvancements.svelte';
	import PlayerHero from '#lib/components/player/PlayerHero.svelte';
	import PlayerStatistics from '#lib/components/player/PlayerStatistics.svelte';
	import SearchField from '#lib/components/SearchField.svelte';
	import Tabs from '#lib/components/Tabs.svelte';
	import { pageTitle, t } from '#lib/i18n/i18n.svelte.ts';
	import { deferredValue } from '#lib/search.svelte.ts';
	import type { AdvancementsResponse, LeaderboardsResponse, PlayerDetail, StatsResponse } from '#lib/types.ts';
	import type { VanillaGroup } from '#lib/vanillaStats.ts';
	import type { Snapshot } from '@sveltejs/kit';
	import { untrack } from 'svelte';
	import { page } from '$app/state';

	// A derived string: page.params is replaced on every navigation, tab switches included, but the name only changes
	// for another player, so only that reloads.
	const name = $derived(page.params.name ?? '');
	const tab = $derived(page.params.tab === 'advancements' ? 'advancements' : 'stats');

	let player = $state<PlayerDetail | null>(null);
	let catalog = $state<AdvancementsResponse | null>(null);
	let leaderboards = $state<LeaderboardsResponse | null>(null);
	let stats = $state<StatsResponse | null>(null);
	let failure = $state<'notFound' | 'error' | null>(null);

	let query = $state('');
	let hideZeros = $state(true);
	let achievedOnly = $state(false);
	let group = $state<VanillaGroup>('custom');
	// null: the first advancement tab.
	let advancementTab = $state<string | null>(null);

	const deferred = deferredValue(() => query);
	const searching = $derived(deferred.current.trim() !== '');

	// Back from a leaderboard or an advancement, the visitor finds the list as it was left.
	export const snapshot: Snapshot<{ query: string; group: VanillaGroup; advancementTab: string | null }> = {
		capture: () => ({ query, group, advancementTab }),
		restore: (value) => {
			query = value.query;
			group = value.group;
			advancementTab = value.advancementTab;
		}
	};

	$effect(() => {
		const current = name;
		untrack(() => {
			player = null;
			catalog = null;
			leaderboards = null;
			stats = null;
			failure = null;
			query = '';
			hideZeros = true;
			achievedOnly = false;
			group = 'custom';
			advancementTab = null;
		});
		let cancelled = false;
		// The catalogs only add to the page: without them it still shows the player.
		Promise.all([
			getPlayer(current),
			getAdvancements().catch(() => null),
			getLeaderboards().catch(() => null),
			getStats().catch(() => null)
		])
			.then(([loadedPlayer, loadedCatalog, loadedBoards, loadedStats]) => {
				if (cancelled) {
					return;
				}
				catalog = loadedCatalog;
				leaderboards = loadedBoards;
				stats = loadedStats;
				player = loadedPlayer;
			})
			.catch((error: unknown) => {
				if (!cancelled) {
					failure = error instanceof NotFoundError ? 'notFound' : 'error';
				}
			});
		return () => {
			cancelled = true;
		};
	});

	const tabs = $derived([
		{ id: 'stats', label: t('player.stats'), href: `/players/${encodeURIComponent(name)}` },
		{ id: 'advancements', label: t('player.advancements'), href: `/players/${encodeURIComponent(name)}/advancements` }
	]);
</script>

<svelte:head>
	<title>{pageTitle(name)}</title>
</svelte:head>

<BackNav />

{#if failure}
	<svelte:element this={pageTitleTag()} class="page-title player-name">{name}</svelte:element>
	<p class="muted">{failure === 'notFound' ? t('player.notFound') : t('site.loadError')}</p>
{:else if !player}
	<svelte:element this={pageTitleTag()} class="page-title player-name">{name}</svelte:element>
	<Loader />
{:else}
	<PlayerHero {player} {catalog} />

	<Tabs {tabs} selected={tab} label={player.name} />

	<div class="search">
		<SearchField
			bind:value={query}
			placeholder={tab === 'advancements' ? t('advancements.search') : t('player.search')}
		/>
	</div>

	{#if tab === 'advancements'}
		<PlayerAdvancements
			{player}
			{catalog}
			query={deferred.current}
			{searching}
			bind:achievedOnly
			bind:selected={advancementTab}
		/>
	{:else}
		<PlayerStatistics
			{player}
			{stats}
			{leaderboards}
			query={deferred.current}
			{searching}
			bind:hideZeros
			bind:group
		/>
	{/if}
{/if}

<style>
	.muted {
		color: var(--color-muted);
	}

	/* The name before the player is loaded (or when they aren't found), as the hero shows it once they are. */
	.page-title {
		min-width: 0;
		margin: 0;
		overflow: hidden;
		font-size: clamp(1.25rem, 6.5vw, 2rem);
		text-overflow: ellipsis;
		white-space: nowrap;
		hyphens: manual;
	}

	.search {
		margin-bottom: 1.1rem;
	}
</style>
