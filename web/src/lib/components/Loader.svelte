<script lang="ts">
	import { t } from '#lib/i18n/i18n.svelte.ts';

	let { compact = false, embed = false }: { compact?: boolean; embed?: boolean } = $props();
</script>

<div
	class="loader"
	class:compact
	class:embed
	role="status"
	aria-live="polite"
	aria-busy="true"
	aria-label={t('site.loading')}
>
	<span class="blocks" aria-hidden="true">
		<span class="block"></span>
		<span class="block"></span>
		<span class="block"></span>
	</span>
</div>

<style>
	.loader {
		--block: 0.72rem;
		--block-border: 2px;
		--block-gap: 0.28rem;
		--hop: 0.55rem;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 2.4rem 1rem;
	}

	.compact {
		padding: 0.85rem 0.4rem;
	}

	/* Inside small boxes (a player's head while its skin loads): over the box, smaller, and never in the way. */
	.embed {
		--block: 0.42rem;
		--block-border: 1px;
		--block-gap: 0.12rem;
		--hop: 0.28rem;
		position: absolute;
		inset: 0;
		padding: 0;
		pointer-events: none;
	}

	.blocks {
		display: flex;
		gap: var(--block-gap);
		align-items: flex-end;
	}

	.embed .blocks {
		transform: scale(0.72);
		transform-origin: 50% 100%;
	}

	.block {
		width: var(--block);
		height: var(--block);
		border: var(--block-border) solid var(--color-border);
		box-shadow: inset 1px 1px 0 var(--button-highlight), inset -1px -1px 0 var(--button-shadow);
		background: var(--color-gold);
		animation: hop 0.72s ease-in-out infinite;
	}

	.block:nth-child(2) {
		background: var(--color-accent);
		animation-delay: 0.12s;
	}

	.block:nth-child(3) {
		background: var(--color-bronze);
		animation-delay: 0.24s;
	}

	@keyframes hop {
		0%,
		100% {
			transform: translateY(0);
		}
		40% {
			transform: translateY(calc(-1 * var(--hop)));
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.block {
			animation: none;
		}
	}
</style>
