<script lang="ts" module>
	import type { IconName } from '#lib/components/iconNames.ts';

	export type HeaderSelectOption = { id: string; label: string; icon: IconName };
</script>

<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { closeMenu, headerMenu, registerMenuRoot, toggleMenu } from '#lib/components/headerMenu.svelte.ts';

	let {
		id,
		label,
		value,
		options,
		onselect,
		showLabel = false,
		compact = false
	}: {
		id: string;
		label: string;
		value: string;
		options: HeaderSelectOption[];
		onselect: (id: string) => void;
		showLabel?: boolean;
		compact?: boolean;
	} = $props();

	const open = $derived(headerMenu.open === id);
	const current = $derived(options.find((option) => option.id === value) ?? options[0]);
	const summary = $derived(`${label}: ${current?.label ?? ''}`);

	function choose(option: string) {
		onselect(option);
		closeMenu();
	}
</script>

<div class="wrap" class:compact use:registerMenuRoot>
	<button
		type="button"
		class="mc-btn trigger"
		aria-haspopup="listbox"
		aria-expanded={open}
		aria-label={summary}
		title={compact ? summary : undefined}
		onclick={() => toggleMenu(id)}
	>
		{#if current}
			<Icon name={current.icon} />
		{/if}
		{#if !compact}
			<span class="text">{showLabel ? summary : current?.label}</span>
		{/if}
		<span class="chevron" class:up={open}><Icon name="chevron" /></span>
	</button>
	{#if open}
		<ul class="list panel" role="listbox" aria-label={label}>
			{#each options as option (option.id)}
				<li role="none">
					<button
						type="button"
						class="option"
						role="option"
						aria-selected={option.id === current?.id}
						onclick={() => choose(option.id)}
					>
						<Icon name={option.icon} />
						<span>{option.label}</span>
					</button>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.wrap {
		position: relative;
		width: 100%;
	}

	.trigger {
		position: relative;
		width: 100%;
	}

	.chevron {
		position: absolute;
		top: 50%;
		right: 0.8rem;
		display: flex;
		transform: translateY(-50%);
	}

	.chevron.up :global(.glyph) {
		transform: rotate(180deg);
	}

	.list {
		position: absolute;
		top: calc(100% + 0.35rem);
		left: 0;
		right: 0;
		z-index: 30;
		margin: 0;
		padding: 0.2rem;
		border: 2px solid var(--color-border);
		/* Opaque like tooltips: the list lies over the page, whose background would show through a panel's colour. */
		background: var(--color-tooltip);
		list-style: none;
	}

	.option {
		justify-content: center;
		width: 100%;
		min-height: 2.2rem;
	}

	.list li + li {
		margin-top: 0.2rem;
	}


	/* In the header's row of small buttons: a square with the icon and the chevron, its list opening leftwards
	   so it stays on screen at the right end of the row. */
	.compact {
		width: auto;
	}

	.compact .trigger {
		gap: 0.2rem;
		width: 3.4rem;
	}

	.compact .chevron {
		position: static;
		transform: none;
	}

	.compact .list {
		left: auto;
		right: 0;
		width: max-content;
		min-width: 11rem;
	}
</style>
