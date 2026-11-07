<script lang="ts">
	import { advancementTabTitle } from '$lib/advancements';
	import McItem from '$lib/components/McItem.svelte';

	// The advancement tabs (Minecraft, Nether, The End, Adventure, Husbandry) as buttons, like the tabs above the game's
	// advancement screen: one is shown at a time. On phones only the icons, so all five fit.
	let {
		tabs,
		selected = $bindable(),
		label
	}: {
		tabs: { id: string; title: string; icon: string; count?: string }[];
		selected: string;
		label: string;
	} = $props();
</script>

<div class="tab-bar" role="tablist" aria-label={label}>
	{#each tabs as tab (tab.id)}
		{@const title = advancementTabTitle(tab.id, tab.title)}
		<button
			type="button"
			role="tab"
			class="mc-btn"
			class:on={selected === tab.id}
			aria-selected={selected === tab.id}
			aria-label={tab.count ? `${title} (${tab.count})` : title}
			title={title}
			onclick={() => (selected = tab.id)}
		>
			<McItem id={tab.icon} compact />
			<span class="text">
				<span class="name">{title}</span>
				{#if tab.count}
					<span class="count">{tab.count}</span>
				{/if}
			</span>
		</button>
	{/each}
</div>
<!-- On phones the buttons show only icons: the chosen tab's name and count under them. -->
{#each tabs.filter((tab) => tab.id === selected) as tab (tab.id)}
	<p class="current">
		{advancementTabTitle(tab.id, tab.title)}
		{#if tab.count}
			<span class="count">· {tab.count}</span>
		{/if}
	</p>
{/each}

<style>
	/* Equal columns, all five in one row (more wrap only with data packs adding tabs). */
	.tab-bar {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(9rem, 100%), 1fr));
		gap: 0.4rem;
		margin: 0 0 0.6rem;
	}

	/* Icon, name and count stacked, so the name has the button's whole width ("Landwirtschaft"). */
	.tab-bar .mc-btn {
		flex-direction: column;
		gap: 0.2rem;
		min-height: 4.6rem;
		padding: 0.4rem 0.4rem 0.35rem;
	}

	/* The chosen tab looks like the selected tab elsewhere (Stats / Advancements). */
	.tab-bar .mc-btn.on {
		color: #ffffa0;
		background: var(--button-face-hover);
		border-color: var(--button-border-hover);
		box-shadow:
			inset 2px 2px 0 var(--button-highlight-hover),
			inset -2px -2px 0 var(--button-shadow-hover);
	}

	.text {
		display: flex;
		flex-direction: column;
		align-items: center;
		line-height: 1.2;
	}

	.count {
		font-size: 0.8rem;
		opacity: 0.8;
		white-space: nowrap;
	}

	.current {
		display: none;
		margin: -0.2rem 0 0.6rem;
		text-align: center;
	}

	@media (max-width: 640px) {
		.tab-bar {
			grid-template-columns: repeat(auto-fit, minmax(3rem, 1fr));
		}

		.tab-bar .mc-btn {
			min-height: 2.8rem;
		}

		.text {
			display: none;
		}

		.current {
			display: block;
		}
	}
</style>
