<script lang="ts">
	import { advancementProgress } from '#lib/advancementTiers.ts';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import Tooltip from '#lib/components/Tooltip.svelte';
	import './meters.css';
	import { i18n, t, type MessageKey } from '#lib/i18n/i18n.svelte.ts';

	let { done, total }: { done: number; total: number } = $props();

	const progress = $derived(advancementProgress(done, total));
	const tierName = $derived(t(`player.tiers.${progress.tier.id}` as MessageKey));
	const hint = $derived(
		progress.next
			? t('player.nextTier', {
					count: progress.missing,
					tier: t(`player.tiers.${progress.next.id}` as MessageKey)
				})
			: t('player.allAdvancements')
	);
	// The text around the tier's name, so the name alone can carry the tooltip.
	const around = $derived.by(() => {
		const text = t('player.tier');
		const at = text.indexOf('{tier}');
		return at < 0 ? { before: text, after: '' } : { before: text.slice(0, at), after: text.slice(at + '{tier}'.length) };
	});
	const percent = $derived(
		new Intl.NumberFormat(i18n.locale, { style: 'percent', maximumFractionDigits: 0 }).format(progress.share)
	);
	const egg = $derived(progress.tier.id === 'dragon_egg');
</script>

<div class="meters">
	<p class="meters-caption">
		{t('player.advancements')}{' '}<a class="meters-how" href="/about#tiers">{t('player.tiersLink')}</a>
	</p>
	<div class="meter">
		<span class="meter-icon" class:egg><ItemIcon id={progress.tier.icon} /></span>
		<div class="meter-line">
			<span class="tier"
				>{around.before}<Tooltip text={hint} align="start" focusable
					><span class="tier-name">{tierName}</span></Tooltip
				>{around.after}</span
			>
			<span class="meter-value">{done} / {total} ({percent})</span>
		</div>
		<span class="meter-bar"
			><span class="meter-fill" class:egg style:width="{progress.share * 100}%" style:--fill={progress.tier.color}
			></span></span
		>
	</div>
</div>

<style>
	/* The dragon egg is black in the game: a purple glow keeps it visible on dark backgrounds. */
	.meter-icon.egg {
		filter: drop-shadow(0 0 3px #c25cf5);
	}

	.tier {
		color: var(--color-muted);
	}

	.tier-name {
		color: var(--color-fg);
	}

	.meter-fill {
		background: var(--fill);
		background-size: 200% 100%;
	}

	.meter-fill.egg {
		animation: shimmer 3s linear infinite;
	}

	@keyframes shimmer {
		to {
			background-position: -200% 0;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.meter-fill.egg {
			animation: none;
		}
	}
</style>
