<script lang="ts">
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import { getStats } from '#lib/api.ts';
	import Loader from '#lib/components/Loader.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import SearchField from '#lib/components/SearchField.svelte';
	import TabBar from '#lib/components/TabBar.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { formatValue } from '#lib/format.ts';
	import { i18n, pageTitle, t, tOptional } from '#lib/i18n/i18n.svelte.ts';
	import { cappedResults, deferredValue, GrowingLimit } from '#lib/search.svelte.ts';
	import {
		matchesVanillaQuery,
		sortVanillaIds,
		vanillaBoardHref,
		vanillaGroupIcons,
		vanillaGroups,
		vanillaHint,
		vanillaIcon,
		vanillaLabel,
		type VanillaGroup
	} from '#lib/vanillaStats.ts';
	import type { StatisticSummary } from '#lib/types.ts';
	import type { Snapshot } from './$types';

	// Every statistic Minecraft keeps, in the game's groups (General, Mobs killed, ...), one group at a time; each tile
	// leads to that statistic's leaderboard. Searching looks through all groups. Like a player's statistics: the ones
	// nobody on the server has yet (all at 0) are hidden unless asked, and then come last, faint.
	let query = $state('');
	let selected = $state<VanillaGroup>('custom');
	// Raw: replaced as a whole when loaded, never changed in place.
	let vanilla = $state.raw<Partial<Record<VanillaGroup, string[]>>>({});
	// Per group, each statistic by ID: its unit, and its leader where anybody has it.
	let top = $state.raw<Partial<Record<VanillaGroup, Record<string, StatisticSummary>>>>({});
	let hideZeros = $state(true);
	let ready = $state(false);
	let failed = $state(false);
	const filterQuery = deferredValue(() => query);
	const searching = $derived(filterQuery.current.trim().length > 0);

	// Back from a leaderboard, the same group and search again.
	export const snapshot: Snapshot<{ query: string; selected: VanillaGroup; hideZeros: boolean }> = {
		capture: () => ({ query, selected, hideZeros }),
		restore: (value) => {
			query = value.query;
			selected = value.selected;
			hideZeros = value.hideZeros;
		}
	};

	function isDone(group: VanillaGroup, id: string) {
		return Boolean(top[group]?.[id]?.leader);
	}

	const groups = $derived(
		vanillaGroups.map((group) => {
			void i18n.locale;
			const all = vanilla[group] ?? [];
			const found = sortVanillaIds(
				group,
				all.filter((id) => matchesVanillaQuery(group, id, filterQuery.current))
			);
			// Done ones first; the zeros after them, or left out (not while searching, as on a player's page).
			const zeros = found.filter((id) => !isDone(group, id));
			const matched = [
				...found.filter((id) => isDone(group, id)),
				...(hideZeros && !searching ? [] : zeros)
			];
			return {
				group,
				total: all.length,
				done: all.filter((id) => isDone(group, id)).length,
				matched: matched.length,
				ids: cappedResults(matched, searching)
			};
		})
	);
	// One group, or while searching every group with a match.
	const shown = $derived(
		searching ? groups.filter((entry) => entry.matched > 0) : groups.filter((entry) => entry.group === selected)
	);

	$effect(() => {
		load();
	});

	async function load() {
		ready = false;
		failed = false;
		const next: Partial<Record<VanillaGroup, string[]>> = {};
		const nextTop: Partial<Record<VanillaGroup, Record<string, StatisticSummary>>> = {};
		try {
			const data = await getStats();
			for (const group of vanillaGroups) {
				const stats = data.groups[group] ?? [];
				next[group] = stats.map((stat) => stat.id);
				nextTop[group] = Object.fromEntries(stats.map((stat) => [stat.id, stat]));
			}
			vanilla = next;
			top = nextTop;
		} catch {
			vanilla = {};
			top = {};
			failed = true;
		} finally {
			ready = true;
		}
	}
	// Long groups (1,600 items) build their tiles over a few frames instead of all at once.
	const grow = new GrowingLimit(() => [shown, hideZeros]);
</script>

<svelte:head>
	<title>{pageTitle(t('menu.stats'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('stats.heading')}</svelte:element>
<p class="lead">{t('stats.lead')}</p>

<div class="filters">
	<SearchField bind:value={query} placeholder={t('player.search')} />
</div>

{#if !ready}
	<Loader />
{:else if failed}
	<p class="empty">{t('site.loadError')}</p>
{:else}
	<div class="tools">
		<label class="toggle">
			<input type="checkbox" bind:checked={hideZeros} />
			{t('player.hideZeros')}
		</label>
	</div>
	{#if !searching}
		<TabBar
			label={t('stats.groupTabs')}
			namesOnPhones
			tabs={groups.map((entry) => ({
				id: entry.group,
				title: tOptional(`stats.groups.${entry.group}.name`, entry.group),
				icon: vanillaGroupIcons[entry.group],
				short: tOptional(`stats.groups.${entry.group}.short`, entry.group),
				count: `${entry.done}/${entry.total}`
			}))}
			bind:selected={() => selected, (id) => (selected = id as VanillaGroup)}
		/>
	{/if}
	{#each shown as entry (entry.group + i18n.locale)}
		<section class="pack">
			{#if searching}
				<p class="pack-title">
					<ItemIcon id={vanillaGroupIcons[entry.group]} compact />
					{tOptional(`stats.groups.${entry.group}.name`, entry.group)}
					<span class="count">{t('player.shown', { shown: entry.ids.length, total: entry.total })}</span>
				</p>
			{/if}
			<div class="tiles">
				{#each entry.ids.slice(0, grow.value) as id (id)}
					{@const stat = top[entry.group]?.[id]}
					{@const leader = stat?.leader}
					<div class="cell">
						<Tooltip text={vanillaHint(entry.group, id)} fill>
							<a class="tile slot" class:zero={!isDone(entry.group, id)} href={vanillaBoardHref(entry.group, id)}>
								<span class="glyph">
									<ItemIcon id={vanillaIcon(entry.group, id)} />
								</span>
								<span class="label">{vanillaLabel(entry.group, id)}</span>
								{#if leader}
									<!-- Extra info, small: who leads it and with what (the first place of its leaderboard). -->
									<span class="leader">
										<span class="who">{leader.name}</span>
										<span class="best">{formatValue(leader.value, stat?.unit ?? 'count')}</span>
									</span>
								{/if}
							</a>
						</Tooltip>
					</div>
				{/each}
			</div>
		</section>
	{:else}
		<p class="empty">{t('player.noMatches')}</p>
	{/each}
{/if}

<style>
	.page-title {
		margin: 0 0 0.4rem;
		/* Same look whether this is the page's h1 or an h2 below the site heading. */
		font-size: 2rem;
		text-align: center;
	}

	.filters {
		margin: 0.9rem 0 1.1rem;
	}

	.pack {
		margin-top: 0.55rem;
	}

	.tools {
		display: flex;
		justify-content: flex-end;
		margin: 0 0 0.6rem;
	}

	.toggle {
		display: inline-flex;
		gap: 0.4rem;
		align-items: center;
		color: var(--color-muted);
		font-size: 0.75rem;
		cursor: pointer;
	}

	/* Nobody has it yet: faint, as a player's zeros. */
	.tile.zero {
		opacity: 0.42;
	}

	.pack-title {
		display: flex;
		gap: 0.45rem;
		align-items: center;
		margin: 1rem 0 0.6rem;
		color: var(--color-gold);
	}

	.count {
		margin-left: 0.45rem;
		color: var(--color-muted);
		font-size: 0.75rem;
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
		gap: 0.45rem;
	}

	/* The tile fills its cell, with the tooltip (the statistic's description) around it, as on advancement tiles. */
	.cell {
		width: 100%;
		height: 100%;
	}

	.tile {
		min-height: 7.2rem;
	}

	.glyph {
		display: grid;
		place-items: center;
		width: 2.6rem;
		height: 2.6rem;
		margin-bottom: 0.15rem;
	}

	.label {
		color: var(--color-muted);
		font-size: 0.875rem;
		line-height: 1.25;
	}

	/* Leader and value, both small, always on two lines at the tile's bottom, so they line up along a row. */
	.leader {
		display: flex;
		flex-direction: column;
		align-items: center;
		max-width: 100%;
		margin-top: auto;
		font-size: 0.75rem;
		line-height: 1.25;
	}

	.who {
		max-width: 100%;
		overflow: hidden;
		color: var(--color-text);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.best {
		color: var(--color-gold);
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}

	.empty {
		color: var(--color-muted);
		text-align: center;
	}
</style>
