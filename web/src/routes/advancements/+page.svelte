<script lang="ts">
	import { pageTitleTag } from '$lib/branding.svelte';
	import {
		advancementHref,
		advancementMatch,
		advancementTabTitle,
		compareAdvancementTitles,
		matchesAdvancementQuery
	} from '$lib/advancements';
	import { boardHint, i18n, pageTitle, t } from '$lib/i18n/i18n.svelte';
	import { getAdvancementMost, getAdvancements } from '$lib/api';
	import AdvancementTiles from '$lib/components/AdvancementTiles.svelte';
	import AdvancementTabBar from '$lib/components/AdvancementTabBar.svelte';
	import AdvancementTree from '$lib/components/AdvancementTree.svelte';
	import AdvancementViewSwitch from '$lib/components/AdvancementViewSwitch.svelte';
	import { advancementView } from '$lib/advancementView.svelte';
	import HomePodium from '$lib/components/HomePodium.svelte';
	import Loader from '$lib/components/Loader.svelte';
	import McItem from '$lib/components/McItem.svelte';
	import SearchField from '$lib/components/SearchField.svelte';
	import { cappedResults, deferredValue } from '$lib/search.svelte';
	import type { AdvancementInfo, AdvancementTab, LeaderboardResponse } from '$lib/types';

	let catalog = $state<AdvancementInfo[]>([]);
	let tabs = $state<AdvancementTab[]>([]);
	let total = $state(0);
	let ranking = $state<LeaderboardResponse | null>(null);
	let failed = $state(false);
	let loaded = $state(false);
	let query = $state('');
	// The tab shown (one at a time, like the game's screen); during a search every tab with matches.
	let selectedTab = $state('');
	const filterQuery = deferredValue(() => query);
	const searching = $derived(filterQuery.current.trim().length > 0);
	const hint = $derived(boardHint('advancements'));

	$effect(() => {
		failed = false;
		loaded = false;
		Promise.all([getAdvancements(), getAdvancementMost().catch(() => null)])
			.then(([data, most]) => {
				catalog = data.advancements;
				tabs = data.tabs;
				total = data.total;
				ranking = most;
			})
			.catch(() => {
				failed = true;
			})
			.finally(() => {
				loaded = true;
			});
	});


	const sections = $derived.by(() => {
		void i18n.locale;
		const needle = filterQuery.current;
		return tabs
			.map((tab) => {
				const items = catalog
					.filter(
						(item) =>
							item.tab === tab.id &&
							matchesAdvancementQuery(item.id, item.title, item.description, needle)
					)
					.sort(compareAdvancementTitles);
				// Ones at least one player finished first, then the rest; both in title order. Searching: title matches
				// before ones found only by their description (sort keeps the order otherwise).
				const matched = [
					...items.filter((item) => (item.holders ?? 0) > 0),
					...items.filter((item) => !item.holders)
				].sort(
					(a, b) =>
						advancementMatch(b.id, b.title, b.description, needle) -
						advancementMatch(a.id, a.title, a.description, needle)
				);
				const all = catalog.filter((item) => item.tab === tab.id);
				return { tab, all, matched, ids: cappedResults(matched, searching) };
			});
	});
	// One tab, or during a search every tab with matches.
	const shownSections = $derived(
		searching
			? sections.filter((section) => section.matched.length > 0)
			: sections.filter((section) => section.tab.id === (selectedTab || tabs[0]?.id))
	);
</script>

<svelte:head>
	<title>{pageTitle(t('nav.advancements'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('advancements.heading')}</svelte:element>
<p class="lead">{t('advancements.lead')}</p>

{#if failed}
	<p class="empty">{t('advancements.loadError')}</p>
{:else if !loaded}
	<Loader />
{:else}
	{#if ranking && ranking.entries.length > 0}
		<HomePodium entries={ranking.entries} hint={hint} />
		<div class="most">
			<a class="mc-btn" href="/advancements/most">{t('advancements.most')}</a>
		</div>
	{/if}

	<div class="filters">
		<SearchField bind:value={query} placeholder={t('advancements.search')} />
		<AdvancementViewSwitch />
	</div>

	{#if !searching && tabs.length > 0 && total > 0}
		<AdvancementTabBar
			label={t('advancements.tabs')}
			tabs={sections.map((section) => ({ ...section.tab, count: String(section.all.length) }))}
			bind:selected={() => selectedTab || tabs[0]?.id, (id) => (selectedTab = id)}
		/>
	{/if}
	{#if shownSections.length > 0 && total > 0}
		{#each shownSections as section (section.tab.id)}
			<section class="pack">
				{#if searching}
					<p class="pack-title">
						<McItem id={section.tab.icon} compact />
						{advancementTabTitle(section.tab.id, section.tab.title)}
						<span class="count">{t('player.shown', { shown: section.ids.length, total: section.all.length })}</span>
					</p>
				{/if}
				{#if advancementView.mode === 'tree'}
					<!-- The whole tab, so the tree keeps its shape; what the search leaves out is faint. -->
					<AdvancementTree
						items={section.all}
						background={section.tab.background}
						obtained={(item) => ((item as { holders?: number }).holders ?? 0) > 0}
						href={(item) => advancementHref(item.id)}
						faded={(item) => !section.matched.some((match) => match.id === item.id)}
						detail={(item) => {
							const holders = (item as { holders?: number }).holders ?? 0;
							return holders === 1 ? t('advancements.holder') : t('advancements.holders', { count: holders });
						}}
					/>
				{:else}
					<AdvancementTiles
						items={section.ids}
						background={section.tab.background ?? ''}
						dim={(item) => Boolean(item.hidden)}
						obtained={(item) => (item.holders ?? 0) > 0}
						showHolders
					/>
				{/if}
			</section>
		{/each}
	{:else if total === 0}
		<p class="empty">{t('advancements.nobody')}</p>
	{:else}
		<p class="empty">{t('player.noMatches')}</p>
	{/if}
{/if}

<style>
	.page-title {
		/* Same look whether this is the page's h1 or an h2 below the site heading. */
		font-size: 2rem;
		margin: 0 0 0.4rem;
		text-align: center;
	}

	.lead,
	.empty {
		color: var(--color-muted);
		text-align: center;
	}

	.most {
		display: flex;
		justify-content: center;
		margin: 0 0 1.15rem;
	}

	.most :global(.mc-btn) {
		width: min(20rem, 100%);
	}

	.filters {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.5rem;
		margin: 0 0 1.1rem;
	}

	.filters > :global(:first-child) {
		flex: 1 1 14rem;
	}

	.pack {
		margin-top: 0.55rem;
	}

	.pack-title {
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		font-size: 1rem;
		color: var(--color-gold);
	}

	.count {
		margin-left: 0.45rem;
		font-size: 0.75rem;
		color: var(--color-muted);
	}
</style>
