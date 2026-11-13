<script lang="ts">
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import type { PlayerDetail } from '#lib/types.ts';
	import CompareRow from './CompareRow.svelte';
	import { winner, type CompareRowData } from './compare';

	let {
		a,
		b,
		skills,
		shown
	}: {
		a: PlayerDetail;
		b: PlayerDetail;
		// Every skill compared: the verdict counts them all, also while a search shows only some.
		skills: CompareRowData[];
		shown: CompareRowData[];
	} = $props();

	const winsA = $derived(skills.filter((row) => winner(row.a, row.b) === 'a').length);
	const winsB = $derived(skills.filter((row) => winner(row.a, row.b) === 'b').length);
	const verdict = $derived.by(() => {
		if (winsA !== winsB) {
			return t('compare.verdict', {
				name: winsA > winsB ? a.name : b.name,
				wins: Math.max(winsA, winsB),
				total: skills.length
			});
		}
		if (a.level === b.level) {
			return t('compare.verdictEven');
		}
		return t('compare.verdictLevel', { wins: winsA, name: a.level > b.level ? a.name : b.name });
	});
</script>

<p class="section">{t('player.skills')}</p>
<p class="verdict panel">
	<span class="score">{winsA} : {winsB}</span>
	<span>{verdict}</span>
</p>
{#each shown as row (row.id)}
	<CompareRow {row} />
{/each}

<style>
	.section {
		margin: 1.1rem 0 0.45rem;
		color: var(--color-gold);
	}

	.verdict {
		display: flex;
		flex-wrap: wrap;
		gap: 0.2rem 0.9rem;
		justify-content: center;
		align-items: baseline;
		margin: 0 0 0.45rem;
		padding: 0.6rem 0.9rem;
		color: var(--color-fg);
		text-align: center;
	}

	.score {
		color: var(--color-gold);
		font-size: 1.6rem;
		font-variant-numeric: tabular-nums;
	}
</style>
