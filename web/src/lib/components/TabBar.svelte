<script lang="ts">
	import ItemIcon from '#lib/components/ItemIcon.svelte';

	type Tab = { id: string; title: string; icon: string; count?: string; short?: string };

	let {
		tabs,
		selected = $bindable(),
		label,
		namesOnPhones = false
	}: {
		tabs: Tab[];
		selected: string;
		label: string;
		namesOnPhones?: boolean;
	} = $props();

	const current = $derived(tabs.find((tab) => tab.id === selected));
</script>

<div class="bar" class:names={namesOnPhones} role="tablist" aria-label={label}>
	{#each tabs as tab (tab.id)}
		<button
			type="button"
			class="mc-btn tab"
			class:lit={tab.id === selected}
			role="tab"
			aria-selected={tab.id === selected}
			title={tab.title}
			aria-label={tab.count ? `${tab.title} (${tab.count})` : tab.title}
			onclick={() => (selected = tab.id)}
		>
			<span class="picture"><ItemIcon id={tab.icon} compact /></span>
			<span class="text">
				<span class="title">{tab.title}</span>
				{#if namesOnPhones}
					<span class="short">{tab.short ?? tab.title}</span>
				{/if}
				{#if tab.count}
					<span class="count">{tab.count}</span>
				{/if}
			</span>
		</button>
	{/each}
</div>
{#if current && !namesOnPhones}
	<!-- On phones the buttons only show icons; this line names the one shown. -->
	<p class="current">
		{current.title}
		{#if current.count}<span class="count">· {current.count}</span>{/if}
	</p>
{/if}

<style>
	.bar {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(9rem, 100%), 1fr));
		/* Every button as high as the highest, in every row. */
		grid-auto-rows: 1fr;
		gap: 0.4rem;
		margin-bottom: 0.6rem;
	}

	.tab {
		flex-direction: column;
		gap: 0.2rem;
		width: 100%;
		height: 100%;
		min-height: 4.6rem;
		padding: 0.4rem 0.4rem 0.35rem;
	}

	/* Title and count as one block under the icon; the gap is only between the icon and it. */
	.text {
		display: flex;
		flex-direction: column;
		align-items: center;
		line-height: 1.2;
	}

	.picture {
		display: flex;
	}

	.count {
		font-size: 0.8rem;
		white-space: nowrap;
		opacity: 0.8;
	}

	.short,
	.current {
		display: none;
	}

	@media (max-width: 640px) {
		.bar {
			grid-template-columns: repeat(auto-fit, minmax(3rem, 1fr));
		}

		.tab {
			min-height: 2.8rem;
			height: 2.8rem;
		}

		.text {
			display: none;
		}

		.current {
			display: block;
			margin: -0.2rem 0 0.6rem;
			text-align: center;
		}

		/* Statistic groups: their short names fit three to a row, so they need no icons and no line below. */
		.names {
			grid-template-columns: repeat(3, 1fr);
		}

		.names .tab {
			height: 100%;
			padding: 0.3rem 0.25rem;
			font-size: 0.85rem;
			line-height: 1.15;
		}

		.names .picture {
			display: none;
		}

		.names .text {
			display: flex;
			line-height: 1.15;
		}

		.names .title {
			display: none;
		}

		.names .short {
			display: block;
		}
	}
</style>
