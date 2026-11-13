<script lang="ts">
	import { getLeaderboards } from '#lib/api.ts';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import Loader from '#lib/components/Loader.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import SearchField from '#lib/components/SearchField.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { boardHint, pageTitle, t, tOptional } from '#lib/i18n/i18n.svelte.ts';
	import { deferredValue } from '#lib/search.svelte.ts';
	import type { LeaderboardBoard, LeaderboardsResponse } from '#lib/types.ts';
	import { onMount } from 'svelte';

	let data = $state.raw<LeaderboardsResponse | null>(null);
	let failed = $state(false);
	let query = $state('');
	const deferred = deferredValue(() => query);

	onMount(() => {
		let cancelled = false;
		getLeaderboards()
			.then((answer) => {
				if (!cancelled) {
					data = answer;
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

	function boardName(id: string): string {
		return tOptional(`boards.${id}.name`, id);
	}

	function matches(board: LeaderboardBoard, needle: string): boolean {
		return !needle || boardName(board.id).toLowerCase().includes(needle) || board.id.toLowerCase().includes(needle);
	}

	const categories = $derived.by(() => {
		if (!data) {
			return [];
		}
		const needle = deferred.current.trim().toLowerCase();
		const byId = new Map(data.boards.map((board) => [board.id, board]));
		return data.categories
			.map((category) => ({
				category,
				boards: category.boards
					.map((id) => byId.get(id))
					.filter((board): board is LeaderboardBoard => board !== undefined && matches(board, needle))
			}))
			.filter((group) => group.boards.length > 0);
	});
</script>

<svelte:head>
	<title>{pageTitle(t('menu.leaderboards'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('boards.page.heading')}</svelte:element>
<p class="lead">{t('boards.page.lead')}</p>

<div class="search">
	<SearchField bind:value={query} placeholder={t('player.search')} />
</div>

{#each categories as { category, boards } (category.id)}
	<h2 class="category">
		<ItemIcon id={category.icon} compact />
		<span>{tOptional(`boards.categories.${category.id}`, category.id)}</span>
	</h2>
	<div class="grid">
		{#each boards as board (board.id)}
			<div class="cell">
				<Tooltip text={boardHint(board.id, board.unit)} fill>
					<a class="mc-btn board" href="/leaderboards/{board.id}">
						<ItemIcon id={board.icon} compact />
						<span>{boardName(board.id)}</span>
					</a>
				</Tooltip>
			</div>
		{/each}
	</div>
{:else}
	{#if failed}
		<p class="message">{t('site.loadError')}</p>
	{:else if !data}
		<Loader />
	{:else}
		<p class="message">{t('player.noMatches')}</p>
	{/if}
{/each}

<p class="all">
	<a href="/stats">{t('boards.page.allStats')}</a>
</p>

<style>
	.page-title {
		margin: 0 0 0.4rem;
		font-size: 2rem;
		text-align: center;
	}

	.search {
		margin: 0.9rem 0 1.1rem;
	}

	.category {
		display: flex;
		gap: 0.45rem;
		align-items: center;
		margin: 1.6rem 0 0.6rem;
		color: var(--color-gold);
		font-size: 1rem;
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
		gap: 0.5rem;
	}

	/* The tooltip's wrapper and hit area fill the cell, so the whole button shows the hint and opens the board. */
	.cell {
		display: flex;
	}

	.board {
		justify-content: flex-start;
		width: 100%;
		height: 100%;
		text-align: left;
	}

	.all {
		margin: 1.6rem 0 0;
		text-align: center;
	}

	.all a {
		color: var(--color-muted);
		text-decoration: underline dotted 2px;
		text-underline-offset: 0.25em;
	}

	.all a:hover,
	.all a:focus-visible {
		color: var(--color-hover);
	}
</style>
