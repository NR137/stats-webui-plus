<script lang="ts">
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import TextPage from '#lib/components/TextPage.svelte';
	import { advancementTiers } from '#lib/advancementTiers.ts';
	import { i18n, pageTitle, t, tOptional, type MessageKey } from '#lib/i18n/i18n.svelte.ts';
	import { skillBoard, skillColors, skillIcons, skillOrder, skillWeights } from '#lib/playerStats.ts';

	const percent = $derived(new Intl.NumberFormat(i18n.locale, { style: 'percent', maximumFractionDigits: 0 }));

	// The same tiers the player page uses; each one reaches up to just below the next tier's start.
	const tierRanges = $derived(
		advancementTiers.map((tier, index) => {
			const next = advancementTiers[index + 1];
			return next ? `${percent.format(tier.min)} – ${percent.format(next.min - 0.01)}` : percent.format(tier.min);
		})
	);
</script>

<svelte:head>
	<title>{pageTitle(t('menu.about'))}</title>
</svelte:head>

<TextPage>
	<svelte:element this={pageTitleTag()} class="page-title">{t('about.heading')}</svelte:element>
	<p>{t('about.lead')}</p>

	<section class="panel">
		<h2>{t('about.levelHeading')}</h2>
		<p>{t('about.level')}</p>
		<p>{t('about.levelOpen')}</p>
		<p>{t('about.levelOwn')}</p>
	</section>

	<section class="panel">
		<h2>{t('about.partsHeading')}</h2>
		<p>{t('about.parts')}</p>
		<p>{t('about.caps')}</p>
	</section>

	<section class="panel" id="skills">
		<h2>{t('about.skillsHeading')}</h2>
		<ul class="skills">
			{#each skillOrder as id (id)}
				<li id="skill-{id}">
					<h3>
						<ItemIcon id={skillIcons[id]} compact />
						<a href="/leaderboards/{skillBoard(id)}">{tOptional(`skills.${id}.name`, id)}</a>
						<span class="weight" style:--skill={skillColors[id]}
							>{t('about.skillWeight', { weight: percent.format(skillWeights[id]) })}</span
						>
					</h3>
					<!-- The same description as the skill's board. -->
					<p>{tOptional(`boards.${skillBoard(id)}.about`, skillBoard(id))}</p>
				</li>
			{/each}
		</ul>
	</section>

	<section class="panel">
		<h2>{t('about.dyingHeading')}</h2>
		<p>{t('about.dying')}</p>
	</section>

	<section class="panel">
		<h2>{t('about.farmsHeading')}</h2>
		<p>{t('about.farms')}</p>
	</section>

	<section class="panel">
		<h2>{t('about.rankingsHeading')}</h2>
		<ul>
			<li>{t('about.rankingLegends')}</li>
			<li>{t('about.rankingVeterans')}</li>
			<li>{t('about.rankingGrinders')}</li>
		</ul>
	</section>

	<section class="panel" id="tiers">
		<h2>{t('about.tiersHeading')}</h2>
		<p>{t('about.tiers')}</p>
		<ul class="tiers">
			{#each advancementTiers as tier, index (tier.id)}
				<li>
					<span class="tier-icon" class:glow={tier.id === 'dragon_egg'}><ItemIcon id={tier.icon} compact /></span>
					<span class="tier-name">{t(`player.tiers.${tier.id}` as MessageKey)}</span>
					<span class="tier-swatch" style:background={tier.color}></span>
					<span class="tier-range">{tierRanges[index]}</span>
				</li>
			{/each}
		</ul>
	</section>

	<section class="panel">
		<h2>{t('about.unitsHeading')}</h2>
		<p>{t('about.units')}</p>
	</section>
</TextPage>

<style>
	li {
		color: var(--color-text);
		line-height: 1.45;
	}

	.panel ul {
		margin: 0;
		padding-left: 1.1rem;
	}

	.panel li + li {
		margin-top: 0.45rem;
	}

	/* .panel in front: these must beat the rule for lists in panels above. */
	.panel .skills {
		padding: 0;
		list-style: none;
	}

	.panel .skills li + li {
		margin-top: 0.9rem;
	}

	h3 {
		display: flex;
		flex-wrap: wrap;
		gap: 0.2rem 0.5rem;
		align-items: center;
		margin: 0 0 0.3rem;
		font-size: 1rem;
	}

	h3 a {
		color: var(--color-fg);
	}

	/* The skill's share of the level, underlined in the colour of its bar. */
	.weight {
		margin-left: auto;
		border-bottom: 3px solid var(--skill);
		color: var(--color-muted);
		font-size: 0.85rem;
	}

	/* One grid for all tiers, so the bars are equally wide and sit at the same distance between the names and the
	   percentages. The margin above keeps room after the paragraph; the row gap spaces the tiers, so the margin
	   between list items doesn't apply here (the items are display: contents). */
	.panel .tiers {
		display: grid;
		grid-template-columns: 1.6rem auto 1fr auto;
		gap: 0.45rem 1rem;
		align-items: center;
		/* Indented like the other lists in panels. */
		margin-top: 0.9rem;
		padding: 0 0 0 1.1rem;
		list-style: none;
	}

	.tiers li {
		display: contents;
	}

	.tier-icon {
		display: inline-flex;
	}

	/* The dragon egg is black in the game: the same purple glow as on player pages keeps it visible on the dark theme. */
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
</style>
