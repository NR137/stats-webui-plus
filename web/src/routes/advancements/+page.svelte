<script lang="ts">
	import type { Snapshot } from './$types';
	import { getAdvancementMost, getAdvancements } from '#lib/api.ts';
	import {
		advancementHref,
		advancementMatch,
		advancementTabTitle,
		compareAdvancementTitles,
		matchesAdvancementQuery
	} from '#lib/advancements.ts';
	import { advancementView } from '#lib/advancementView.svelte.ts';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import AdvancementTiles from '#lib/components/AdvancementTiles.svelte';
	import AdvancementTree from '#lib/components/AdvancementTree.svelte';
	import AdvancementViewSwitch from '#lib/components/AdvancementViewSwitch.svelte';
	import HiddenAdvancementsToggle from '#lib/components/HiddenAdvancementsToggle.svelte';
	import Podium from '#lib/components/Podium.svelte';
	import Loader from '#lib/components/Loader.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import SearchField from '#lib/components/SearchField.svelte';
	import TabBar from '#lib/components/TabBar.svelte';
	import { formatValue } from '#lib/format.ts';
	import { hiddenFromView } from '#lib/hiddenAdvancements.svelte.ts';
	import { boardHint, pageTitle, t } from '#lib/i18n/i18n.svelte.ts';
	import { cappedResults, deferredValue } from '#lib/search.svelte.ts';
	import type { AdvancementInfo, AdvancementsResponse, LeaderboardEntry, LeaderboardResponse } from '#lib/types.ts';
	import { onMount } from 'svelte';

	let data = $state.raw<AdvancementsResponse | null>(null);
	let most = $state.raw<LeaderboardResponse | null>(null);
	let failed = $state(false);
	let query = $state('');
	let selectedTab = $state('');
	const deferred = deferredValue(() => query);

	// Back from an advancement, the same tab and search again.
	export const snapshot: Snapshot<{ query: string; tab: string }> = {
		capture: () => ({ query, tab: selectedTab }),
		restore: (value) => {
			query = value.query;
			selectedTab = value.tab;
		}
	};

	onMount(() => {
		let cancelled = false;
		// "Most" is extra: when it fails, the page just goes without the podium.
		Promise.all([getAdvancements(), getAdvancementMost().catch(() => null)])
			.then(([list, ranking]) => {
				if (!cancelled) {
					data = list;
					most = ranking;
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

	const queryText = $derived(deferred.current);
	const searching = $derived(queryText.trim() !== '');
	const obtained = (item: { holders?: number }) => (item.holders ?? 0) > 0;

	const sections = $derived.by(() => {
		if (!data) {
			return [];
		}
		const advancements = data.advancements;
		return data.tabs.map((tab) => {
			const all = advancements.filter((item) => item.tab === tab.id);
			const visible = all.filter((item) => !hiddenFromView(item));
			// Array sort is stable: each step keeps the order of the one before within its groups.
			const matching = visible
				.filter((item) => matchesAdvancementQuery(item.id, item.title, item.description, queryText))
				.sort(compareAdvancementTitles)
				.sort((a, b) => Number(obtained(b)) - Number(obtained(a)));
			if (searching) {
				matching.sort(
					(a, b) =>
						advancementMatch(b.id, b.title, b.description, queryText) -
						advancementMatch(a.id, a.title, a.description, queryText)
				);
			}
			return {
				tab,
				all,
				matching,
				matchingIds: new Set(matching.map((item) => item.id)),
				shown: cappedResults(matching, searching),
				total: all.length
			};
		});
	});

	const barTabs = $derived(
		sections.map(({ tab, total }) => ({
			id: tab.id,
			title: advancementTabTitle(tab.id, tab.title),
			icon: tab.icon,
			count: String(total)
		}))
	);
	const currentTab = $derived(
		sections.some((section) => section.tab.id === selectedTab) ? selectedTab : (sections[0]?.tab.id ?? '')
	);
	const visibleSections = $derived(
		searching
			? sections.filter((section) => section.matching.length > 0)
			: sections.filter((section) => section.tab.id === currentTab)
	);
	const hasAny = $derived((data?.tabs.length ?? 0) > 0 && (data?.advancements.length ?? 0) > 0);
	const nothingShown = $derived(visibleSections.every((section) => section.shown.length === 0));

	function holdersText(item: { holders?: number }): string {
		const count = item.holders ?? 0;
		return count === 1 ? t('advancements.holder') : t('advancements.holders', { count });
	}

	function display(entry: LeaderboardEntry): string {
		return formatValue(entry.value, most?.unit || 'count');
	}
</script>

<svelte:head>
	<title>{pageTitle(t('menu.advancements'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('advancements.heading')}</svelte:element>
<p class="lead">{t('advancements.lead')}</p>

{#if failed}
	<p class="message">{t('site.loadError')}</p>
{:else if !data}
	<Loader />
{:else}
	{#if most && most.entries.length > 0}
		<Podium entries={most.entries} {display} hint={boardHint('advancements')} />
		<div class="most">
			<a class="mc-btn" href="/advancements/most">{t('advancements.most')}</a>
		</div>
	{/if}

	<div class="filters">
		<div class="search">
			<SearchField bind:value={query} placeholder={t('advancements.search')} />
		</div>
		<AdvancementViewSwitch />
		<HiddenAdvancementsToggle />
	</div>

	{#if !hasAny}
		<p class="message">{t('advancements.nobody')}</p>
	{:else}
		{#if !searching}
			<TabBar
				tabs={barTabs}
				bind:selected={() => currentTab, (next) => (selectedTab = next)}
				label={t('advancements.tabs')}
			/>
		{/if}
		{#if nothingShown}
			<p class="message">{t('player.noMatches')}</p>
		{:else}
			{#each visibleSections as section (section.tab.id)}
				<section class="section">
					{#if searching}
						<p class="tab-heading">
							<ItemIcon id={section.tab.icon} compact />
							<span>{advancementTabTitle(section.tab.id, section.tab.title)}</span>
							<span class="shown">
								{t('player.shown', { shown: section.shown.length, total: section.total })}
							</span>
						</p>
					{/if}
					{#if advancementView.mode === 'tree'}
						<!-- The whole tab, so the tree keeps its shape; what doesn't match fades. -->
						<AdvancementTree
							items={section.all}
							background={section.tab.background}
							obtained={(item) => obtained(item as AdvancementInfo)}
							href={(item) => advancementHref(item.id)}
							hidden={hiddenFromView}
							faded={(item) => !section.matchingIds.has(item.id)}
							detail={(item) => holdersText(item as AdvancementInfo)}
						/>
					{:else}
						<AdvancementTiles
							items={section.shown}
							background={section.tab.background ?? ''}
							dim={(item) => Boolean(item.hidden)}
							{obtained}
							showHolders
						/>
					{/if}
				</section>
			{/each}
		{/if}
	{/if}
{/if}

<style>
	.page-title {
		margin: 0 0 0.4rem;
		font-size: 2rem;
		text-align: center;
	}

	.most {
		display: flex;
		justify-content: center;
		margin: 0 0 1.15rem;
	}

	.most .mc-btn {
		width: 100%;
		max-width: 20rem;
	}

	.filters {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		align-items: center;
		margin: 0 0 1.1rem;
	}

	.search {
		flex: 1 1 14rem;
	}

	.section {
		margin-top: 0.55rem;
	}

	.tab-heading {
		display: inline-flex;
		gap: 0.45rem;
		align-items: center;
		color: var(--color-gold);
		font-size: 1rem;
	}

	.shown {
		margin-left: 0.45rem;
		color: var(--color-muted);
		font-size: 0.75rem;
	}
</style>
