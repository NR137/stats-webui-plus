<script lang="ts">
	import { matchesAdvancementQuery } from '#lib/advancements.ts';
	import SearchField from '#lib/components/SearchField.svelte';
	import { hiddenFromView } from '#lib/hiddenAdvancements.svelte.ts';
	import { t, type MessageKey } from '#lib/i18n/i18n.svelte.ts';
	import { cappedResults, deferredValue } from '#lib/search.svelte.ts';
	import type { AdvancementsResponse, LeaderboardsResponse, PlayerDetail } from '#lib/types.ts';
	import { vanillaGroups } from '#lib/vanillaStats.ts';
	import CompareAdvancements from './CompareAdvancements.svelte';
	import CompareLeaderboards from './CompareLeaderboards.svelte';
	import CompareRow from './CompareRow.svelte';
	import CompareSkills from './CompareSkills.svelte';
	import CompareStatistics from './CompareStatistics.svelte';
	import {
		leaderboardSections,
		matchingRows,
		overviewRows,
		rowMatches,
		skillRows,
		uniqueAdvancements,
		vanillaGroupRows,
		type StatisticsGroup,
		type UniqueAdvancement
	} from './compare';

	let {
		a,
		b,
		leaderboards,
		advancements
	}: {
		a: PlayerDetail;
		b: PlayerDetail;
		// Either may have failed to load; their sections are left out then.
		leaderboards: LeaderboardsResponse | null;
		advancements: AdvancementsResponse | null;
	} = $props();

	let search = $state('');
	const delayed = deferredValue(() => search);
	const query = $derived(delayed.current.trim().toLowerCase());
	const searching = $derived(query !== '');

	const overview = $derived(overviewRows(a, b).filter((row) => rowMatches(row, query)));

	const skills = $derived(skillRows(a, b));
	const shownSkills = $derived(matchingRows(skills, query, t('player.skills')));

	const boards = $derived(
		leaderboardSections(a, b, leaderboards)
			.map((section) => ({ ...section, rows: matchingRows(section.rows, query, section.name, t('menu.leaderboards')) }))
			.filter((section) => section.rows.length > 0)
	);

	const statistics = $derived(
		vanillaGroups.map((group): StatisticsGroup => {
			const rows = vanillaGroupRows(a, b, group);
			const name = t(`stats.groups.${group}.name` as MessageKey);
			return { group, rows: cappedResults(matchingRows(rows, query, name), searching), total: rows.length };
		})
	);
	// Not searching, the selected group is always shown (also when it is empty).
	const anyStatistics = $derived(!searching || statistics.some((entry) => entry.rows.length > 0));

	const catalog = $derived(new Map((advancements?.advancements ?? []).map((item) => [item.id, item])));
	const onlyText = (name: string) => t('compare.onlyAdvancements', { name }).toLowerCase();
	const unique = $derived.by(() => {
		// Hidden ones stay out as on every page: a guard against spoilers.
		const visible = (items: UniqueAdvancement[]) => items.filter((item) => !hiddenFromView(item));
		const own = visible(uniqueAdvancements(a, b, catalog));
		const theirs = visible(uniqueAdvancements(b, a, catalog));
		const all =
			!query ||
			[t('compare.advancementsDiff').toLowerCase(), onlyText(a.name), onlyText(b.name)].some((text) =>
				text.includes(query)
			);
		const matching = (items: UniqueAdvancement[]) =>
			all ? items : items.filter((item) => matchesAdvancementQuery(item.id, item.title, item.description, query));
		return { a: matching(own), b: matching(theirs) };
	});
	const anyUnique = $derived(unique.a.length > 0 || unique.b.length > 0);

	const anything = $derived(
		overview.length > 0 || shownSkills.length > 0 || boards.length > 0 || anyStatistics || anyUnique
	);
</script>

<div class="search">
	<SearchField bind:value={search} placeholder={t('player.search')} />
</div>
{#if anything}
	<div class="rows">
		{#each overview as row (row.id)}
			<CompareRow {row} />
		{/each}
		{#if shownSkills.length > 0}
			<CompareSkills {a} {b} {skills} shown={shownSkills} />
		{/if}
		{#if boards.length > 0}
			<CompareLeaderboards sections={boards} />
		{/if}
		{#if anyStatistics}
			<CompareStatistics groups={statistics} {searching} />
		{/if}
		{#if anyUnique}
			<CompareAdvancements
				nameA={a.name}
				nameB={b.name}
				tabs={advancements?.tabs ?? []}
				a={unique.a}
				b={unique.b}
				{searching}
			/>
		{/if}
	</div>
{:else}
	<p class="muted">{t('player.noMatches')}</p>
{/if}

<style>
	.search {
		margin: 0 0 0.85rem;
	}

	.rows {
		display: flex;
		flex-direction: column;
		gap: 0.45rem;
	}

	.muted {
		color: var(--color-muted);
		text-align: center;
	}
</style>
