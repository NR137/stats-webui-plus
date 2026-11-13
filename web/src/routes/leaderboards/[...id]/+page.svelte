<script lang="ts">
	import { page } from '$app/state';
	import { getLeaderboard, getStatistic, NotFoundError } from '#lib/api.ts';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import BackNav from '#lib/components/BackNav.svelte';
	import Podium from '#lib/components/Podium.svelte';
	import Loader from '#lib/components/Loader.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import RankedList from '#lib/components/RankedList.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { formatValue } from '#lib/format.ts';
	import { boardHint, pageTitle, t, tOptional, unitText } from '#lib/i18n/i18n.svelte.ts';
	import type { LeaderboardEntry, LeaderboardResponse, StatisticBoardResponse } from '#lib/types.ts';
	import { parseVanillaBoardId, vanillaGroups, vanillaHint, vanillaIcon, vanillaLabel } from '#lib/vanillaStats.ts';

	type Status = 'loading' | 'ready' | 'notFound' | 'failed';

	const id = $derived(page.params.id ?? '');
	const statistic = $derived(parseVanillaBoardId(id));

	let board = $state.raw<LeaderboardResponse | null>(null);
	let stat = $state.raw<StatisticBoardResponse | null>(null);
	let status = $state<Status>('loading');

	// A new address starts over: the old board must not show under the new name.
	$effect(() => {
		const current = id;
		const vanilla = statistic;
		let cancelled = false;
		board = null;
		stat = null;
		status = 'loading';
		const request = vanilla ? getStatistic(vanilla.group, vanilla.id) : getLeaderboard(current);
		request
			.then((answer) => {
				if (cancelled) {
					return;
				}
				if (vanilla) {
					stat = answer as StatisticBoardResponse;
				} else {
					board = answer as LeaderboardResponse;
				}
				status = 'ready';
			})
			.catch((error) => {
				if (!cancelled) {
					status = error instanceof NotFoundError ? 'notFound' : 'failed';
				}
			});
		return () => {
			cancelled = true;
		};
	});

	const unit = $derived(stat?.unit ?? board?.unit ?? '');
	const entries = $derived<LeaderboardEntry[]>(stat?.entries ?? board?.entries ?? []);
	const coin = $derived(!statistic && id === 'legends');

	const name = $derived(
		statistic
			? vanillaLabel(statistic.group, statistic.id)
			: tOptional(`boards.${id}.name`, board ? id : t('boards.board.fallbackTitle'))
	);
	const icon = $derived(statistic ? vanillaIcon(statistic.group, statistic.id) : (board?.icon ?? ''));

	const category = $derived(
		statistic
			? tOptional(`stats.groups.${statistic.group}.name`, statistic.group)
			: board
				? tOptional(`boards.categories.${board.category}`, board.category)
				: ''
	);

	const hint = $derived(
		statistic ? vanillaHint(statistic.group, statistic.id) : boardHint(id, unit || undefined)
	);
	const about = $derived((statistic ? '' : tOptional(`boards.${id}.about`, '')) || hint);

	// What a combined board adds up, per group of the game's statistics, in the game's order of groups.
	const parts = $derived.by(() => {
		const given = board?.parts;
		if (!given) {
			return [];
		}
		return vanillaGroups
			.filter((group) => (given[group]?.length ?? 0) > 0)
			.map((group) => ({
				group,
				name: tOptional(`stats.groups.${group}.name`, group),
				items: (given[group] ?? []).map((part) => vanillaLabel(group, part)).join(', ')
			}));
	});

	function display(entry: LeaderboardEntry): string {
		return formatValue(entry.value, unit || 'count');
	}
</script>

<svelte:head>
	<title>{pageTitle(name)}</title>
</svelte:head>

<BackNav />
<svelte:element this={pageTitleTag()} class="page-title">
	{#if icon}<ItemIcon id={icon} />{/if}
	<span>{name}</span>
</svelte:element>
{#if category}
	<p class="lead">
		{category}{#if unit}{' · '}<Tooltip text={unitText(unit, 'hint', '')} placement="bottom" focusable
				>{unitText(unit, 'name', unit)}</Tooltip
			>{/if}
	</p>
{/if}
{#if about}
	<p class="about">{about}</p>
{/if}
{#if parts.length > 0}
	<div class="parts">
		{#each parts as part (part.group)}
			<p><span class="group">{part.name}:</span> {part.items}</p>
		{/each}
	</div>
{/if}

{#if status === 'failed'}
	<p class="message">{t('site.loadError')}</p>
{:else if status === 'notFound'}
	<p class="message">{t('boards.board.notFound')}</p>
{:else if status === 'loading'}
	<Loader />
{:else if entries.length === 0}
	<p class="message">{t('boards.board.empty')}</p>
{:else}
	<Podium {entries} {display} {coin} {hint} />
	{#key id}
		<RankedList {entries} value={display} {coin} {hint} />
	{/key}
{/if}

<style>
	.page-title {
		display: flex;
		gap: 0.55rem;
		align-items: center;
		justify-content: center;
		margin: 0;
		font-size: 2rem;
		text-align: center;
	}

	.about {
		max-width: 40rem;
		margin: 0.3rem auto 0;
		color: var(--color-muted);
		font-size: 0.875rem;
		text-align: center;
	}

	.parts {
		max-width: 40rem;
		margin: 0.4rem auto 0;
		color: var(--color-muted);
		font-size: 0.75rem;
		line-height: 1.4;
		text-align: center;
	}

	.parts p {
		margin: 0.15rem 0;
	}

	.group {
		color: var(--color-text);
	}
</style>
