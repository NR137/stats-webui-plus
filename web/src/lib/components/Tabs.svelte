<script lang="ts" module>
	export type TabItem = { id: string; label: string; href?: string };
</script>

<script lang="ts">
	let {
		tabs,
		selected = $bindable(),
		label
	}: {
		tabs: TabItem[];
		selected: string;
		label: string;
	} = $props();
</script>

<div class="tabs" class:many={tabs.length > 2} role="tablist" aria-label={label}>
	{#each tabs as tab (tab.id)}
		{#if tab.href}
			<!-- Links: the URL decides the tab; the page stays where it is and so does the focus. -->
			<a
				class="mc-btn tab"
				class:lit={tab.id === selected}
				role="tab"
				aria-selected={tab.id === selected}
				href={tab.href}
				data-sveltekit-reset="false">{tab.label}</a
			>
		{:else}
			<button
				type="button"
				class="mc-btn tab"
				class:lit={tab.id === selected}
				role="tab"
				aria-selected={tab.id === selected}
				onclick={() => (selected = tab.id)}>{tab.label}</button
			>
		{/if}
	{/each}
</div>

<style>
	.tabs {
		display: grid;
		grid-auto-columns: 1fr;
		grid-auto-flow: column;
		gap: 0.45rem;
		margin-bottom: 1.15rem;
	}

	.tab {
		width: 100%;
		min-height: 2.15rem;
		padding: 0.28rem 0.55rem;
	}

	@media (max-width: 520px) {
		.tabs.many {
			grid-auto-flow: row;
			grid-template-columns: 1fr;
		}
	}
</style>
