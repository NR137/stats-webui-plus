<script lang="ts">
	import HeaderSelect, { type HeaderSelectOption } from '#lib/components/HeaderSelect.svelte';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import { setThemePreference, theme } from '#lib/theme/theme.svelte.ts';
	import { isThemePreference, themes } from '#lib/theme/themes.ts';

	let { compact = false }: { compact?: boolean } = $props();

	const options = $derived<HeaderSelectOption[]>([
		{ id: 'system', label: t('settings.theme.system'), icon: 'monitor' },
		...themes.map((entry) => ({ id: entry.id, label: t(entry.label), icon: entry.icon }))
	]);
</script>

<HeaderSelect
	id="theme"
	label={t('settings.theme.label')}
	value={theme.preference}
	{options}
	showLabel
	{compact}
	onselect={(id) => {
		if (isThemePreference(id)) {
			setThemePreference(id);
		}
	}}
/>
