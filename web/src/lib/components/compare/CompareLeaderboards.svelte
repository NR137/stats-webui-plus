<script lang="ts">
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import CompareRow from './CompareRow.svelte';
	import type { LeaderboardSection } from './compare';

	let { sections }: { sections: LeaderboardSection[] } = $props();
</script>

<p class="section">{t('menu.leaderboards')}</p>
{#each sections as section (section.id)}
	<p class="section sub">{section.name}</p>
	{#each section.rows as row (row.id)}
		<CompareRow {row} />
	{/each}
{/each}

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
</style>
