<script lang="ts">
	import { page } from '$app/state';
	import CustomPageView from '#lib/components/CustomPageView.svelte';
</script>

<CustomPageView id={page.params.id ?? ''} />
