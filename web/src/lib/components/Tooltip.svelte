<script lang="ts">
	import { onDestroy, type Snippet } from 'svelte';

	let {
		text,
		align = 'center',
		placement = 'top',
		focusable = false,
		fill = false,
		children
	}: {
		text: string;
		align?: 'start' | 'center' | 'end';
		placement?: 'top' | 'bottom';
		focusable?: boolean;
		// Fill the parent (a tile's grid cell), so the whole of it is what it holds, e.g. a link.
		fill?: boolean;
		children: Snippet;
	} = $props();

	// How far a bubble stays from the page's edges.
	const EDGE = 8;

	const uid = $props.id();
	let wrapper = $state<HTMLElement>();
	let bubble = $state<HTMLElement>();
	let shift = $state(0);
	let observer: ResizeObserver | null = null;

	function isOpen(node: HTMLElement) {
		return getComputedStyle(node).display !== 'none';
	}

	/** The bubble's width; a closed one is measured fixed and invisible, so it never widens or moves the page. */
	function bubbleWidth(node: HTMLElement): number {
		if (isOpen(node)) {
			return node.offsetWidth;
		}
		const { style } = node;
		style.setProperty('display', 'block');
		style.setProperty('position', 'fixed');
		style.setProperty('visibility', 'hidden');
		style.setProperty('left', '0');
		style.setProperty('top', '0');
		const width = node.offsetWidth;
		for (const property of ['display', 'position', 'visibility', 'left', 'top']) {
			style.removeProperty(property);
		}
		return width;
	}

	// Where the bubble would be, worked out from the wrapper (its transform is our own shift, so its rect isn't used).
	function fit() {
		const node = bubble;
		const anchor = wrapper;
		if (!node || !anchor) {
			return;
		}
		const width = bubbleWidth(node);
		const box = anchor.getBoundingClientRect();
		const left =
			align === 'start' ? box.left : align === 'end' ? box.right - width : box.left + box.width / 2 - width / 2;
		// The page's own width, without the scrollbar. Not clientWidth: with the root's scrollbar-gutter (base.css)
		// Chromium counts the scrollbar in it.
		const pageWidth = document.documentElement.getBoundingClientRect().width;
		let next = 0;
		if (left + width > pageWidth - EDGE) {
			next = pageWidth - EDGE - (left + width);
		}
		// The start edge wins when the bubble is wider than the room there is: its text begins there.
		if (left + next < EDGE) {
			next = EDGE - left;
		}
		shift = next;
	}

	function prepare() {
		fit();
		// Only bubbles that have been opened are watched: a list has up to 1,600 of them, and most are never opened.
		if (!observer && bubble && typeof ResizeObserver !== 'undefined') {
			const node = bubble;
			observer = new ResizeObserver(() => {
				// A text that changes while shown (a language switch, a live value) may no longer fit where it was.
				if (isOpen(node)) {
					fit();
				}
			});
			observer.observe(node);
		}
	}

	onDestroy(() => observer?.disconnect());
</script>

{#if !text}
	{@render children()}
{:else}
	<!-- Fitted before it opens (pointer down for touch, enter for mice, focus for keyboards), so it never shows cut off
	     at the window's edge first. -->
	<span
		class="wrap"
		class:fill
		role="presentation"
		bind:this={wrapper}
		onpointerdown={prepare}
		onpointerenter={prepare}
		onfocusin={prepare}
	>
		{#if focusable}
			<!-- A button, so keyboards and touch screens reach the text too. -->
			<button type="button" class="hit focusable" aria-describedby={uid}>{@render children()}</button>
		{:else}
			<span class="hit">{@render children()}</span>
		{/if}
		<span
			id={uid}
			class="bubble {align} {placement}"
			role="tooltip"
			bind:this={bubble}
			style:--tooltip-shift="{shift}px">{text}</span
		>
	</span>
{/if}

<style>
	.wrap {
		position: relative;
		display: inline-flex;
		max-width: 100%;
	}

	.wrap.fill,
	.wrap.fill > .hit {
		width: 100%;
		height: 100%;
		cursor: pointer;
	}

	.wrap:hover,
	.wrap:focus-within {
		z-index: 60;
	}

	/* Looks like what it holds: none of the button's own look. */
	.hit,
	.hit:hover,
	.hit:focus-visible {
		display: inline-flex;
		gap: 0;
		align-items: center;
		min-width: 0;
		min-height: 0;
		max-width: 100%;
		margin: 0;
		padding: 0;
		border: 0;
		border-radius: 0;
		box-shadow: none;
		background: none;
		color: inherit;
		font: inherit;
		text-align: inherit;
		text-shadow: inherit;
		appearance: none;
		cursor: help;
	}

	/* The underline marks text that can be tapped. It stays on hover and focus: without it the element was 2px lower,
	   the pointer left it and the bubble flickered. */
	.focusable,
	.focusable:hover,
	.focusable:focus-visible {
		border-bottom: 2px dotted color-mix(in srgb, currentColor 55%, transparent);
	}

	.hit:focus-visible {
		outline: 2px solid var(--button-border-hover);
		outline-offset: 2px;
	}

	/* Not just invisible: display none, so the many closed bubbles of a list take no room and never widen the page. */
	.bubble {
		position: absolute;
		z-index: 50;
		display: none;
		width: max-content;
		max-width: min(16.5rem, 70vw);
		padding: 0.4rem 0.55rem;
		border: 2px solid var(--color-border);
		box-shadow: var(--panel-edge);
		background: var(--color-tooltip);
		color: var(--color-fg);
		font-size: 0.85rem;
		line-height: 1.35;
		text-align: left;
		text-shadow: var(--text-shadow);
		transform: translateX(var(--tooltip-shift, 0px));
		pointer-events: none;
	}

	.wrap:hover > .bubble,
	.wrap:focus-within > .bubble {
		display: block;
	}

	.top {
		bottom: calc(100% + 0.4rem);
	}

	.bottom {
		top: calc(100% + 0.4rem);
	}

	.start {
		left: 0;
	}

	.end {
		right: 0;
	}

	.center {
		left: 50%;
		transform: translateX(calc(-50% + var(--tooltip-shift, 0px)));
	}
</style>
