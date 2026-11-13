<script lang="ts">
	import { advancementTabTitle } from '#lib/advancements.ts';
	import AdvancementTiles from '#lib/components/AdvancementTiles.svelte';
	import HiddenAdvancementsToggle from '#lib/components/HiddenAdvancementsToggle.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import TabBar from '#lib/components/TabBar.svelte';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import type { AdvancementTab } from '#lib/types.ts';
	import { advancementGroups, type AdvancementGroup, type UniqueAdvancement } from './compare';

	let {
		nameA,
		nameB,
		tabs,
		a,
		b,
		searching
	}: {
		nameA: string;
		nameB: string;
		// The catalog's tabs, in the game's order.
		tabs: AdvancementTab[];
		// Each side's unique advancements that match the search.
		a: UniqueAdvancement[];
		b: UniqueAdvancement[];
		searching: boolean;
	} = $props();

	let chosen = $state<string | null>(null);

	const groups = $derived(advancementGroups(tabs, (tab) => advancementTabTitle(tab.id, tab.title), a, b));
	const hasAny = (group: AdvancementGroup) => group.a.length > 0 || group.b.length > 0;
	// The chosen tab while it still exists, else the first with any.
	const selected = $derived(
		groups.some((group) => group.id === chosen) ? (chosen ?? '') : (groups.find(hasAny)?.id ?? groups[0]?.id ?? '')
	);
	const tabBar = $derived(
		groups.map((group) => ({
			id: group.id,
			title: group.title,
			icon: group.icon,
			count: `${group.a.length} : ${group.b.length}`
		}))
	);
	const shown = $derived(searching ? groups.filter(hasAny) : groups.filter((group) => group.id === selected));
</script>

{#snippet column(name: string, items: UniqueAdvancement[], background: string)}
	<div class="column">
		<p class="section sub own">
			{t('compare.onlyAdvancements', { name })}{' '}<span class="count">{items.length}</span>
		</p>
		{#if items.length > 0}
			<AdvancementTiles {items} {background} />
		{:else}
			<p class="muted none">{t('compare.onlyAdvancementsEmpty')}</p>
		{/if}
	</div>
{/snippet}

<div class="head">
	<p class="section">
		{t('compare.advancementsDiff')}{' '}<span class="count">{a.length} : {b.length}</span>
	</p>
	<HiddenAdvancementsToggle />
</div>
{#if !searching}
	<TabBar
		tabs={tabBar}
		bind:selected={() => selected, (id) => (chosen = id)}
		label={t('compare.advancementsDiff')}
		namesOnPhones
	/>
{/if}
<!-- Whose side is whose, once; on phones the columns are one below the other and each says it itself. -->
<div class="sides">
	<p class="section sub">{t('compare.onlyAdvancements', { name: nameA })}</p>
	<p class="section sub">{t('compare.onlyAdvancements', { name: nameB })}</p>
</div>
{#each shown as group (group.id)}
	{#if searching}
		<p class="tab-title"><ItemIcon id={group.icon} compact />{group.title}</p>
	{/if}
	<div class="group">
		{@render column(nameA, group.a, group.background)}
		{@render column(nameB, group.b, group.background)}
	</div>
{/each}

<style>
	.head {
		display: flex;
		flex-wrap: wrap;
		gap: 0 1rem;
		justify-content: space-between;
		align-items: baseline;
		margin: 1.1rem 0 0.45rem;
	}

	.section {
		margin: 0;
		color: var(--color-gold);
	}

	.sub {
		margin: 0.85rem 0 0.4rem;
		color: var(--color-muted);
		font-size: 0.95rem;
	}

	.count {
		margin-left: 0.45rem;
		color: var(--color-muted);
	}

	.sides,
	.group {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.7rem;
	}

	.sides .sub {
		margin-top: 0;
	}

	.tab-title {
		display: flex;
		gap: 0.45rem;
		align-items: center;
		margin: 1rem 0 0.45rem;
		color: var(--color-gold);
	}

	.column {
		min-width: 0;
	}

	/* Room for two lines of title in every tile, so a long title doesn't make its tile taller than the one beside it
	   on the other side, and the rows of both sides line up. */
	.column :global(.tile .label) {
		min-height: 2.5em;
	}

	.own {
		display: none;
	}

	.muted {
		color: var(--color-muted);
	}

	.none {
		margin: 0.2rem 0 0;
		text-align: left;
	}

	@media (max-width: 700px) {
		.sides {
			display: none;
		}

		.group {
			grid-template-columns: 1fr;
		}

		.own {
			display: block;
		}

		.column:first-child .own {
			margin-top: 0;
		}
	}
</style>
