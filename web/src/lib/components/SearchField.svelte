<script lang="ts">
	import { onMount } from 'svelte';

	let {
		value = $bindable(''),
		placeholder,
		label,
		autofocus = false,
		onkeydown
	}: {
		value?: string;
		placeholder: string;
		label?: string;
		autofocus?: boolean;
		onkeydown?: (event: KeyboardEvent) => void;
	} = $props();

	let input = $state<HTMLInputElement>();

	// Not the autofocus attribute: browsers only honour it on page load, not in a menu that opens later.
	onMount(() => {
		if (autofocus) {
			input?.focus();
		}
	});
</script>

<input
	class="search"
	type="search"
	bind:this={input}
	bind:value
	{placeholder}
	aria-label={label ?? placeholder}
	autocomplete="off"
	spellcheck="false"
	{onkeydown}
/>

<style>
	/* A slot of the game's inventory. */
	.search {
		width: 100%;
		min-height: 2.5rem;
		padding: 0.35rem 0.8rem;
		border: 2px solid var(--color-border);
		border-radius: 0;
		outline: none;
		box-shadow: inset 2px 2px 0 var(--button-shadow);
		background: var(--slot-bg);
		color: inherit;
		font: inherit;
		appearance: none;
	}

	.search:focus-visible {
		border-color: var(--button-border-hover);
	}
</style>
