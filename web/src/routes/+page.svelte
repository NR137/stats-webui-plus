<script lang="ts">
	import { branding } from '#lib/branding.svelte.ts';
	import CustomPageView from '#lib/components/CustomPageView.svelte';
	import Loader from '#lib/components/Loader.svelte';
	import RankingPage from '#lib/components/RankingPage.svelte';
</script>

<!-- The start page: the server owner's own page (config startPage), else the ranking. Which one is known once the
     server's settings are in. -->
{#if !branding.loaded}
	<Loader />
{:else if branding.startPage}
	<CustomPageView id={branding.startPage} />
{:else}
	<RankingPage />
{/if}
