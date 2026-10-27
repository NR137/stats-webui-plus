<script lang="ts">
	import { pageTitleTag } from '$lib/branding.svelte';
	import { page } from '$app/state';
	import AdvancementTiles from '$lib/components/AdvancementTiles.svelte';
	import BackNav from '$lib/components/BackNav.svelte';
	import Loader from '$lib/components/Loader.svelte';
	import McItem from '$lib/components/McItem.svelte';
	import PlayerSkin from '$lib/components/PlayerSkin.svelte';
	import RankingScore from '$lib/components/RankingScore.svelte';
	import SearchField from '$lib/components/SearchField.svelte';
	import Tabs from '$lib/components/Tabs.svelte';
	import Tooltip from '$lib/components/Tooltip.svelte';
	import { advancementTabTitle, compareAdvancementTitles, matchesAdvancementQuery } from '$lib/advancements';
	import { getAdvancements, getLeaderboards, getPlayerProfile } from '$lib/api';
	import { boardHint, formatRelative, formatWhen, i18n, pageTitle, t, tDynamic } from '$lib/i18n/i18n.svelte';
	import type { MessageKey } from '$lib/i18n/messages/es';
	import { gradeItems, gradeOrder } from '$lib/playerStats';
	import { advancementProgress } from '$lib/advancementTiers';
	import { cappedResults, deferredValue } from '$lib/search.svelte';
	import type { AdvancementInfo, AdvancementTab, LeaderboardCategory, PlayerDetail } from '$lib/types';
	import {
		hideZerosByDefault,
		vanillaBoardHref,
		vanillaGroups,
		vanillaIcon,
		vanillaLabel,
		visibleVanilla,
		type VanillaGroup
	} from '$lib/vanillaStats';

	// Raw: replaced as a whole when loaded, never changed in place; deep reactivity would proxy every row.
	let player = $state.raw<PlayerDetail | null>(null);
	let catalog = $state<AdvancementInfo[]>([]);
	let advancementTabs = $state<AdvancementTab[]>([]);
	let statCategories = $state<LeaderboardCategory[]>([]);
	let failed = $state(false);
	let loading = $state(true);
	let query = $state('');
	let hideZeros = $state({ ...hideZerosByDefault });
	let achievedOnly = $state(false);
	// Only the name: page.params is replaced on every navigation, including tab switches,
	// and reading it directly would reload the player whenever the tab changes.
	const playerName = $derived(page.params.name);

	// The tab comes from the URL: /players/<name> (stats) or /players/<name>/advancements.
	const profileTab = $derived(page.params.tab === 'advancements' ? 'advancements' : 'stats');
	let openGroups = $state(closedGroups());
	let openAdvancementTabs = $state<Record<string, boolean>>({});
	const filterQuery = deferredValue(() => query);

	const searching = $derived(filterQuery.current.trim().length > 0);
	// Tabs are links, so the browser's back/forward buttons and "open in new tab" work too.
	const playerHref = $derived(`/players/${encodeURIComponent(playerName ?? '')}`);
	const profileTabs = $derived([
		{ id: 'stats', label: t('player.stats'), href: playerHref },
		{ id: 'advancements', label: t('player.advancements'), href: `${playerHref}/advancements` }
	]);

	function closedGroups(): Record<VanillaGroup, boolean> {
		return {
			custom: false,
			killed: false,
			killed_by: false,
			mined: false,
			crafted: false,
			used: false,
			broken: false,
			picked_up: false,
			dropped: false
		};
	}

	function isOpen(group: VanillaGroup) {
		return searching || openGroups[group];
	}

	function toggleGroup(group: VanillaGroup) {
		if (searching) {
			return;
		}
		openGroups = { ...openGroups, [group]: !openGroups[group] };
	}

	function isAdvancementOpen(tab: string) {
		return searching || Boolean(openAdvancementTabs[tab]);
	}

	function toggleAdvancementTab(tab: string) {
		if (searching) {
			return;
		}
		openAdvancementTabs = { ...openAdvancementTabs, [tab]: !openAdvancementTabs[tab] };
	}

	$effect(() => {
		const name = playerName;
		if (!name) {
			return;
		}
		player = null;
		catalog = [];
		advancementTabs = [];
		statCategories = [];
		failed = false;
		loading = true;
		query = '';
		hideZeros = { ...hideZerosByDefault };
		achievedOnly = false;
		openGroups = closedGroups();
		openAdvancementTabs = {};
		Promise.all([
			getPlayerProfile(name),
			getAdvancements().catch(() => null),
			getLeaderboards().catch(() => null)
		])
			.then(([data, advancements, boards]) => {
				player = data;
				catalog = advancements?.advancements ?? [];
				advancementTabs = advancements?.tabs ?? [];
				statCategories = boards?.categories ?? [];
				openAdvancementTabs = Object.fromEntries(
					(advancements?.tabs ?? []).map((tab) => [tab.id, true])
				);
				failed = false;
			})
			.catch(() => {
				player = null;
				failed = true;
			})
			.finally(() => {
				loading = false;
			});
	});

	const shownGrades = $derived(
		player
			? gradeOrder.filter((id) => player && Object.prototype.hasOwnProperty.call(player.grades, id))
			: []
	);
	const scoreHint = $derived.by(() => {
		void i18n.locale;
		return boardHint('champion');
	});
	const playHint = $derived.by(() => {
		void i18n.locale;
		return boardHint('play-time');
	});
	// Shown in the visitor's own time zone and language; "≈" marks estimates for players from before the mod.
	function timeText(ms: number | null, estimated: boolean) {
		void i18n.locale;
		return ms ? `${estimated ? '≈ ' : ''}${formatWhen(ms)}` : t('player.unknown');
	}

	function timeHint(ms: number | null, estimated: boolean, estimateKey: MessageKey) {
		void i18n.locale;
		return ms ? [formatRelative(ms), estimated ? t(estimateKey) : ''].filter(Boolean).join(' · ') : '';
	}

	const firstJoinText = $derived(player ? timeText(player.firstJoin, player.firstJoinEstimated) : '');
	const firstJoinHint = $derived(
		player ? timeHint(player.firstJoin, player.firstJoinEstimated, 'player.firstJoinEstimate') : ''
	);
	const lastSeenText = $derived(player ? timeText(player.lastLogout, player.lastLogoutEstimated) : '');
	const lastSeenHint = $derived(
		player && !player.online ? timeHint(player.lastLogout, player.lastLogoutEstimated, 'player.lastSeenEstimate') : ''
	);

	const rateHint = $derived.by(() => {
		void i18n.locale;
		return boardHint('efficient');
	});
	const doneIds = $derived(new Set(player?.advancements?.ids ?? []));
	const doneTimes = $derived(player?.advancements?.times ?? {});
	const advancementsDone = $derived(player?.advancements?.done ?? 0);
	const advancementsTotal = $derived(player?.advancements?.total || catalog.length);
	const progress = $derived(advancementProgress(advancementsDone, advancementsTotal));
	const progressPercent = $derived.by(() => {
		void i18n.locale;
		return new Intl.NumberFormat(i18n.locale, { style: 'percent', maximumFractionDigits: 0 }).format(progress.share);
	});
	// "Stone tier" / "Stufe Stein": the text around the tier name, which is shown highlighted with the tooltip.
	const tierLabel = $derived.by(() => {
		const [before = '', after = ''] = t('player.tierLabel', { tier: '\u0000' }).split('\u0000');
		return [before, after];
	});
	const progressHint = $derived.by(() => {
		void i18n.locale;
		return progress.next
			? t('player.nextTier', { count: progress.missing, tier: t(`player.tier.${progress.next.id}` as MessageKey) })
			: t('player.allAdvancements');
	});
	const advancementSections = $derived.by(() => {
		void i18n.locale;
		const needle = filterQuery.current;
		return advancementTabs
			.map((tab) => {
				const items = catalog.filter((item) => item.tab === tab.id).sort(compareAdvancementTitles);
				const finished = items.filter((item) => doneIds.has(item.id));
				const done = finished.length;
				// Finished ones first, then the rest; both in title order.
				const ordered = [...finished, ...items.filter((item) => !doneIds.has(item.id))];
				const matched = ordered.filter((item) => {
					if (achievedOnly && !doneIds.has(item.id)) {
						return false;
					}
					return matchesAdvancementQuery(item.id, item.title, item.description, needle);
				});
				return { tab, items, done, matched, ids: cappedResults(matched, searching) };
			})
			.filter((section) => section.matched.length > 0);
	});
</script>

<svelte:head>
	<title>{pageTitle(page.params.name ?? t('title.site'))}</title>
</svelte:head>

<BackNav />

{#if failed}
	<svelte:element this={pageTitleTag()} class="page-title">{page.params.name}</svelte:element>
	<p class="muted">{t('player.loadError')}</p>
{:else if loading || !player}
	<svelte:element this={pageTitleTag()} class="page-title">{page.params.name}</svelte:element>
	<Loader />
{:else}
	<section class="hero">
		<div class="portrait">
			<PlayerSkin uuid={player.uuid} variant="body" />
		</div>
		<div class="info">
			<div class="identity">
				<div class="title">
					<PlayerSkin uuid={player.uuid} variant="head" />
					<svelte:element this={pageTitleTag()} class="page-title">{player.name}</svelte:element>
				</div>
				<p class="scoreline">
					<Tooltip text={scoreHint} align="end" focusable>
						<RankingScore value={player.championDisplay} />
					</Tooltip>
					<span class="rank">{t('player.rankOf', { rank: player.championRank, total: player.trackedPlayers })}</span>
				</p>
				<dl class="meta">
					<div>
						<dt>{t('player.playTime')}</dt>
						<dd>
							<Tooltip text={playHint} align="start" focusable>
								<span>{player.playHoursDisplay}</span>
							</Tooltip>
						</dd>
					</div>
					<div>
						<dt>{t('player.scorePerHour')}</dt>
						<dd>
							<Tooltip text={rateHint} align="end" focusable>
								<span>{player.scorePerHourDisplay}</span>
							</Tooltip>
						</dd>
					</div>
					<div>
						<dt>{t('player.firstJoin')}</dt>
						<dd>
							<Tooltip text={firstJoinHint} align="start" focusable>
								<span>{firstJoinText}</span>
							</Tooltip>
						</dd>
					</div>
					<div>
						<dt>{t('player.lastSeen')}</dt>
						<dd>
							{#if player.online}
								<span class="online">● {t('player.onlineNow')}</span>
							{:else}
								<Tooltip text={lastSeenHint} align="end" focusable>
									<span>{lastSeenText}</span>
								</Tooltip>
							{/if}
						</dd>
					</div>
				</dl>
			</div>
			{#if shownGrades.length > 0}
				<div class="grades">
					<p class="section">{t('player.grades')}</p>
					{#each shownGrades as id}
						{@const grade = player.grades[id] ?? 0}
						<div class="grade">
							<McItem id={gradeItems[id]} />
							<div class="grade-copy">
								<span>{tDynamic('player.grade', id)}</span>
								<span class="grade-value">{Math.round(grade)}</span>
							</div>
							<div class="bar {id}">
								<i style="width: {Math.min(100, Math.max(0, grade))}%"></i>
							</div>
						</div>
					{/each}
				</div>
			{/if}
			{#if advancementsTotal > 0}
				<div class="grades">
					<p class="section">{t('player.advancements')}</p>
					<div class="grade">
						<span class="tier-icon tier-{progress.tier.id}"><McItem id={progress.tier.icon} /></span>
						<div class="grade-copy">
							<span class="tier-label">
								{tierLabel[0]}<Tooltip text={progressHint} align="start" focusable>
									<span class="tier-name">{t(`player.tier.${progress.tier.id}` as MessageKey)}</span>
								</Tooltip>{tierLabel[1]}
							</span>
							<span class="grade-value">{advancementsDone} / {advancementsTotal} ({progressPercent})</span>
						</div>
						<div class="bar">
							<i
								class="tier-fill tier-{progress.tier.id}"
								style="width: {progress.share * 100}%; background: {progress.tier.color}; background-size: 200% 100%"
							></i>
						</div>
					</div>
				</div>
			{/if}
		</div>
	</section>

	<Tabs tabs={profileTabs} selected={profileTab} label={player.name} />

	<div class="filters">
		<SearchField
			bind:value={query}
			placeholder={profileTab === 'advancements' ? t('advancements.search') : t('player.search')}
		/>
	</div>

	{#if profileTab === 'advancements'}
		<div class="pack-head">
			<span class="count"
				>{t('advancements.doneOf', {
					done: player.advancements?.done ?? 0,
					total: player.advancements?.total || catalog.length
				})}</span
			>
			<label class="toggle">
				<input type="checkbox" bind:checked={achievedOnly} />
				{t('player.achievedOnly')}
			</label>
		</div>
		{#if advancementSections.length === 0}
			<p class="muted">{player.advancements?.done ? t('player.noMatches') : t('player.noAdvancements')}</p>
		{:else}
			{#each advancementSections as section (section.tab.id + i18n.locale)}
				<section class="pack">
					<div class="pack-head">
						<button
							class="fold"
							type="button"
							aria-expanded={isAdvancementOpen(section.tab.id)}
							onclick={() => toggleAdvancementTab(section.tab.id)}
						>
							<span class="chevron" class:open={isAdvancementOpen(section.tab.id)}>▸</span>
							<span class="pack-title">
								<McItem id={section.tab.icon} compact />
								{advancementTabTitle(section.tab.id, section.tab.title)}
								<span class="count"
									>{t('advancements.doneOf', {
										done: section.done,
										total: section.items.length
									})}</span
								>
							</span>
						</button>
					</div>
					{#if isAdvancementOpen(section.tab.id)}
						<AdvancementTiles
							items={section.ids}
							dim={(item) => !doneIds.has(item.id)}
							when={(item) => (doneTimes[item.id] ? formatWhen(doneTimes[item.id]) : undefined)}
						/>
					{/if}
				</section>
			{/each}
		{/if}
	{:else if player.vanilla}
		{#each vanillaGroups as group (group + i18n.locale)}
			{@const rows = player.vanilla[group] ?? []}
			{@const matched = visibleVanilla(group, rows, filterQuery.current, hideZeros[group])}
			{@const shown = cappedResults(matched, searching)}
			<section class="pack">
				<div class="pack-head">
					<button
						class="fold"
						type="button"
						aria-expanded={isOpen(group)}
						onclick={() => toggleGroup(group)}
					>
						<span class="chevron" class:open={isOpen(group)}>▸</span>
						<span class="pack-title">
							{tDynamic('player.group', group)}
							<span class="count">{t('player.shown', { shown: shown.length, total: rows.length })}</span>
						</span>
					</button>
					<label class="toggle">
						<input type="checkbox" bind:checked={hideZeros[group]} />
						{t('player.hideZeros')}
					</label>
				</div>
				{#if isOpen(group)}
					{#if matched.length === 0}
						<p class="muted">{t('player.noMatches')}</p>
					{:else}
						<div class="tiles">
							{#each shown as stat (stat.id)}
								<a class="tile slot" class:zero={stat.value <= 0} href={vanillaBoardHref(group, stat.id)}>
									<span class="glyph">
										<McItem id={vanillaIcon(group, stat.id)} />
									</span>
									<span class="amount">{stat.display}</span>
									<span class="label">{vanillaLabel(group, stat.id)}</span>
									{#if stat.value > 0}
										<span class="place">{t('player.rankOf', { rank: stat.rank, total: player.trackedPlayers })}</span>
									{/if}
								</a>
							{/each}
						</div>
					{/if}
				{/if}
			</section>
		{/each}
	{:else}
		{#each statCategories as group}
			<section class="pack">
				<h2>{tDynamic('category', group.id)}</h2>
				<div class="tiles">
					{#each group.boards as stat}
						{@const row = player.stats[stat.id]}
						<a class="tile slot" class:zero={!row || row.value <= 0} href="/leaderboards/{stat.id}">
							<span class="glyph">
								<McItem id={stat.icon} />
							</span>
							<span class="amount">{row?.display ?? '0'}</span>
							<span class="label">{tDynamic('leaderboard', stat.id)}</span>
							{#if row && row.value > 0}
								<span class="place">{t('player.rankOf', { rank: row.rank, total: player.trackedPlayers })}</span>
							{/if}
						</a>
					{/each}
				</div>
			</section>
		{/each}
	{/if}
{/if}

<style>
	.muted {
		color: var(--color-muted);
	}

	.hero {
		display: flex;
		flex-wrap: wrap;
		gap: 1.5rem;
		align-items: stretch;
		margin-bottom: 1.8rem;
	}

	/* As tall as the info next to it; the avatar's camera fits the body to whatever size this ends up. */
	.portrait {
		flex: 0 0 22rem;
		width: 22rem;
		display: flex;
		min-height: 20rem;
	}

	/* Stacked on narrow screens: full width, and only as tall as the body needs. */
	@media (max-width: 680px) {
		.portrait {
			flex-basis: 100%;
			width: 100%;
			height: min(26rem, 110vw);
		}
	}

	.portrait :global(.skin.body) {
		flex: 1 1 auto;
		width: 100%;
		height: 100%;
		min-height: 20rem;
	}

	.portrait :global(.body .stage) {
		width: 100%;
		height: 100%;
		min-height: 20rem;
	}

	.info {
		min-width: min(100%, 18rem);
		flex: 1;
		display: flex;
		flex-direction: column;
	}

	/* Name on the left, score on the right, the facts in two columns below: uses the whole width. */
	.identity {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem 1rem;
	}

	.title {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		min-width: 0;
	}

	.page-title {
		/* Same look whether this is the page's h1 or an h2 below the site heading. */
		font-size: 2rem;
		margin: 0;
	}

	.scoreline {
		display: flex;
		flex-wrap: wrap;
		align-items: baseline;
		gap: 0.75rem;
		margin: 0;
	}

	.rank,
	.meta,
	.section,
	.label,
	.place,
	.count {
		color: var(--color-muted);
	}

	.meta {
		flex: 1 1 100%;
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 0.55rem 1.2rem;
		margin: 0.4rem 0 0;
	}

	.meta dt {
		font-size: 0.85rem;
	}

	.meta dd {
		margin: 0.1rem 0 0;
		color: var(--color-fg);
	}

	/* The right column lines up with the score above and the bars below. */
	.meta > div:nth-child(even) {
		text-align: right;
	}

	.tier-label {
		color: var(--color-muted);
	}

	.tier-name {
		color: var(--color-fg);
	}

	.online {
		color: var(--color-accent);
	}

	.grades {
		margin-top: 1.2rem;
		display: flex;
		flex-direction: column;
		gap: 0.55rem;
	}

	.section {
		margin: 0 0 0.2rem;
		font-size: 0.95rem;
	}

	.grade {
		display: grid;
		grid-template-columns: 2rem 1fr;
		grid-template-rows: auto auto;
		column-gap: 0.7rem;
		row-gap: 0.28rem;
		align-items: center;
	}

	.grade-copy {
		display: flex;
		justify-content: space-between;
		gap: 0.8rem;
		align-items: baseline;
	}

	.grade-value {
		color: var(--color-gold);
		font-variant-numeric: tabular-nums;
	}

	.bar {
		grid-column: 2;
		height: 0.55rem;
		background: var(--slot-bg);
		border: 2px solid var(--color-border);
		box-shadow: inset 0 0 0 1px var(--button-shadow);
	}

	.bar i {
		display: block;
		height: 100%;
		background: var(--color-accent);
	}

	.bar.combat i {
		background: #c53a3a;
	}

	.bar.milestones i {
		background: #9b59d0;
	}

	.bar.mining i {
		background: #4fc3f7;
	}

	.bar.explore i {
		background: #55ff55;
	}

	.bar.produce i {
		background: #e6c35c;
	}

	.bar.survive i {
		background: #f28aa0;
	}

	.bar.flex i {
		background: #55ffff;
	}

	.tier-icon {
		display: inline-flex;
	}

	/* The dragon egg is black in the game; a purple glow keeps it visible on the dark theme too. */
	.tier-icon.tier-dragon_egg {
		filter: drop-shadow(0 0 3px #c25cf5);
	}

	.tier-fill.tier-dragon_egg {
		animation: dragon-shimmer 3s linear infinite;
	}

	@keyframes dragon-shimmer {
		to {
			background-position: -200% 0;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.tier-fill.tier-dragon_egg {
			animation: none;
		}
	}

	.filters {
		margin: 0 0 1.1rem;
	}

	.pack {
		margin-top: 0.55rem;
	}

	.pack-head {
		display: flex;
		flex-wrap: wrap;
		align-items: baseline;
		justify-content: space-between;
		gap: 0.6rem 1rem;
		margin: 0 0 0.7rem;
	}

	.fold {
		display: inline-flex;
		align-items: baseline;
		gap: 0.4rem;
		min-height: 0;
		padding: 0;
		color: var(--color-muted);
		font: inherit;
		text-align: left;
		text-shadow: inherit;
		background: none;
		border: none;
		box-shadow: none;
		cursor: pointer;
	}

	.fold:hover,
	.fold:focus-visible {
		color: var(--color-hover);
		background: none;
		border: none;
		box-shadow: none;
	}

	.chevron {
		display: inline-block;
		color: var(--color-gold);
		transform: rotate(0deg);
		transition: transform 0.12s linear;
	}

	.chevron.open {
		transform: rotate(90deg);
	}

	.pack-title {
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		font-size: 1rem;
	}

	.pack h2 {
		margin: 0 0 0.7rem;
		font-size: 1rem;
		color: var(--color-muted);
	}

	.count {
		margin-left: 0.45rem;
		font-size: 0.78rem;
	}

	.toggle {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
		color: var(--color-muted);
		font-size: 0.78rem;
		cursor: pointer;
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(8.4rem, 1fr));
		gap: 0.45rem;
	}

	.tile {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.28rem;
		width: 100%;
		height: 100%;
		padding: 0.7rem 0.45rem 0.55rem;
		text-align: center;
		min-height: 8.2rem;
	}

	.tile.zero {
		opacity: 0.42;
	}

	.glyph {
		display: grid;
		place-items: center;
		width: 2.6rem;
		height: 2.6rem;
		margin-bottom: 0.15rem;
	}

	.amount {
		color: var(--color-gold);
		font-variant-numeric: tabular-nums;
		font-size: 1.05rem;
	}

	.label,
	.place {
		font-size: 0.72rem;
		line-height: 1.25;
	}

	.place {
		color: var(--color-accent);
	}
</style>
