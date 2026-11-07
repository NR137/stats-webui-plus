<script lang="ts">
	import { advancementIcon } from '$lib/advancements';
	import McItem from '$lib/components/McItem.svelte';

	// An advancement's icon on its frame from the game: the shape shows the type (task, goal, challenge), gold when
	// obtained and silver when not.
	let {
		id,
		icon,
		frame = 'task',
		obtained = false
	}: {
		id: string;
		icon: string;
		frame?: string;
		obtained?: boolean;
	} = $props();

	const shape = $derived(frame === 'goal' || frame === 'challenge' ? frame : 'task');
	const image = $derived(`/mc/advancement/${shape}_frame_${obtained ? 'obtained' : 'unobtained'}.png`);
</script>

<span class="advancement-icon" style:background-image="url({image})">
	<McItem id={advancementIcon(id, icon)} />
</span>

<style>
	/* Like the game's advancement screen: a 26 pixel frame with the 16 pixel icon 5 pixels in, every game pixel
	   exactly --advancement-scale screen pixels (3 by default, the tree uses 2 on narrow screens; in px, so it stays
	   whole whatever the font size). */
	.advancement-icon {
		--size: calc(26px * var(--advancement-scale, 3));
		display: inline-grid;
		place-items: center;
		flex-shrink: 0;
		width: var(--size);
		height: var(--size);
		background-size: var(--size) var(--size);
		image-rendering: pixelated;
	}

	.advancement-icon :global(.frame),
	.advancement-icon :global(.icon),
	.advancement-icon :global(.letter) {
		width: calc(16px * var(--advancement-scale, 3));
		height: calc(16px * var(--advancement-scale, 3));
	}
</style>
