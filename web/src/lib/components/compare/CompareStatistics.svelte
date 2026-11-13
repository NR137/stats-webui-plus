<script lang="ts">
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import TabBar from '#lib/components/TabBar.svelte';
	import { t, type MessageKey } from '#lib/i18n/i18n.svelte.ts';
	import { vanillaGroupIcons, type VanillaGroup } from '#lib/vanillaStats.ts';
	import CompareRow from './CompareRow.svelte';
	import type { StatisticsGroup } from './compare';

	let { groups, searching }: { groups: StatisticsGroup[]; searching: boolean } = $props();

	let selected = $state<string>('custom');

	const name = (group: VanillaGroup) => t(`stats.groups.${group}.name` as MessageKey);
	const tabs = $derived(
		groups.map(({ group, total }) => ({
			id: group,
			title: name(group),
			icon: vanillaGroupIcons[group],
			short: t(`stats.groups.${group}.short` as MessageKey),
			count: String(total)
		}))
	);
	const current = $derived(groups.find((entry) => entry.group === selected));
</script>

<p class="section">{t('compare.vanilla')}</p>
{#if searching}
	{#each groups.filter((entry) => entry.rows.length > 0) as entry (entry.group)}
		<p class="section sub group">
			<ItemIcon id={vanillaGroupIcons[entry.group]} compact />
			{name(entry.group)}
			<span class="count">{t('player.shown', { shown: entry.rows.length, total: entry.total })}</span>
		</p>
		{#each entry.rows as row (row.id)}
			<CompareRow {row} />
		{/each}
	{/each}
{:else}
	<TabBar {tabs} bind:selected label={t('stats.groupTabs')} namesOnPhones />
	{#each current?.rows ?? [] as row (row.id)}
		<CompareRow {row} />
	{:else}
		<p class="muted empty">{t('compare.vanillaEmpty')}</p>
	{/each}
{/if}

<style>
	.section {
		margin: 1.1rem 0 0.45rem;
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

	.muted {
		color: var(--color-muted);
	}

	.empty {
		margin: 0.4rem 0;
		text-align: center;
	}
</style>
