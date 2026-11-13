<script lang="ts">
	import { advancementDescription, advancementTitle } from '#lib/advancements.ts';
	import AdvancementIcon from '#lib/components/AdvancementIcon.svelte';
	import { mcFile } from '#lib/mc/files.ts';

	// One advancement tab as the game's advancement screen shows it: the tab's background, each advancement on its
	// frame at the place the game lays it out, and the lines from each parent to its children.
	type Item = {
		id: string;
		title?: string;
		description?: string;
		icon: string;
		frame?: string;
		parent?: string | null;
		x?: number;
		y?: number;
		// Hidden by the game until unlocked (see hiddenAdvancements).
		hidden?: boolean;
	};

	let {
		items,
		background,
		obtained,
		href,
		faded,
		hidden,
		detail
	}: {
		// Every advancement of the tab: the tree needs all of them, also ones a search doesn't match.
		items: Item[];
		background?: string | null;
		// Gold frame.
		obtained: (item: Item) => boolean;
		href: (item: Item) => string;
		// Not matching the search: shown faint, so it is clear where the matches sit in the tree.
		faded?: (item: Item) => boolean;
		// Left out (e.g. not achieved, with "Achieved only"): no frame, no line; the others keep their places.
		hidden?: (item: Item) => boolean;
		// A line under the title in the box, e.g. when the player did it or how many players have it.
		detail?: (item: Item) => string | undefined;
	} = $props();

	// The game's geometry (AdvancementWidget): a column is 28 pixels, a row 27; the 26 pixel frame sits 3 pixels into
	// its cell, and the lines are measured from the cell's origin. PAD keeps the outermost frames off the panel's edge.
	const COLUMN = 28;
	const ROW = 27;
	const FRAME = 26;
	const PAD = 6;

	const placed = $derived(
		items.map((item) => ({
			item,
			left: Math.floor((item.x ?? 0) * COLUMN) + PAD,
			top: Math.floor((item.y ?? 0) * ROW) + PAD
		}))
	);
	// The whole tab's size, also with some left out, so everything stays where the game puts it.
	const width = $derived(Math.max(0, ...placed.map((node) => node.left)) + 3 + FRAME + PAD);
	const height = $derived(Math.max(0, ...placed.map((node) => node.top)) + FRAME + PAD);
	const nodes = $derived(placed.filter((node) => !hidden?.(node.item)));

	type Segment = { x: number; y: number; w: number; h: number };
	type Lines = { white: Segment[]; border: Segment[] };
	// Each parent-child line as the game draws it: right from the parent's centre to 4 pixels past its frame, down or
	// up to the child's row, then right to the child's centre. A 1 pixel white line with a black border of 1 pixel.
	// Lines to a faded advancement are faded too; the bright ones are drawn over them, so a stretch shared with a
	// bright child stays bright.
	const lines = $derived.by(() => {
		const shown = new Map(nodes.map((node) => [node.item.id, node]));
		const all = new Map(placed.map((node) => [node.item.id, node]));
		const faint: Lines = { white: [], border: [] };
		const bright: Lines = { white: [], border: [] };
		for (const node of nodes) {
			// The nearest ancestor that is shown, as the game links to the first visible parent.
			let parentId = node.item.parent;
			while (parentId && !shown.has(parentId)) {
				parentId = all.get(parentId)?.item.parent;
			}
			const parent = parentId ? shown.get(parentId) : undefined;
			if (!parent) {
				continue;
			}
			const into = faded?.(node.item) ? faint : bright;
			// As the game computes them: from the cell's origin, not the frame's (which sits 3 pixels in), so the bend is
			// 1 pixel past the parent's frame and 2 short of the child's.
			const startX = parent.left + 13;
			const bendX = parent.left + FRAME + 4;
			const startY = parent.top + 13;
			const endX = node.left + 13;
			const endY = node.top + 13;
			const white = [
				{ x: Math.min(startX, bendX), y: startY, w: Math.abs(bendX - startX) + 1, h: 1 },
				{ x: Math.min(endX, bendX), y: endY, w: Math.abs(endX - bendX) + 1, h: 1 },
				{ x: bendX, y: Math.min(startY, endY), w: 1, h: Math.abs(endY - startY) + 1 }
			];
			into.white.push(...white);
			into.border.push(...white.map((line) => ({ x: line.x - 1, y: line.y - 1, w: line.w + 2, h: line.h + 2 })));
		}
		return { faint, bright };
	});

	// The game's box with title and description over the pointed advancement: fixed to the window, so the scrolling
	// panel can't cut it off. Above the icon, or below where there's no room above.
	let pointed = $state<{ item: Item; x: number; y: number; below: boolean } | null>(null);

	function point(item: Item, target: Element) {
		const box = target.getBoundingClientRect();
		const below = box.top < 120;
		pointed = { item, x: box.left + box.width / 2, y: below ? box.bottom : box.top, below };
	}

	// Centred on the icon, but kept 8 pixels inside the window (its width is only known once it is drawn).
	let bubble = $state<HTMLElement>();
	let bubbleLeft = $state(0);
	$effect(() => {
		if (!pointed || !bubble) {
			return;
		}
		const width = bubble.offsetWidth;
		bubbleLeft = Math.min(Math.max(8, pointed.x - width / 2), window.innerWidth - width - 8);
	});
</script>

<div class="tree-box">
	<div class="scroller" onscroll={() => (pointed = null)}>
		<div
			class="tree"
			style:--w={width}
			style:--h={height}
			style:background-image="url({mcFile(`/mc/advancement/backgrounds/${background ?? 'stone'}.png`)})"
		>
			<svg viewBox="0 0 {width} {height}" aria-hidden="true" shape-rendering="crispEdges">
				{#each [lines.faint, lines.bright] as group, index}
					<g opacity={index === 0 ? 0.3 : 1}>
						{#each group.border as segment}
							<rect x={segment.x} y={segment.y} width={segment.w} height={segment.h} fill="#000" />
						{/each}
						{#each group.white as segment}
							<rect x={segment.x} y={segment.y} width={segment.w} height={segment.h} fill="#fff" />
						{/each}
					</g>
				{/each}
			</svg>
			{#each nodes as node (node.item.id)}
				<a
					class="node"
					class:faded={faded?.(node.item)}
					style:--left={node.left + 3}
					style:--top={node.top}
					href={href(node.item)}
					aria-label={advancementTitle(node.item.id, node.item.title)}
					onpointerenter={(event) => event.pointerType === 'mouse' && point(node.item, event.currentTarget)}
					onpointerleave={() => (pointed = null)}
					onfocus={(event) => point(node.item, event.currentTarget)}
					onblur={() => (pointed = null)}
				>
					<AdvancementIcon
						id={node.item.id}
						icon={node.item.icon}
						frame={node.item.frame}
						obtained={obtained(node.item)}
					/>
				</a>
			{/each}
		</div>
	</div>
</div>

<!-- Hidden while scrolling: the box would stay where the icon was. -->
<svelte:window onscroll={() => (pointed = null)} />
{#if pointed}
	<div
		bind:this={bubble}
		class="bubble"
		class:below={pointed.below}
		role="tooltip"
		style:left="{bubbleLeft}px"
		style:top="{pointed.y}px"
	>
		<span class="title" class:challenge={pointed.item.frame === 'challenge'}
			>{advancementTitle(pointed.item.id, pointed.item.title)}</span
		>
		<span class="description">{advancementDescription(pointed.item.id, pointed.item.description ?? '')}</span>
		{#if detail?.(pointed.item)}
			<span class="detail">{detail(pointed.item)}</span>
		{/if}
	</div>
{/if}

<style>
	.tree-box {
		/* Every game pixel is 3 screen pixels, 2 on narrower screens (the frames shrink with it). */
		--s: 3;
		--advancement-scale: var(--s);
		margin-bottom: 0.5rem;
	}

	@media (max-width: 859px) {
		.tree-box {
			--s: 2;
		}
	}

	/* Wider trees scroll sideways (a finger on phones), as the game's screen does. */
	.scroller {
		overflow-x: auto;
		border: 2px solid var(--color-border);
	}

	.tree {
		position: relative;
		width: calc(var(--w) * var(--s) * 1px);
		min-width: 100%;
		height: calc(var(--h) * var(--s) * 1px);
		background-size: calc(16px * var(--s));
		image-rendering: pixelated;
	}

	svg {
		position: absolute;
		top: 0;
		left: 0;
		width: calc(var(--w) * var(--s) * 1px);
		height: calc(var(--h) * var(--s) * 1px);
	}

	.node {
		position: absolute;
		left: calc(var(--left) * var(--s) * 1px);
		top: calc(var(--top) * var(--s) * 1px);
		display: block;
		line-height: 0;
	}

	.node:focus-visible {
		outline: 2px solid var(--color-hover);
		outline-offset: 2px;
	}

	/* Darkened rather than see-through: the frames cover the lines behind them, as in the game. */
	.node.faded {
		filter: brightness(0.4) grayscale(0.7);
	}

	/* The game's box: dark in both themes (like the advancement tiles), so the title keeps the game's yellow and
	   pink. Over the icon, or under it near the top of the window. */
	.bubble {
		--color-advancement: #ffff00;
		--color-challenge: #ff88ff;
		--color-muted: #a0a0a0;
		--color-text: #e0e0e0;
		position: fixed;
		z-index: 70;
		display: flex;
		flex-direction: column;
		gap: 0.15rem;
		width: max-content;
		max-width: min(18rem, calc(100vw - 1rem));
		padding: 0.4rem 0.6rem;
		border: 2px solid #000;
		box-shadow: inset 0 0 0 2px #2b1e14;
		background: #120c08;
		font-size: 0.875rem;
		line-height: 1.3;
		text-shadow: 2px 2px 0 #3f3f3f;
		transform: translateY(calc(-100% - 0.4rem));
		pointer-events: none;
	}

	.bubble.below {
		transform: translateY(0.4rem);
	}

	.title {
		color: var(--color-advancement);
	}

	.title.challenge {
		color: var(--color-challenge);
	}

	.description {
		color: var(--color-muted);
	}

	.detail {
		color: var(--color-text);
		font-variant-numeric: tabular-nums;
	}
</style>
