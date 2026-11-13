<script lang="ts">
	import type { Snippet } from 'svelte';

	// The line above a tab's list: a count on the left (or nothing), the switches on the right.
	let { count = '', children }: { count?: string; children: Snippet } = $props();
</script>

<div class="head">
	<span class="count">{count}</span>
	<span class="tools">{@render children()}</span>
</div>

<style>
	.head {
		display: flex;
		flex-wrap: wrap;
		gap: 0.6rem 1rem;
		align-items: baseline;
		justify-content: space-between;
		margin: 0 0 0.7rem;
	}

	.count {
		margin-left: 0.45rem;
		color: var(--color-muted);
		font-size: 0.75rem;
	}

	.tools {
		display: inline-flex;
		flex-wrap: wrap;
		gap: 0.4rem 0.9rem;
		align-items: center;
		justify-content: flex-end;
	}

	/* "Achieved only", "Hide zeros". */
	.tools :global(.check) {
		display: inline-flex;
		gap: 0.4rem;
		align-items: center;
		color: var(--color-muted);
		font-size: 0.75rem;
		cursor: pointer;
	}
</style>
