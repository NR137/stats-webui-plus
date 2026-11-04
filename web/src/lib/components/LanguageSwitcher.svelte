<script lang="ts">
	import HeaderSelect from '$lib/components/HeaderSelect.svelte';
	import type { IconName } from '$lib/components/iconNames';
	import { i18n, localeSettings, setLocale, t, type Locale, type MessageKey } from '$lib/i18n/i18n.svelte';

	const localeKeys: Record<Locale, MessageKey> = {
		es: 'language.es',
		en: 'language.en',
		fr: 'language.fr',
		de: 'language.de'
	};

	const localeIcons: Record<Locale, IconName> = {
		es: 'es',
		en: 'en',
		fr: 'fr',
		de: 'de'
	};

	// Enabled languages only, alphabetically by their own name (Deutsch, English, Español, Français).
	const options = $derived(
		localeSettings.enabled
			.map((locale) => ({
				id: locale,
				label: t(localeKeys[locale]),
				icon: localeIcons[locale]
			}))
			.sort((a, b) => a.label.localeCompare(b.label))
	);
</script>

{#if options.length > 1}
	<HeaderSelect
		id="language"
		label={t('language.label')}
		value={i18n.locale}
		{options}
		onselect={(next) => setLocale(next as Locale)}
	/>
{/if}
