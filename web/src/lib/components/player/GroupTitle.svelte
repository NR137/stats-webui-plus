<script lang="ts">
	import ItemIcon from '#lib/components/ItemIcon.svelte';

	// While searching, the name of each tab or group above its matches.
	let { icon, title, count }: { icon: string; title: string; count: string } = $props();
</script>

<p class="title">
	<ItemIcon id={icon} compact />
	{title}
	<span class="count">{count}</span>
</p>

<style>
	.title {
		display: inline-flex;
		gap: 0.45rem;
		align-items: center;
		font-size: 1rem;
	}

	.count {
		margin-left: 0.45rem;
		color: var(--color-muted);
		font-size: 0.75rem;
	}
</style>
