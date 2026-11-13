<script lang="ts">
	import { mcFile } from '#lib/mc/files.ts';
	import { itemLetter, itemTextureUrls } from '#lib/mc/items.svelte.ts';

	let { id, compact = false }: { id: string; compact?: boolean } = $props();

	// The sizes the server draws its own pictures in, besides the 32 pixel original.
	const SIZES = [24, 36, 48, 72, 108];
	const DRAWN = /^\/mc\/(item|block|iso|layered|tinted|special|face|scene)\/(.+)$/;

	const urls = $derived(itemTextureUrls(id));
	// Keyed on the addresses, not on the props: a list rebuilt after a language switch passes the same id again, and
	// starting over would show its failed addresses (and the empty frame) once more.
	const key = $derived(urls.join('\n'));
	// Writable deriveds: a failed address moves on to the next; a new id (other addresses) starts at the first again.
	let index = $derived.by(() => {
		void key;
		return 0;
	});
	const src = $derived(urls[index]);
	let loaded = $derived.by(() => {
		void src;
		return false;
	});
	let image = $state<HTMLImageElement>();

	const srcset = $derived.by(() => {
		const match = src?.match(DRAWN);
		if (!match) {
			return undefined;
		}
		const [, folder, file] = match;
		return SIZES.map((size) => `${mcFile(`/mc/${folder}/${size}/${file}`)} ${size}w`).join(', ');
	});

	// A picture already in the cache may be complete before the load handler is there to see it.
	$effect(() => {
		void src;
		if (image?.complete && image.naturalWidth > 0) {
			loaded = true;
		}
	});
</script>

<span class="frame" class:compact>
	{#if src}
		<img
			class="icon"
			class:loaded
			bind:this={image}
			src={mcFile(src)}
			{srcset}
			sizes={srcset ? (compact ? '24px' : '36px') : undefined}
			alt=""
			width="32"
			height="32"
			decoding="async"
			loading="lazy"
			onload={() => (loaded = true)}
			onerror={() => (index += 1)}
		/>
	{:else}
		<span class="letter" aria-hidden="true">{itemLetter(id)}</span>
	{/if}
</span>

<style>
	.frame {
		position: relative;
		display: inline-grid;
		flex-shrink: 0;
		place-items: center;
		width: 2.25rem;
		height: 2.25rem;
	}

	/* Hidden until loaded, so a missing picture never flashes the browser's broken-image sign before the next. Its own
	   layer: hovering a tile repainted it, and rasterising it on the GPU then picked other pixel columns, so the icon
	   seemed to shift by a pixel. */
	.icon {
		display: block;
		flex-shrink: 0;
		width: 2.25rem;
		height: 2.25rem;
		opacity: 0;
		image-rendering: pixelated;
		object-fit: contain;
		will-change: transform;
	}

	.icon.loaded {
		opacity: 1;
	}

	.letter {
		display: grid;
		place-items: center;
		width: 2.25rem;
		height: 2.25rem;
		border: 2px solid var(--color-border);
		background: var(--slot-bg);
		color: var(--color-gold);
		font-size: 1rem;
		line-height: 1;
	}

	.compact,
	.compact .icon,
	.compact .letter {
		width: 1.5rem;
		height: 1.5rem;
	}

	.compact .letter {
		font-size: 0.8rem;
	}
</style>
