<script lang="ts">
	import { getLeaderboard } from '#lib/api.ts';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import Podium from '#lib/components/Podium.svelte';
	import Loader from '#lib/components/Loader.svelte';
	import RankedList from '#lib/components/RankedList.svelte';
	import Tabs from '#lib/components/Tabs.svelte';
	import { formatValue } from '#lib/format.ts';
	import { boardHint, pageTitle, t, tOptional } from '#lib/i18n/i18n.svelte.ts';
	import type { LeaderboardResponse } from '#lib/types.ts';
	import { onMount } from 'svelte';

	const RANKINGS = ['legends', 'veterans', 'grinders'] as const;
	type RankingId = (typeof RANKINGS)[number];

	let boards = $state.raw<Partial<Record<RankingId, LeaderboardResponse>> | null>(null);
	let failed = $state(false);
	let selected = $state<string>('legends');

	const tabs = $derived(RANKINGS.map((id) => ({ id, label: tOptional(`boards.${id}.name`, id) })));
	const board = $derived(boards?.[selected as RankingId] ?? null);
	const entries = $derived(board?.entries ?? []);
	const coin = $derived(selected === 'legends');
	const hint = $derived(boardHint(selected));

	onMount(() => {
		let cancelled = false;
		(async () => {
			try {
				// All at once, so switching tabs never waits; one that fails is just an empty ranking.
				const loaded = await Promise.all(RANKINGS.map((id) => getLeaderboard(id).catch(() => null)));
				if (cancelled) {
					return;
				}
				const next: Partial<Record<RankingId, LeaderboardResponse>> = {};
				RANKINGS.forEach((id, index) => {
					const answer = loaded[index];
					if (answer) {
						next[id] = answer;
					}
				});
				boards = next;
			} catch {
				if (!cancelled) {
					failed = true;
				}
			}
		})();
		return () => {
			cancelled = true;
		};
	});

	function display(entry: { value: number }): string {
		return formatValue(entry.value, board?.unit ?? 'count');
	}
</script>

<svelte:head>
	<title>{pageTitle(t('menu.ranking'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('ranking.heading')}</svelte:element>
<p class="lead">{t('ranking.lead')} <a class="how" href="/about">{t('about.howLink')}</a></p>

{#if failed}
	<p class="message">{t('site.loadError')}</p>
{:else if !boards}
	<Loader />
{:else}
	{#if entries.length > 0}
		<Podium {entries} display={display} {coin} {hint} />
	{/if}
	<Tabs {tabs} bind:selected label={t('ranking.heading')} />
	{#if entries.length > 0}
		<RankedList
			{entries}
			value={display}
			{coin}
			{hint}
			nameMinWidth={false}
			--ranked-search-margin="0 0 0.85rem"
			--ranked-list-margin="0"
		/>
	{:else}
		<p class="empty">{t('ranking.rankingEmpty')}</p>
	{/if}
{/if}

<style>
	.page-title {
		margin: 0 0 0.4rem;
		font-size: 2rem;
		text-align: center;
	}

	.how {
		display: inline-block;
		border-bottom: 1px dotted currentColor;
		color: var(--color-text);
	}

	.how:hover,
	.how:focus-visible {
		color: var(--color-hover);
	}

	.empty {
		color: var(--color-muted);
		text-align: left;
	}
</style>
