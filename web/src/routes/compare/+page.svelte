<script lang="ts">
	import { pageTitleTag } from '$lib/branding.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { advancementTabTitle, compareAdvancementTitles, matchesAdvancementQuery } from '$lib/advancements';
	import { getAdvancements, getLeaderboards, getPlayerProfile, getPlayers } from '$lib/api';
	import AdvancementTiles from '$lib/components/AdvancementTiles.svelte';
	import Loader from '$lib/components/Loader.svelte';
	import McItem from '$lib/components/McItem.svelte';
	import PlayerPicker from '$lib/components/PlayerPicker.svelte';
	import PlayerSkin from '$lib/components/PlayerSkin.svelte';
	import RankingScore from '$lib/components/RankingScore.svelte';
	import SearchField from '$lib/components/SearchField.svelte';
	import Tooltip from '$lib/components/Tooltip.svelte';
	import { boardHint, i18n, nameComparer, pageTitle, t, tDynamic } from '$lib/i18n/i18n.svelte';
	import { gradeItems, gradeOrder } from '$lib/playerStats';
	import { cappedResults, deferredValue } from '$lib/search.svelte';
	import type { AdvancementInfo, AdvancementTab, LeaderboardsResponse, PlayerDetail, PlayerSummary } from '$lib/types';
	import {
		vanillaBoardHref,
		vanillaGroups,
		vanillaHint,
		vanillaIcon,
		vanillaLabel,
		type VanillaGroup
	} from '$lib/vanillaStats';

	type Side = 'a' | 'b' | 'tie';

	type DuelRow = {
		id: string;
		icon: string;
		label: string;
		href?: string;
		a: number;
		b: number;
		displayA: string;
		displayB: string;
		coin: boolean;
		capped: boolean;
		lowerWins: boolean;
		tone: string;
		hint: string;
	};

	const vanillaLowerWins = new Set(['minecraft:deaths']);

	// Raw: replaced as a whole when loaded, never changed in place; deep reactivity would proxy every row.
	let catalog = $state.raw<LeaderboardsResponse | null>(null);
	let advancementCatalog = $state<AdvancementInfo[]>([]);
	let advancementTabs = $state<AdvancementTab[]>([]);
	let players = $state<PlayerSummary[]>([]);
	let playersReady = $state(false);
	let left = $state('');
	let right = $state('');
	// Raw: replaced as a whole when loaded, never changed in place; deep reactivity would proxy every row.
	let leftProfile = $state.raw<PlayerDetail | null>(null);
	let rightProfile = $state.raw<PlayerDetail | null>(null);
	let failed = $state(false);
	let leftFailed = $state(false);
	let rightFailed = $state(false);
	let leftLoading = $state(false);
	let rightLoading = $state(false);
	let query = $state('');
	const filterQuery = deferredValue(() => query);
	let exclusiveOpen = $state(true);
	let openVanilla = $state<Record<VanillaGroup, boolean>>({
		custom: false,
		killed: false,
		killed_by: false,
		mined: false,
		crafted: false,
		used: false,
		broken: false,
		picked_up: false,
		dropped: false
	});

	$effect(() => {
		playersReady = false;
		Promise.all([getPlayers(), getLeaderboards().catch(() => null), getAdvancements().catch(() => null)])
			.then(([data, boards, advancements]) => {
				players = data.players;
				catalog = boards;
				advancementCatalog = advancements?.advancements ?? [];
				advancementTabs = advancements?.tabs ?? [];
				failed = false;
			})
			.catch(() => {
				players = [];
				failed = true;
			})
			.finally(() => {
				playersReady = true;
			});
	});

	$effect(() => {
		left = page.url.searchParams.get('a') ?? '';
		right = page.url.searchParams.get('b') ?? '';
	});

	$effect(() => {
		const name = left;
		if (!name) {
			leftProfile = null;
			leftFailed = false;
			leftLoading = false;
			return;
		}
		leftProfile = null;
		leftFailed = false;
		leftLoading = true;
		getPlayerProfile(name)
			.then((data) => {
				if (left === name) {
					leftProfile = data;
				}
			})
			.catch(() => {
				if (left === name) {
					leftFailed = true;
				}
			})
			.finally(() => {
				if (left === name) {
					leftLoading = false;
				}
			});
	});

	$effect(() => {
		const name = right;
		if (!name) {
			rightProfile = null;
			rightFailed = false;
			rightLoading = false;
			return;
		}
		rightProfile = null;
		rightFailed = false;
		rightLoading = true;
		getPlayerProfile(name)
			.then((data) => {
				if (right === name) {
					rightProfile = data;
				}
			})
			.catch(() => {
				if (right === name) {
					rightFailed = true;
				}
			})
			.finally(() => {
				if (right === name) {
					rightLoading = false;
				}
			});
	});

	function sync(nextLeft = left, nextRight = right) {
		left = nextLeft;
		right = nextRight;
		const params = new URLSearchParams();
		if (nextLeft) {
			params.set('a', nextLeft);
		}
		if (nextRight) {
			params.set('b', nextRight);
		}
		goto(`/compare?${params.toString()}`, { replaceState: true, keepFocus: true });
	}

	function winner(a: number, b: number, lowerWins = false): Side {
		if (a === b) {
			return 'tie';
		}
		if (lowerWins) {
			return a < b ? 'a' : 'b';
		}
		return a > b ? 'a' : 'b';
	}

	function barWidth(value: number, other: number, capped: boolean) {
		if (capped) {
			return Math.min(100, Math.max(0, value));
		}
		const max = Math.max(value, other, 0.0001);
		return Math.min(100, (value / max) * 100);
	}

	function matchesNeedle(text: string, needle: string) {
		return text.toLowerCase().includes(needle);
	}

	function filterRows(rows: DuelRow[], needle: string) {
		if (!needle) {
			return rows;
		}
		return rows.filter((row) => matchesNeedle(row.label, needle) || matchesNeedle(row.id, needle));
	}

	function exclusiveAdvancements(have: string[], other: string[]) {
		const taken = new Set(other);
		const byId = new Map(advancementCatalog.map((item) => [item.id, item]));
		return have
			.filter((id) => !taken.has(id))
			.map(
				(id) =>
					byId.get(id) ?? {
						id,
						tab: '',
						title: id,
						description: '',
						icon: 'minecraft:experience_bottle',
						frame: 'task',
						hidden: false,
						holders: 0
					}
			)
			.sort(compareAdvancementTitles);
	}

	function filterExclusive(items: AdvancementInfo[], needle: string, sectionHit: boolean) {
		const matched =
			!needle || sectionHit
				? items
				: items.filter((item) =>
						matchesAdvancementQuery(item.id, item.title, item.description, needle)
					);
		return { matched: matched.length, items: cappedResults(matched, searching) };
	}

	function isOpen(group: VanillaGroup) {
		return searching || openVanilla[group];
	}

	function toggleVanilla(group: VanillaGroup) {
		if (searching) {
			return;
		}
		openVanilla = { ...openVanilla, [group]: !openVanilla[group] };
	}

	function exclusiveExpanded() {
		return searching || exclusiveOpen;
	}

	function toggleExclusive() {
		if (searching) {
			return;
		}
		exclusiveOpen = !exclusiveOpen;
	}

	const leftChoices = $derived(players.filter((player) => player.name !== right));
	const rightChoices = $derived(players.filter((player) => player.name !== left));
	const ready = $derived(Boolean(leftProfile && rightProfile && left !== right));
	const searching = $derived(filterQuery.current.trim().length > 0);

	const overviewRows = $derived.by((): DuelRow[] => {
		void i18n.locale;
		const aPlayer = leftProfile;
		const bPlayer = rightProfile;
		if (!aPlayer || !bPlayer) {
			return [];
		}
		return [
			{
				id: 'champion',
				icon: 'minecraft:nether_star',
				label: tDynamic('leaderboard', 'champion'),
				href: '/leaderboards/champion',
				a: aPlayer.championScore,
				b: bPlayer.championScore,
				displayA: aPlayer.championDisplay,
				displayB: bPlayer.championDisplay,
				coin: true,
				capped: true,
				lowerWins: false,
				tone: 'champion',
				hint: boardHint('champion')
			},
			{
				id: 'play-time',
				icon: 'minecraft:clock',
				label: t('player.playTime'),
				href: '/leaderboards/play-time',
				a: aPlayer.playHours,
				b: bPlayer.playHours,
				displayA: aPlayer.playHoursDisplay,
				displayB: bPlayer.playHoursDisplay,
				coin: false,
				capped: false,
				lowerWins: false,
				tone: 'play-time',
				hint: boardHint('play-time')
			},
			{
				id: 'score-per-hour',
				icon: 'minecraft:experience_bottle',
				label: t('player.scorePerHour'),
				href: '/leaderboards/efficient',
				a: aPlayer.scorePerHour,
				b: bPlayer.scorePerHour,
				displayA: aPlayer.scorePerHourDisplay,
				displayB: bPlayer.scorePerHourDisplay,
				coin: false,
				capped: false,
				lowerWins: false,
				tone: 'score-per-hour',
				hint: boardHint('efficient')
			},
			{
				id: 'advancements',
				icon: 'minecraft:experience_bottle',
				label: t('player.advancements'),
				href: '/advancements/most',
				a: aPlayer.advancements?.done ?? 0,
				b: bPlayer.advancements?.done ?? 0,
				displayA: t('advancements.doneOf', {
					done: aPlayer.advancements?.done ?? 0,
					total: aPlayer.advancements?.total ?? 0
				}),
				displayB: t('advancements.doneOf', {
					done: bPlayer.advancements?.done ?? 0,
					total: bPlayer.advancements?.total ?? 0
				}),
				coin: false,
				capped: false,
				lowerWins: false,
				tone: '',
				hint: boardHint('advancements')
			}
		];
	});

	const gradeRows = $derived.by((): DuelRow[] => {
		void i18n.locale;
		const aPlayer = leftProfile;
		const bPlayer = rightProfile;
		if (!aPlayer || !bPlayer) {
			return [];
		}
		return gradeOrder.map((id) => {
			const a = aPlayer.grades[id] ?? 0;
			const b = bPlayer.grades[id] ?? 0;
			return {
				id,
				icon: gradeItems[id],
				label: tDynamic('player.grade', id),
				a,
				b,
				displayA: String(Math.round(a)),
				displayB: String(Math.round(b)),
				coin: false,
				capped: true,
				lowerWins: false,
				tone: id,
				hint: ''
			};
		});
	});

	const statSections = $derived.by(() => {
		void i18n.locale;
		const aPlayer = leftProfile;
		const bPlayer = rightProfile;
		if (!aPlayer || !bPlayer) {
			return [];
		}
		return (catalog?.categories ?? [])
			.map((category) => ({
				id: category.id,
				title: tDynamic('category', category.id),
				rows: category.boards
					.filter((board) => board.compare)
					.map((board) => {
						const a = aPlayer.stats[board.id];
						const b = bPlayer.stats[board.id];
						return {
							id: board.id,
							icon: board.icon,
							label: tDynamic('leaderboard', board.id),
							href: `/leaderboards/${board.id}`,
							a: a?.value ?? 0,
							b: b?.value ?? 0,
							displayA: a?.display ?? '0',
							displayB: b?.display ?? '0',
							coin: false,
							capped: false,
							lowerWins: board.lowerWins,
							tone: '',
							hint: boardHint(board.id)
						};
					})
			}))
			.filter((section) => section.rows.length > 0);
	});

	const vanillaSections = $derived.by(() => {
		void i18n.locale;
		const aPlayer = leftProfile;
		const bPlayer = rightProfile;
		if (!aPlayer?.vanilla || !bPlayer?.vanilla) {
			return [];
		}
		return vanillaGroups.map((group) => {
			const leftRows = aPlayer.vanilla?.[group] ?? [];
			const rightRows = bPlayer.vanilla?.[group] ?? [];
			const leftMap = new Map(leftRows.map((row) => [row.id, row]));
			const rightMap = new Map(rightRows.map((row) => [row.id, row]));
			const ids = [...new Set([...leftMap.keys(), ...rightMap.keys()])];
			const rows: DuelRow[] = [];
			for (const id of ids) {
				const a = leftMap.get(id);
				const b = rightMap.get(id);
				const av = a?.value ?? 0;
				const bv = b?.value ?? 0;
				if (av <= 0 && bv <= 0) {
					continue;
				}
				rows.push({
					id,
					icon: vanillaIcon(group, id),
					label: vanillaLabel(group, id),
					href: vanillaBoardHref(group, id),
					a: av,
					b: bv,
					displayA: a?.display ?? '0',
					displayB: b?.display ?? '0',
					coin: false,
					capped: false,
					lowerWins: group === 'killed_by' || vanillaLowerWins.has(id),
					tone: '',
					hint: vanillaHint(group, id)
				});
			}
			// Each row already carries its name, so the sort doesn't look it up on every comparison.
			const compare = nameComparer();
			rows.sort(
				(leftRow, rightRow) =>
					Math.max(rightRow.a, rightRow.b) - Math.max(leftRow.a, leftRow.b) ||
					compare(leftRow.label, rightRow.label) ||
					leftRow.id.localeCompare(rightRow.id)
			);
			return {
				id: group,
				title: tDynamic('player.group', group),
				rows
			};
		});
	});

	const shownOverview = $derived.by(() => filterRows(overviewRows, filterQuery.current.trim().toLowerCase()));
	const shownGrades = $derived.by(() => {
		const needle = filterQuery.current.trim().toLowerCase();
		if (needle && matchesNeedle(t('player.grades'), needle)) {
			return gradeRows;
		}
		return filterRows(gradeRows, needle);
	});
	const shownStatSections = $derived.by(() => {
		const needle = filterQuery.current.trim().toLowerCase();
		if (!needle) {
			return statSections;
		}
		return statSections
			.map((section) => ({
				...section,
				rows:
					matchesNeedle(section.title, needle) || matchesNeedle(t('compare.stats'), needle)
						? section.rows
						: filterRows(section.rows, needle)
			}))
			.filter((section) => section.rows.length > 0);
	});
	const shownVanilla = $derived.by(() => {
		const needle = filterQuery.current.trim().toLowerCase();
		return vanillaSections
			.map((section) => {
				const matched =
					!needle || matchesNeedle(section.title, needle) ? section.rows : filterRows(section.rows, needle);
				return {
					...section,
					total: section.rows.length,
					matched: matched.length,
					rows: cappedResults(matched, searching)
				};
			})
			.filter((section) => section.matched > 0);
	});
	const exclusiveSides = $derived.by(() => {
		void i18n.locale;
		const aPlayer = leftProfile;
		const bPlayer = rightProfile;
		if (!aPlayer || !bPlayer) {
			return { onlyA: [] as AdvancementInfo[], onlyB: [] as AdvancementInfo[] };
		}
		return {
			onlyA: exclusiveAdvancements(aPlayer.advancements?.ids ?? [], bPlayer.advancements?.ids ?? []),
			onlyB: exclusiveAdvancements(bPlayer.advancements?.ids ?? [], aPlayer.advancements?.ids ?? [])
		};
	});
	const shownExclusive = $derived.by(() => {
		void i18n.locale;
		const needle = filterQuery.current;
		const lowered = needle.trim().toLowerCase();
		const sectionHit =
			Boolean(lowered) &&
			(matchesNeedle(t('compare.advancementsDiff'), lowered) ||
				matchesNeedle(t('compare.onlyAdvancements', { name: leftProfile?.name ?? '' }), lowered) ||
				matchesNeedle(t('compare.onlyAdvancements', { name: rightProfile?.name ?? '' }), lowered));
		return {
			onlyA: filterExclusive(exclusiveSides.onlyA, needle, sectionHit),
			onlyB: filterExclusive(exclusiveSides.onlyB, needle, sectionHit)
		};
	});
	// By tab, in the game's order, both sides next to each other: each tab with its own background, as on the
	// advancement pages. Advancements of a tab the catalog doesn't know come last, on stone.
	const exclusiveTabs = $derived.by(() => {
		const known = new Set(advancementTabs.map((tab) => tab.id));
		const groups: { tab: AdvancementTab; a: AdvancementInfo[]; b: AdvancementInfo[] }[] = [
			...advancementTabs,
			{ id: '', title: '', icon: 'minecraft:experience_bottle' }
		].map((tab) => {
			const inTab = (item: AdvancementInfo) => (tab.id ? item.tab === tab.id : !known.has(item.tab));
			return { tab, a: shownExclusive.onlyA.items.filter(inTab), b: shownExclusive.onlyB.items.filter(inTab) };
		});
		return groups.filter((group) => group.a.length > 0 || group.b.length > 0);
	});
	const showExclusive = $derived(shownExclusive.onlyA.matched + shownExclusive.onlyB.matched > 0);
	const hasMatches = $derived(
		shownOverview.length > 0 ||
			shownGrades.length > 0 ||
			shownStatSections.length > 0 ||
			shownVanilla.length > 0 ||
			showExclusive
	);

	const tally = $derived.by(() => {
		let leftWins = 0;
		let rightWins = 0;
		let ties = 0;
		const scored = [...overviewRows, ...gradeRows, ...statSections.flatMap((section) => section.rows)];
		for (const row of scored) {
			const side = winner(row.a, row.b, row.lowerWins);
			if (side === 'a') {
				leftWins += 1;
			} else if (side === 'b') {
				rightWins += 1;
			} else {
				ties += 1;
			}
		}
		return { leftWins, rightWins, ties };
	});
</script>

{#snippet duel(row: DuelRow)}
	{@const side = winner(row.a, row.b, row.lowerWins)}
	<div class="stat slot" class:win-a={side === 'a'} class:win-b={side === 'b'}>
		<div class="label">
			{#if row.href}
				<Tooltip text={row.hint} align="start">
					<a class="stat-link" href={row.href}>
						<McItem id={row.icon} />
						<span>{row.label}</span>
					</a>
				</Tooltip>
			{:else}
				<McItem id={row.icon} />
				<Tooltip text={row.hint} align="start" focusable={Boolean(row.hint)}>
					<span>{row.label}</span>
				</Tooltip>
			{/if}
		</div>
		<div class="values">
			<Tooltip text={row.hint} align="end">
				<span class="num a" class:hot={side === 'a'} class:dim={side === 'b'}>
					{#if row.coin}
						<RankingScore value={row.displayA} />
					{:else}
						{row.displayA}
					{/if}
				</span>
			</Tooltip>
			<div class="bars" aria-hidden="true">
				<div class="bar left {row.tone}">
					<i style="width: {barWidth(row.a, row.b, row.capped)}%"></i>
				</div>
				<div class="bar right {row.tone}">
					<i style="width: {barWidth(row.b, row.a, row.capped)}%"></i>
				</div>
			</div>
			<Tooltip text={row.hint} align="start">
				<span class="num b" class:hot={side === 'b'} class:dim={side === 'a'}>
					{#if row.coin}
						<RankingScore value={row.displayB} />
					{:else}
						{row.displayB}
					{/if}
				</span>
			</Tooltip>
		</div>
	</div>
{/snippet}

<svelte:head>
	<title>{pageTitle(t('nav.compare'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('compare.heading')}</svelte:element>
<p class="muted">{failed ? t('compare.loadError') : t('compare.lead')}</p>

{#if !playersReady}
	<Loader />
{:else}
	<div class="row">
		<div class="pick">
			<span>{t('compare.playerA')}</span>
			<PlayerPicker
				id="compare-a"
				label={t('compare.playerA')}
				value={left}
				players={leftChoices}
				placeholder={t('compare.choose')}
				onselect={(name) => sync(name, right)}
			/>
		</div>
		<div class="pick">
			<span>{t('compare.playerB')}</span>
			<PlayerPicker
				id="compare-b"
				label={t('compare.playerB')}
				value={right}
				players={rightChoices}
				placeholder={t('compare.choose')}
				onselect={(name) => sync(left, name)}
			/>
		</div>
	</div>

	<div class="cols">
		<section class="panel fighter">
			{#if leftProfile}
				<div class="who">
					<PlayerSkin uuid={leftProfile.uuid} variant="head" />
					<h2><a class="name-link" href="/players/{encodeURIComponent(leftProfile.name)}">{leftProfile.name}</a></h2>
				</div>
				<PlayerSkin uuid={leftProfile.uuid} variant="body" />
				<p class="scoreline">
					<Tooltip text={boardHint('champion')} focusable>
						<RankingScore value={leftProfile.championDisplay} />
					</Tooltip>
				</p>
				<p class="meta">{t('player.rankOf', { rank: leftProfile.championRank, total: leftProfile.trackedPlayers })}</p>
			{:else}
				<h2>{left || t('compare.slotA')}</h2>
				{#if leftFailed}
					<p class="muted">{t('compare.statsError')}</p>
				{:else if leftLoading}
					<Loader compact />
				{:else}
					<p class="muted">{left ? t('compare.waiting') : t('compare.unselected')}</p>
				{/if}
			{/if}
		</section>
		<section class="panel fighter">
			{#if rightProfile}
				<div class="who">
					<PlayerSkin uuid={rightProfile.uuid} variant="head" />
					<h2><a class="name-link" href="/players/{encodeURIComponent(rightProfile.name)}">{rightProfile.name}</a></h2>
				</div>
				<PlayerSkin uuid={rightProfile.uuid} variant="body" />
				<p class="scoreline">
					<Tooltip text={boardHint('champion')} focusable>
						<RankingScore value={rightProfile.championDisplay} />
					</Tooltip>
				</p>
				<p class="meta">{t('player.rankOf', { rank: rightProfile.championRank, total: rightProfile.trackedPlayers })}</p>
			{:else}
				<h2>{right || t('compare.slotB')}</h2>
				{#if rightFailed}
					<p class="muted">{t('compare.statsError')}</p>
				{:else if rightLoading}
					<Loader compact />
				{:else}
					<p class="muted">{right ? t('compare.waiting') : t('compare.unselected')}</p>
				{/if}
			{/if}
		</section>
	</div>

	{#if left && right && left === right}
		<p class="muted">{t('compare.same')}</p>
	{:else if left && right && (leftLoading || rightLoading)}
		<Loader />
	{:else if ready}
	<p class="tally">
		{t('compare.tally', {
			left: leftProfile?.name ?? '',
			leftWins: tally.leftWins,
			ties: tally.ties,
			right: rightProfile?.name ?? '',
			rightWins: tally.rightWins
		})}
	</p>
	<div class="filters">
		<SearchField bind:value={query} placeholder={t('player.search')} />
	</div>
	{#if hasMatches}
		<div class="duel">
			{#each shownOverview as row}
				{@render duel(row)}
			{/each}
			{#if shownGrades.length > 0}
				<p class="section">{t('player.grades')}</p>
				{#each shownGrades as row}
					{@render duel(row)}
				{/each}
			{/if}
			{#if showExclusive}
				<div class="pack">
					<button
						class="fold heading"
						type="button"
						aria-expanded={exclusiveExpanded()}
						onclick={toggleExclusive}
					>
						<span class="chevron" class:open={exclusiveExpanded()}>▸</span>
						<span>
							{t('compare.advancementsDiff')}
							<span class="count"
								>{shownExclusive.onlyA.matched + shownExclusive.onlyB.matched}</span
							>
						</span>
					</button>
					{#if exclusiveExpanded()}
						<div class="exclusive sides">
							<p class="section sub">
								{t('compare.onlyAdvancements', { name: leftProfile?.name ?? '' })}
								<span class="count">{shownExclusive.onlyA.matched}</span>
							</p>
							<p class="section sub">
								{t('compare.onlyAdvancements', { name: rightProfile?.name ?? '' })}
								<span class="count">{shownExclusive.onlyB.matched}</span>
							</p>
						</div>
						{#each exclusiveTabs as group (group.tab.id)}
							<p class="tab-title">
								<McItem id={group.tab.icon} compact />
								{group.tab.id ? advancementTabTitle(group.tab.id, group.tab.title) : t('compare.otherAdvancements')}
							</p>
							<div class="exclusive">
								{#each [{ items: group.a, name: leftProfile?.name ?? '' }, { items: group.b, name: rightProfile?.name ?? '' }] as side, index (index)}
									<div class="exclusive-col">
										<!-- On phones the sides stack, so each says whose it is. -->
										<p class="section sub side-name">
											{t('compare.onlyAdvancements', { name: side.name })}
											<span class="count">{side.items.length}</span>
										</p>
										{#if side.items.length > 0}
											<AdvancementTiles items={side.items} background={group.tab.background ?? ''} />
										{:else}
											<p class="muted empty">{t('compare.onlyAdvancementsEmpty')}</p>
										{/if}
									</div>
								{/each}
							</div>
						{/each}
					{/if}
				</div>
			{/if}
			{#if shownStatSections.length > 0}
				<p class="section">{t('compare.stats')}</p>
				{#each shownStatSections as section}
					<p class="section sub">{section.title}</p>
					{#each section.rows as row}
						{@render duel(row)}
					{/each}
				{/each}
			{/if}
			{#each shownVanilla as section}
				<div class="pack">
					<button
						class="fold"
						type="button"
						aria-expanded={isOpen(section.id)}
						onclick={() => toggleVanilla(section.id)}
					>
						<span class="chevron" class:open={isOpen(section.id)}>▸</span>
						<span>
							{section.title}
							<span class="count">{t('player.shown', { shown: section.rows.length, total: section.total })}</span>
						</span>
					</button>
					{#if isOpen(section.id)}
						{#each section.rows as row}
							{@render duel(row)}
						{/each}
					{/if}
				</div>
			{/each}
		</div>
	{:else}
		<p class="muted">{t('player.noMatches')}</p>
	{/if}
	{:else if !left || !right}
		<p class="muted hint">{t('compare.needTwo')}</p>
	{/if}
{/if}

<style>
	.page-title {
		/* Same look whether this is the page's h1 or an h2 below the site heading. */
		font-size: 2rem;
		margin: 0.67em 0;
		text-align: center;
	}

	.muted {
		color: var(--color-muted);
		text-align: center;
	}

	.hint {
		margin-top: 1.4rem;
	}

	.row,
	.cols {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.7rem;
		margin-top: 1.25rem;
	}

	.pick {
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
		color: var(--color-muted);
	}

	.fighter {
		padding: 0.85rem 0.95rem;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.7rem;
		text-align: center;
	}

	.who {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.55rem;
	}

	/* The name leads to the player's page. */
	.name-link {
		text-decoration: underline dotted 2px;
		text-underline-offset: 0.25em;
	}

	.name-link:hover,
	.name-link:focus-visible {
		color: var(--color-hover);
	}

	/* Titles get room for two lines, so a tile with a long title isn't taller than its neighbour on the other side and
	   the rows line up. */
	.exclusive :global(.tile .label) {
		min-height: 2.5em;
	}

	h2:not(.page-title) {
		margin: 0;
	}

	.scoreline {
		margin: 0;
	}

	.meta {
		margin: 0;
		color: var(--color-muted);
	}

	.tally {
		margin: 1.4rem 0 0.8rem;
		text-align: center;
		color: var(--color-gold);
	}

	.filters {
		margin: 0 0 0.85rem;
	}

	.section {
		margin: 1.1rem 0 0.45rem;
		color: var(--color-gold);
	}

	.section.sub {
		margin: 0.85rem 0 0.4rem;
		font-size: 0.95rem;
		color: var(--color-muted);
	}

	.duel {
		display: flex;
		flex-direction: column;
		gap: 0.45rem;
	}

	.pack {
		display: flex;
		flex-direction: column;
		gap: 0.45rem;
		margin-top: 0.4rem;
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

	.fold.heading {
		color: var(--color-gold);
	}

	.chevron {
		display: inline-block;
		width: 1rem;
		color: var(--color-gold);
		transform: rotate(0deg);
		transition: transform 0.12s linear;
	}

	.chevron.open {
		transform: rotate(90deg);
	}

	.count {
		margin-left: 0.45rem;
		color: var(--color-muted);
	}

	.exclusive {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.7rem;
	}

	.exclusive-col {
		min-width: 0;
	}

	.tab-title {
		display: flex;
		align-items: center;
		gap: 0.45rem;
		margin: 1rem 0 0.45rem;
		color: var(--color-gold);
	}

	/* Whose side: once above both columns; on phones, where they stack, above each. */
	.side-name {
		display: none;
	}

	.exclusive .empty {
		margin: 0.2rem 0 0;
		text-align: left;
	}

	.stat {
		display: grid;
		gap: 0.45rem;
		padding: 0.55rem 0.7rem;
	}

	.label {
		display: flex;
		align-items: center;
		gap: 0.55rem;
	}

	.stat-link {
		display: inline-flex;
		align-items: center;
		gap: 0.55rem;
		min-height: 0;
		padding: 0;
		color: inherit;
		font: inherit;
		text-align: left;
		text-decoration: none;
		text-shadow: inherit;
		background: none;
		border: none;
		box-shadow: none;
	}

	.stat-link:hover,
	.stat-link:focus-visible {
		color: var(--color-hover);
		background: none;
		border: none;
		box-shadow: none;
	}

	.values {
		display: grid;
		grid-template-columns: minmax(4.5rem, auto) 1fr minmax(4.5rem, auto);
		gap: 0.55rem;
		align-items: center;
	}

	.num {
		font-variant-numeric: tabular-nums;
	}

	.num.a {
		text-align: right;
	}

	.num.hot {
		color: var(--color-gold);
	}

	.num.dim {
		color: var(--color-muted);
	}

	.bars {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.2rem;
		height: 0.55rem;
	}

	.bar {
		height: 100%;
		background: var(--slot-bg);
		border: 2px solid var(--color-border);
		box-shadow: inset 0 0 0 1px var(--button-shadow);
		overflow: hidden;
	}

	.bar.left {
		display: flex;
		justify-content: flex-end;
	}

	.bar i {
		display: block;
		height: 100%;
		background: var(--color-accent);
	}

	.bar.champion i,
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

	.bar.produce i,
	.bar.play-time i {
		background: #e6c35c;
	}

	.bar.survive i,
	.bar.score-per-hour i {
		background: #f28aa0;
	}

	.bar.flex i {
		background: #55ffff;
	}

	@media (max-width: 700px) {
		.row,
		.cols,
		.values,
		.exclusive {
			grid-template-columns: 1fr;
		}

		.exclusive.sides {
			display: none;
		}

		.side-name {
			display: block;
		}

		.num.a,
		.num.b {
			text-align: left;
		}
	}
</style>
