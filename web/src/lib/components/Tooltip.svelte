<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		text,
		align = 'center',
		placement = 'top',
		focusable = false,
		children
	}: {
		text: string;
		align?: 'start' | 'center' | 'end';
		placement?: 'top' | 'bottom';
		focusable?: boolean;
		children: Snippet;
	} = $props();

	// Space kept free between the bubble and the window's edge.
	const EDGE = 8;
	let bubble = $state<HTMLElement | undefined>();

	/* A bubble near the window's edge (an advancement tile at the end of a row, the podium's outer places) would stick
	   out of it, cut off and making the page scroll sideways; it moves along until it fits. */
	function fit() {
		const node = bubble;
		if (!node) {
			return;
		}
		node.style.removeProperty('--shift');
		const box = node.getBoundingClientRect();
		if (!box.width) {
			return;
		}
		// The page's own width: without a scrollbar, or the space kept free for one (scrollbar-gutter), even where the
		// browser hides scrollbars but keeps that space.
		const width = document.documentElement.getBoundingClientRect().width;
		let shift = 0;
		if (box.right > width - EDGE) {
			shift = width - EDGE - box.right;
		}
		if (box.left + shift < EDGE) {
			shift = EDGE - box.left;
		}
		if (shift) {
			node.style.setProperty('--shift', `${Math.round(shift)}px`);
		}
	}

	/* Measured whenever the bubble appears (its size goes from nothing to its own), whatever opened it: the mouse,
	   the keyboard, or a finger that touched it and let go elsewhere, which leaves it open on phones without an event
	   to listen to. Runs after layout and before the bubble is drawn, so it never shows in the wrong place. */
	$effect(() => {
		const node = bubble;
		if (!node) {
			return;
		}
		const observer = new ResizeObserver(fit);
		observer.observe(node);
		return () => observer.disconnect();
	});
</script>

{#if text}
	<span class="wrap {align} {placement}">
		{#if focusable}
			<button type="button" class="hit marked">
				{@render children()}
			</button>
		{:else}
			<span class="hit">
				{@render children()}
			</span>
		{/if}
		<span class="bubble" role="tooltip" bind:this={bubble}>{text}</span>
	</span>
{:else}
	{@render children()}
{/if}

<style>
	.wrap {
		position: relative;
		display: inline-flex;
		align-items: center;
		max-width: 100%;
	}

	.hit {
		display: inline-flex;
		align-items: center;
		max-width: 100%;
		min-height: 0;
		padding: 0;
		color: inherit;
		font: inherit;
		text-align: inherit;
		text-shadow: inherit;
		background: none;
		border: none;
		box-shadow: none;
		cursor: help;
	}

	button.hit:hover,
	button.hit:focus-visible {
		color: inherit;
		background: none;
		border: none;
		box-shadow: none;
	}

	.hit.marked {
		border-bottom: 2px dotted color-mix(in srgb, currentColor 55%, transparent);
	}

	.hit:focus-visible {
		outline: 2px solid var(--button-border-hover);
		outline-offset: 2px;
	}

	.bubble {
		position: absolute;
		z-index: 50;
		width: max-content;
		max-width: min(16.5rem, 70vw);
		padding: 0.4rem 0.55rem;
		color: var(--color-fg);
		font-size: 0.85rem;
		line-height: 1.35;
		text-align: left;
		text-shadow: var(--text-shadow);
		background: var(--color-tooltip);
		border: 2px solid var(--color-border);
		box-shadow: inset 0 0 0 2px var(--button-shadow);
		/* Not just invisible: a hidden bubble that still took up space would widen the page near its edge. */
		display: none;
		pointer-events: none;
		transform: translateX(var(--shift, 0px));
	}

	.top .bubble {
		bottom: calc(100% + 0.4rem);
	}

	.bottom .bubble {
		top: calc(100% + 0.4rem);
	}

	.start .bubble {
		left: 0;
	}

	.center .bubble {
		left: 50%;
		transform: translateX(calc(-50% + var(--shift, 0px)));
	}

	.end .bubble {
		right: 0;
	}

	.wrap:hover,
	.wrap:focus-within {
		z-index: 60;
	}

	.wrap:hover .bubble,
	.wrap:focus-within .bubble {
		display: block;
	}
</style>
