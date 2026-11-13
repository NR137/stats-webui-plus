<script lang="ts">
	import { playerPath } from '#lib/players.ts';
	import { page } from '$app/state';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import { criterionPart } from '#lib/advancementCriteria.ts';
	import { pathToId } from '#lib/ids.ts';
	import { advancementDescription, advancementHref, advancementTitle } from '#lib/advancements.ts';
	import { NotFoundError, getPlayerAdvancement } from '#lib/api.ts';
	import AdvancementIcon from '#lib/components/AdvancementIcon.svelte';
	import BackNav from '#lib/components/BackNav.svelte';
	import Loader from '#lib/components/Loader.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import { formatWhen, nameComparer, pageTitle, t } from '#lib/i18n/i18n.svelte.ts';
	import type { PlayerAdvancementResponse } from '#lib/types.ts';

	// One player's progress on one advancement: when it was done, and for advancements with several parts (Adventuring
	// Time's biomes, Monsters Hunted's mobs, ...) which parts are done, when, and which are still missing.

	let detail = $state.raw<PlayerAdvancementResponse | null>(null);
	let failed = $state(false);
	let notFound = $state(false);
	let loading = $state(true);

	const name = $derived(page.params.name ?? '');
	// The ID as in every address (lib/ids.ts), like .../advancements/adventure/adventuring_time. Its first part is a
	// parameter of its own, so that .../advancements alone stays the player's advancements tab.
	const id = $derived(pathToId([page.params.first, page.params.rest].filter(Boolean).join('/')));

	$effect(() => {
		const player = name;
		const advancement = id;
		if (!player || !page.params.first) {
			return;
		}
		detail = null;
		failed = false;
		notFound = false;
		loading = true;
		getPlayerAdvancement(player, advancement)
			.then((data) => {
				detail = data;
			})
			.catch((error) => {
				failed = true;
				notFound = error instanceof NotFoundError;
			})
			.finally(() => {
				loading = false;
			});
	});

	type Option = { criterion: string; at: number; label: string; icon?: string };
	// A requirement is met by any one of its options; met is when the first of them was met (0 if none yet).
	type Requirement = { options: Option[]; met: number };

	const requirements = $derived.by((): Requirement[] => {
		if (!detail) {
			return [];
		}
		const advancement = detail.id;
		return detail.requirements.map((group) => {
			// A criterion without a time (null) counts as not met yet, like 0 below.
			const options = group.map((item) => ({
				...item,
				at: item.at ?? 0,
				...criterionPart(advancement, item.criterion)
			}));
			const times = options.map((option) => option.at).filter((at) => at > 0);
			return { options, met: times.length > 0 ? Math.min(...times) : 0 };
		});
	});
	// A single criterion has nothing to list: the status says it all.
	const hasParts = $derived(requirements.length > 1 || (requirements[0]?.options.length ?? 0) > 1);
	const done = $derived(requirements.filter((item) => item.met > 0).sort((a, b) => a.met - b.met));
	const missing = $derived.by(() => {
		const compare = nameComparer();
		return requirements
			.filter((item) => item.met === 0)
			.map((item) => ({ ...item, options: [...item.options].sort((a, b) => compare(a.label, b.label)) }))
			.sort((a, b) => compare(a.options[0]?.label ?? '', b.options[0]?.label ?? ''));
	});
	const title = $derived(detail ? advancementTitle(detail.id, detail.title) : t('menu.advancements'));
	const share = $derived(requirements.length > 0 ? done.length / requirements.length : 0);
</script>

<svelte:head>
	<title>{pageTitle(`${title} · ${detail?.player.name ?? name}`)}</title>
</svelte:head>

<BackNav />

{#if failed}
	<p class="empty">{t(notFound ? 'advancements.notFound' : 'site.loadError')}</p>
{:else if loading || !detail}
	<Loader />
{:else}
	<svelte:element this={pageTitleTag()} class="page-title">
		<AdvancementIcon id={detail.id} icon={detail.icon} frame={detail.frame} obtained={detail.done} />
		{title}
	</svelte:element>
	<p class="lead">
		{advancementDescription(detail.id, detail.description)}
		{#if detail.experience}
			{' · '}{t('advancements.reward', { xp: detail.experience })}
		{/if}
	</p>

	<section class="status panel">
		<a class="player" href="{playerPath(detail.player.name, detail.player.uuid)}/advancements">{detail.player.name}</a>
		{#if detail.done}
			<span class="state done">{t('advancements.player.doneOn', { when: formatWhen(detail.completedAt ?? 0) })}</span>
		{:else}
			<span class="state">{t('advancements.player.notYet')}</span>
		{/if}
		{#if requirements.length > 1}
			<span class="count">{t('advancements.player.progress', { done: done.length, total: requirements.length })}</span>
			<span class="bar" class:full={detail.done}><i style="width: {share * 100}%"></i></span>
		{/if}
	</section>

	{#if hasParts}
		{#snippet option(item: Option, showWhen: boolean)}
			<li class="part slot" class:met={item.at > 0}>
				{#if item.icon}
					<ItemIcon id={item.icon} compact />
				{/if}
				<span class="text">
					<span class="label">{item.label}</span>
					{#if showWhen && item.at > 0}
						<span class="when">{formatWhen(item.at)}</span>
					{/if}
				</span>
			</li>
		{/snippet}

		{#if done.length > 0}
			<section class="parts">
				<h2>
					{t('advancements.player.partsDone')}
					<!-- "Missing 1" over 41 mobs of which any one counts would only confuse. -->
					{#if requirements.length > 1}<span class="total">{done.length}</span>{/if}
				</h2>
				<ul>
					{#each done as requirement}
						<!-- Of a requirement with several options, only the ones met so far. -->
						{#each requirement.options.filter((item) => item.at > 0) as item (item.criterion)}
							{@render option(item, true)}
						{/each}
					{/each}
				</ul>
			</section>
		{/if}

		{#if missing.length > 0}
			<section class="parts">
				<h2>
					{t('advancements.player.partsMissing')}
					<!-- "Missing 1" over 41 mobs of which any one counts would only confuse. -->
					{#if requirements.length > 1}<span class="total">{missing.length}</span>{/if}
				</h2>
				<!-- What is needed in any case first, then each requirement of which any one option counts. -->
				{#if missing.some((item) => item.options.length === 1)}
					<ul>
						{#each missing.filter((item) => item.options.length === 1) as requirement}
							{@render option(requirement.options[0], false)}
						{/each}
					</ul>
				{/if}
				{#each missing.filter((item) => item.options.length > 1) as requirement}
					<p class="any">{t('advancements.player.anyOf')}</p>
					<ul>
						{#each requirement.options as item (item.criterion)}
							{@render option(item, false)}
						{/each}
					</ul>
				{/each}
			</section>
		{/if}
	{/if}

	<p class="overall">
		<a href={advancementHref(detail.id)}>{t('advancements.player.allPlayers')}</a>
		<span class="holders">({detail.holders === 1
				? t('advancements.holder')
				: t('advancements.holders', { count: detail.holders })})</span>
	</p>
{/if}

<style>
	.page-title {
		display: flex;
		gap: 0.55rem;
		align-items: center;
		justify-content: center;
		margin: 0;
		/* Same look whether this is the page's h1 or an h2 below the site heading. */
		font-size: 2rem;
		text-align: center;
	}

	.empty {
		color: var(--color-muted);
		text-align: center;
	}

	.status {
		display: flex;
		flex-wrap: wrap;
		gap: 0.4rem 1rem;
		align-items: center;
		margin: 1rem 0;
		padding: 0.8rem 1rem;
	}

	.player {
		border-bottom: 1px dotted currentColor;
		color: var(--color-fg);
	}

	.player:hover,
	.player:focus-visible {
		color: var(--color-hover);
	}

	.state {
		color: var(--color-muted);
	}

	.state.done {
		color: var(--color-accent);
	}

	.count {
		margin-left: auto;
		color: var(--color-gold);
		font-variant-numeric: tabular-nums;
	}

	.bar {
		flex: 1 1 100%;
		height: 6px;
		border: 1px solid var(--color-border);
		background: var(--slot-bg);
	}

	.bar i {
		display: block;
		height: 100%;
		background: var(--color-gold);
	}

	.bar.full i {
		background: var(--color-accent);
	}

	.parts h2 {
		margin: 1.2rem 0 0.6rem;
		color: var(--color-muted);
		font-size: 1rem;
	}

	.total {
		margin-left: 0.4rem;
		font-size: 0.75rem;
	}

	.parts ul {
		display: grid;
		/* Wide enough for every single word (German mob names like "Eiswanderer" are too short to be hyphenated and
		   broke anywhere in narrower boxes): four per row on desktop, two on phones. */
		grid-template-columns: repeat(auto-fill, minmax(min(12rem, calc(50% - 0.25rem)), 1fr));
		gap: 0.45rem;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.part {
		display: flex;
		gap: 0.5rem;
		align-items: center;
		min-height: 2.6rem;
		padding: 0.35rem 0.6rem;
	}

	.part:not(.met) .label {
		color: var(--color-muted);
	}

	.text {
		display: flex;
		flex: 1;
		flex-direction: column;
		min-width: 0;
	}

	.label {
		font-size: 0.875rem;
		line-height: 1.25;
	}

	.when {
		color: var(--color-muted);
		font-size: 0.75rem;
		font-variant-numeric: tabular-nums;
	}

	.any {
		margin: 0.6rem 0 0.4rem;
		color: var(--color-muted);
		font-size: 0.875rem;
	}

	.overall {
		margin-top: 1.6rem;
		text-align: center;
	}

	.overall a {
		border-bottom: 1px dotted currentColor;
		color: var(--color-text);
	}

	.overall a:hover,
	.overall a:focus-visible {
		color: var(--color-hover);
	}

	.holders {
		color: var(--color-muted);
		font-size: 0.875rem;
	}
</style>
