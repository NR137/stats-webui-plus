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
	let wrap = $state<HTMLElement | undefined>();
	let bubble = $state<HTMLElement | undefined>();

	/* A bubble near the window's edge (an advancement tile at the end of a row, the podium's outer places) would stick
	   out of it, cut off and making the page scroll sideways; it moves along until it fits. Worked out before it opens
	   where possible: laid out at its usual place even for a moment, it would make phones keep the page wider. */
	function fit() {
		const node = bubble;
		const anchor = wrap;
		if (!node || !anchor) {
			return;
		}
		// Its width doesn't depend on where it is (max-content, at most max-width), so a closed bubble is measured
		// fixed to the window, where it never widens the page.
		let width = node.offsetWidth;
		if (!width) {
			node.style.setProperty('display', 'block');
			node.style.setProperty('position', 'fixed');
			node.style.setProperty('visibility', 'hidden');
			width = node.offsetWidth;
			node.style.removeProperty('display');
			node.style.removeProperty('position');
			node.style.removeProperty('visibility');
		}
		if (!width) {
			return;
		}
		const around = anchor.getBoundingClientRect();
		const left =
			align === 'start' ? around.left : align === 'end' ? around.right - width : around.left + around.width / 2 - width / 2;
		// The page's own width: without a scrollbar, or the space kept free for one (scrollbar-gutter), even where the
		// browser hides scrollbars but keeps that space.
		const page = document.documentElement.getBoundingClientRect().width;
		let shift = 0;
		if (left + width > page - EDGE) {
			shift = page - EDGE - (left + width);
		}
		if (left + shift < EDGE) {
			shift = EDGE - left;
		}
		if (shift) {
			node.style.setProperty('--shift', `${Math.round(shift)}px`);
		} else {
			node.style.removeProperty('--shift');
		}
	}

	// Before it opens: a finger touching down (it opens when the finger lifts), the mouse arriving, keyboard focus.
	$effect(() => {
		const node = wrap;
		if (!node) {
			return;
		}
		const events = ['pointerdown', 'pointerenter', 'focusin'];
		for (const event of events) {
			node.addEventListener(event, fit);
		}
		return () => {
			for (const event of events) {
				node.removeEventListener(event, fit);
			}
		};
	});

	/* And whenever it appears or changes size (another language), whatever opened it: after layout, before it is
	   drawn, so it never shows in the wrong place. */
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
	<span class="wrap {align} {placement}" bind:this={wrap}>
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
		box-shadow: var(--panel-edge);
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
