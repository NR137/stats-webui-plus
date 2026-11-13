<script lang="ts">
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';

	let {
		href,
		icon,
		value,
		label,
		rank = '',
		hint,
		zero
	}: { href: string; icon: string; value: string; label: string; rank?: string; hint: string; zero: boolean } =
		$props();
</script>

<div class="cell">
	<Tooltip text={hint} fill>
		<a class="tile slot" class:zero {href}>
			<span class="icon"><ItemIcon id={icon} /></span>
			<span class="value">{value}</span>
			<span class="label">{label}</span>
			{#if rank}
				<span class="rank">{rank}</span>
			{/if}
		</a>
	</Tooltip>
</div>

<style>
	.cell {
		display: flex;
		min-width: 0;
		height: 100%;
	}

	.tile {
		min-height: 8.2rem;
	}

	.zero {
		opacity: 0.42;
	}

	.icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 2.6rem;
		height: 2.6rem;
		margin-bottom: 0.15rem;
	}

	.value {
		color: var(--color-gold);
		font-size: 1.125rem;
		font-variant-numeric: tabular-nums;
	}

	.label {
		color: var(--color-muted);
		font-size: 0.875rem;
		line-height: 1.25;
	}

	.rank {
		color: var(--color-accent);
		font-size: 0.75rem;
		line-height: 1.25;
	}
</style>
