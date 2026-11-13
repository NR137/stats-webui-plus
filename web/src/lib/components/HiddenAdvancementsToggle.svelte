<script lang="ts">
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { hiddenAdvancements, setHideHidden } from '#lib/hiddenAdvancements.svelte.ts';
	import { t } from '#lib/i18n/i18n.svelte.ts';
</script>

<Tooltip text={t('advancements.hideHiddenHint')} align="end">
	<label class="toggle">
		<input
			type="checkbox"
			checked={hiddenAdvancements.hide}
			onchange={(event) => setHideHidden(event.currentTarget.checked)}
		/>
		{t('advancements.hideHidden')}
	</label>
</Tooltip>

<style>
	.toggle {
		display: inline-flex;
		gap: 0.4rem;
		align-items: center;
		color: var(--color-muted);
		font-size: 0.75rem;
		cursor: pointer;
	}
</style>
