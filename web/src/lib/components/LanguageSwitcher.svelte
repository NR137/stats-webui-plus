<script lang="ts">
	import HeaderSelect, { type HeaderSelectOption } from '#lib/components/HeaderSelect.svelte';
	import { i18n, localeSettings, nameComparer, setLocale, t } from '#lib/i18n/i18n.svelte.ts';
	import { isLocale } from '#lib/i18n/locales.ts';

	let { compact = false }: { compact?: boolean } = $props();

	// Each language in its own words, so a visitor finds theirs whatever the page is in.
	const options = $derived.by<HeaderSelectOption[]>(() => {
		const compare = nameComparer();
		return localeSettings.enabled
			.map((code) => ({ id: code, label: t(`settings.language.${code}` as const), icon: code }))
			.sort((a, b) => compare(a.label, b.label));
	});
</script>

{#if options.length > 1}
	<HeaderSelect
		id="language"
		label={t('settings.language.label')}
		value={i18n.locale}
		{options}
		{compact}
		onselect={(id) => {
			if (isLocale(id)) {
				setLocale(id);
			}
		}}
	/>
{/if}
