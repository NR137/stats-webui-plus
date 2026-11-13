<script lang="ts">
	import { advancementView, setAdvancementView, type AdvancementView } from '#lib/advancementView.svelte.ts';
	import { t } from '#lib/i18n/i18n.svelte.ts';

	const views = $derived<{ mode: AdvancementView; label: string }[]>([
		{ mode: 'tiles', label: t('advancements.viewTiles') },
		{ mode: 'tree', label: t('advancements.viewTree') }
	]);
</script>

<div class="switch" role="group" aria-label={t('advancements.view')}>
	{#each views as view (view.mode)}
		<button
			type="button"
			class="mc-btn"
			class:lit={advancementView.mode === view.mode}
			aria-pressed={advancementView.mode === view.mode}
			onclick={() => setAdvancementView(view.mode)}>{view.label}</button
		>
	{/each}
</div>

<style>
	.switch {
		display: inline-flex;
		gap: 0.3rem;
	}

	.mc-btn {
		min-height: 2rem;
		padding: 0.15rem 0.8rem;
		font-size: 0.85rem;
	}
</style>
