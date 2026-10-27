<script lang="ts">
	import { pageTitleTag } from '$lib/branding.svelte';
	import McItem from '$lib/components/McItem.svelte';
	import { advancementTiers } from '$lib/advancementTiers';
	import { i18n, pageTitle, t, type MessageKey } from '$lib/i18n/i18n.svelte';

	// "10–24 %" per tier, from the same definitions the player page uses.
	const tierRanges = $derived.by(() => {
		const percent = new Intl.NumberFormat(i18n.locale, { style: 'percent', maximumFractionDigits: 0 });
		return advancementTiers.map((tier, index) => {
			const next = advancementTiers[index + 1];
			const range = next
				? `${percent.format(tier.min)} – ${percent.format(next.min - 0.01)}`
				: percent.format(tier.min);
			return { tier, range };
		});
	});
</script>

<svelte:head>
	<title>{pageTitle(t('nav.about'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('about.heading')}</svelte:element>
<p>{t('about.lead')}</p>

<section class="panel">
	<h2>{t('about.howHeading')}</h2>
	<p>{t('about.how')}</p>
</section>

<section class="panel">
	<h2>{t('about.categoriesHeading')}</h2>
	<ul>
		<li>{t('about.category.combat')}</li>
		<li>{t('about.category.milestones')}</li>
		<li>{t('about.category.mining')}</li>
		<li>{t('about.category.explore')}</li>
		<li>{t('about.category.produce')}</li>
		<li>{t('about.category.survive')}</li>
		<li>{t('about.category.flex')}</li>
	</ul>
</section>

<section class="panel">
	<h2>{t('about.farmsHeading')}</h2>
	<p>{t('about.farms')}</p>
</section>

<section class="panel">
	<h2>{t('about.crownsHeading')}</h2>
	<ul>
		<li>{t('about.crownChampion')}</li>
		<li>{t('about.crownDedicated')}</li>
		<li>{t('about.crownEfficient')}</li>
	</ul>
</section>

<section class="panel">
	<h2>{t('about.tiersHeading')}</h2>
	<p>{t('about.tiers')}</p>
	<ul class="tiers">
		{#each tierRanges as { tier, range } (tier.id)}
			<li>
				<span class="tier-icon" class:glow={tier.id === 'dragon_egg'}><McItem id={tier.icon} compact /></span>
				<span class="tier-name">{t(`player.tier.${tier.id}` as MessageKey)}</span>
				<span class="tier-swatch" style="background: {tier.color}"></span>
				<span class="tier-range">{range}</span>
			</li>
		{/each}
	</ul>
</section>

<section class="panel">
	<h2>{t('about.unitsHeading')}</h2>
	<p>{t('about.units')}</p>
</section>

<style>
	.page-title {
		/* Same look whether this is the page's h1 or an h2 below the site heading. */
		font-size: 2rem;
		margin: 0.67em 0;
		text-align: center;
	}

	p,
	li {
		color: var(--color-text);
		line-height: 1.45;
	}

	p {
		max-width: 42rem;
		margin-left: auto;
		margin-right: auto;
	}

	.panel {
		max-width: 42rem;
		margin: 1.1rem auto 0;
		padding: 0.85rem 1rem;
	}

	.panel p {
		margin: 0;
		max-width: none;
	}

	.panel ul {
		margin: 0;
		padding-left: 1.1rem;
	}

	.tiers {
		list-style: none;
		padding: 0;
	}

	.tiers li {
		display: grid;
		/* Fixed range column so all color bars end at the same point. */
		grid-template-columns: 1.6rem minmax(6rem, auto) 1fr 8rem;
		align-items: center;
		gap: 0.6rem;
	}

	.tier-icon {
		display: inline-flex;
	}

	/* Same glow as on player pages: the black dragon egg stays visible on the dark theme. */
	.tier-icon.glow {
		filter: drop-shadow(0 0 3px #c25cf5);
	}

	.tier-swatch {
		height: 0.55rem;
		border: 2px solid var(--color-border);
	}

	.tier-range {
		color: var(--color-gold);
		font-variant-numeric: tabular-nums;
		text-align: right;
	}

	.panel li + li {
		margin-top: 0.45rem;
	}

	h2:not(.page-title) {
		margin: 0 0 0.6rem;
		font-size: 1rem;
		color: var(--color-gold);
	}
</style>
