<script lang="ts">
	import { advancementMatch, advancementTabTitle, compareAdvancementTitles, playerAdvancementHref } from '#lib/advancements.ts';
	import { advancementView } from '#lib/advancementView.svelte.ts';
	import AdvancementTiles from '#lib/components/AdvancementTiles.svelte';
	import AdvancementTree from '#lib/components/AdvancementTree.svelte';
	import AdvancementViewSwitch from '#lib/components/AdvancementViewSwitch.svelte';
	import HiddenAdvancementsToggle from '#lib/components/HiddenAdvancementsToggle.svelte';
	import GroupTitle from '#lib/components/player/GroupTitle.svelte';
	import ListHead from '#lib/components/player/ListHead.svelte';
	import TabBar from '#lib/components/TabBar.svelte';
	import { hiddenFromView } from '#lib/hiddenAdvancements.svelte.ts';
	import { formatWhen, i18n, t } from '#lib/i18n/i18n.svelte.ts';
	import { cappedResults } from '#lib/search.svelte.ts';
	import type { AdvancementInfo, AdvancementTab, AdvancementsResponse, PlayerDetail } from '#lib/types.ts';

	let {
		player,
		catalog,
		query,
		searching,
		achievedOnly = $bindable(),
		selected = $bindable()
	}: {
		player: PlayerDetail;
		catalog: AdvancementsResponse | null;
		// Already deferred.
		query: string;
		searching: boolean;
		achievedOnly: boolean;
		// null: the first tab.
		selected: string | null;
	} = $props();

	type TabView = {
		tab: AdvancementTab;
		title: string;
		// The whole tab: done ones first, each part in title order.
		items: AdvancementInfo[];
		matched: AdvancementInfo[];
		matchedIds: Set<string>;
		shown: AdvancementInfo[];
		done: number;
	};

	const completed = $derived(player.advancements.completed);
	const progress = $derived(player.advancements.progress);

	function isDone(item: { id: string }): boolean {
		return Object.hasOwn(completed, item.id);
	}

	const views = $derived.by((): TabView[] => {
		if (!catalog) {
			return [];
		}
		return catalog.tabs.map((tab) => {
			const sorted = catalog.advancements.filter((item) => item.tab === tab.id).sort(compareAdvancementTitles);
			const done = sorted.filter(isDone);
			const items = [...done, ...sorted.filter((item) => !isDone(item))];
			// Title matches before description-only ones; the sort is stable, so each keeps its order.
			const matched = items
				.filter((item) => (!achievedOnly || isDone(item)) && !hiddenFromView(item))
				.map((item) => ({ item, score: advancementMatch(item.id, item.title, item.description, query) }))
				.filter((entry) => entry.score > 0)
				.sort((a, b) => b.score - a.score)
				.map((entry) => entry.item);
			return {
				tab,
				title: advancementTabTitle(tab.id, tab.title),
				items,
				matched,
				matchedIds: new Set(matched.map((item) => item.id)),
				shown: cappedResults(matched, searching),
				done: done.length
			};
		});
	});

	const current = $derived(views.find((view) => view.tab.id === selected) ?? views[0]);
	const withMatches = $derived(views.filter((view) => view.matched.length > 0));

	const barTabs = $derived(
		views.map((view) => ({
			id: view.tab.id,
			title: view.title,
			icon: view.tab.icon,
			count: `${view.done}/${view.items.length}`
		}))
	);

	function href(item: { id: string }): string {
		return playerAdvancementHref(player.name, item.id, player.uuid);
	}

	function progressText(item: { id: string }): string | undefined {
		const value = progress[item.id];
		return value ? `${value.done} / ${value.total}` : undefined;
	}
</script>

<ListHead count={t('advancements.doneOf', { done: player.advancements.done, total: player.advancements.total })}>
	<AdvancementViewSwitch />
	<label class="check">
		<input type="checkbox" bind:checked={achievedOnly} />
		{t('player.achievedOnly')}
	</label>
	<HiddenAdvancementsToggle />
</ListHead>

{#if !searching}
	{#if current}
		<TabBar
			tabs={barTabs}
			bind:selected={() => current.tab.id, (id) => (selected = id)}
			label={t('advancements.tabs')}
		/>
		<section class="content">{@render content(current)}</section>
	{/if}
{:else if withMatches.length > 0}
	{#each withMatches as view (view.tab.id)}
		<!-- The heading inside the section, so its margins sit where they always did. -->
		<section class="content">
			<GroupTitle
				icon={view.tab.icon}
				title={view.title}
				count={t('advancements.doneOf', { done: view.done, total: view.items.length })}
			/>
			{@render content(view)}
		</section>
	{/each}
{:else}
	<p class="muted">{t('player.noMatches')}</p>
{/if}

{#snippet content(view: TabView)}
	{#key `${view.tab.id}/${i18n.locale}`}
			{#if view.matched.length === 0}
				<p class="muted">{t('player.noneAchievedInTab')}</p>
			{:else if advancementView.mode === 'tree'}
				<!-- The tree keeps its shape: what the search leaves out is faint, what "achieved only" leaves out is gone. -->
				<AdvancementTree
					items={view.items}
					background={view.tab.background}
					obtained={isDone}
					{href}
					hidden={(item) => (achievedOnly && !isDone(item)) || hiddenFromView(item)}
					faded={(item) => !view.matchedIds.has(item.id)}
					detail={(item) => (completed[item.id] ? formatWhen(completed[item.id]) : progressText(item))}
				/>
			{:else}
				<AdvancementTiles
					items={view.shown}
					background={view.tab.background ?? ''}
					dim={(item) => !isDone(item)}
					obtained={isDone}
					when={(item) => (completed[item.id] ? formatWhen(completed[item.id]) : undefined)}
					{href}
					progress={progressText}
				/>
			{/if}
	{/key}
{/snippet}

<style>
	.content {
		margin-top: 0.55rem;
	}

	.muted {
		color: var(--color-muted);
	}
</style>
