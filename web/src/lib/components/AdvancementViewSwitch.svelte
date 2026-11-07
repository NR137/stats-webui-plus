<script lang="ts">
	import { advancementView, setAdvancementView, type AdvancementView } from '$lib/advancementView.svelte';
	import { t } from '$lib/i18n/i18n.svelte';

	const modes: { id: AdvancementView; label: () => string }[] = [
		{ id: 'tiles', label: () => t('advancements.viewTiles') },
		{ id: 'tree', label: () => t('advancements.viewTree') }
	];
</script>

<div class="switch" role="group" aria-label={t('advancements.view')}>
	{#each modes as mode (mode.id)}
		<button
			type="button"
			class="mc-btn"
			class:on={advancementView.mode === mode.id}
			aria-pressed={advancementView.mode === mode.id}
			onclick={() => setAdvancementView(mode.id)}>{mode.label()}</button
		>
	{/each}
</div>

<style>
	.switch {
		display: inline-flex;
		gap: 0.3rem;
	}

	.switch .mc-btn {
		min-height: 2rem;
		padding: 0.15rem 0.8rem;
		font-size: 0.85rem;
	}

	/* The chosen one looks like the selected tab (Stats / Advancements) in both themes. */
	.switch .mc-btn.on {
		color: #ffffa0;
		background: var(--button-face-hover);
		border-color: var(--button-border-hover);
		box-shadow:
			inset 2px 2px 0 var(--button-highlight-hover),
			inset -2px -2px 0 var(--button-shadow-hover);
	}
</style>
