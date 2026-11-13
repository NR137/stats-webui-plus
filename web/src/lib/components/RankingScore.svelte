<script lang="ts">
	import { mcFile } from '#lib/mc/files.ts';

	let { value }: { value: string } = $props();
</script>

<span class="score">
	<img class="orb" src={mcFile('/mc/special/experience_orb.png')} alt="" width="16" height="16" />
	{value}
</span>

<style>
	/* Like the experience level over the game's hotbar. */
	.score {
		display: inline-flex;
		gap: 0.3rem;
		align-items: center;
		color: var(--color-xp);
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}

	.orb {
		display: block;
		flex-shrink: 0;
		width: 1.05rem;
		height: 1.05rem;
		image-rendering: pixelated;
	}
</style>
