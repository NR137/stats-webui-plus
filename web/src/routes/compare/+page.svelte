<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { tick } from 'svelte';
	import { getAdvancements, getLeaderboards, getPlayers } from '#lib/api.ts';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import Loader from '#lib/components/Loader.svelte';
	import PlayerPicker from '#lib/components/PlayerPicker.svelte';
	import Comparison from '#lib/components/compare/Comparison.svelte';
	import PlayerCard from '#lib/components/compare/PlayerCard.svelte';
	import { PlayerSide } from '#lib/components/compare/side.svelte.ts';
	import { pageTitle, t } from '#lib/i18n/i18n.svelte.ts';
	import { playerKey } from '#lib/players.ts';
	import { scrollToContent } from '#lib/scrollToContent.ts';
	import type { AdvancementsResponse, LeaderboardsResponse, PlayerSummary } from '#lib/types.ts';

	// The keys follow the address, so back and forward change the players.
	const keyA = $derived(page.url.searchParams.get('a') ?? '');
	const keyB = $derived(page.url.searchParams.get('b') ?? '');

	const sideA = new PlayerSide(() => keyA);
	const sideB = new PlayerSide(() => keyB);

	let loading = $state(true);
	let failed = $state(false);
	let players = $state<PlayerSummary[]>([]);
	let leaderboards = $state<LeaderboardsResponse | null>(null);
	let advancements = $state<AdvancementsResponse | null>(null);

	// The boards and the advancements only add sections: without them the page still compares.
	Promise.all([
		getPlayers(),
		getLeaderboards().catch(() => null),
		getAdvancements().catch(() => null)
	]).then(
		([list, boards, catalog]) => {
			players = list.players;
			leaderboards = boards;
			advancements = catalog;
			loading = false;
		},
		() => {
			failed = true;
			loading = false;
		}
	);

	const playersA = $derived(players.filter((player) => playerKey(player.name, player.uuid) !== keyB));
	const playersB = $derived(players.filter((player) => playerKey(player.name, player.uuid) !== keyA));
	const same = $derived(keyA !== '' && keyA === keyB);
	const both = $derived(
		sideA.status === 'loaded' && sideB.status === 'loaded' && sideA.player && sideB.player
			? { a: sideA.player, b: sideB.player }
			: null
	);

	// Set by a choice that completes the pair: once both are loaded and compared, the page scrolls to the content as
	// when it opens, once per choice.
	let scrollPending = $state(false);

	$effect(() => {
		if (!scrollPending) {
			return;
		}
		if (same || sideA.status === 'failed' || sideB.status === 'failed') {
			scrollPending = false;
		} else if (both) {
			scrollPending = false;
			tick().then(() => scrollToContent());
		}
	});

	function sync(side: 'a' | 'b', key: string) {
		const next = { a: side === 'a' ? key : keyA, b: side === 'b' ? key : keyB };
		const params = new URLSearchParams();
		for (const [name, value] of Object.entries(next)) {
			if (value) {
				params.set(name, value);
			}
		}
		scrollPending = next.a !== '' && next.b !== '';
		// Keeps the focus where it is (Tab goes on from the picker), but starts at the top again: scrollToContent only
		// scrolls from there, once both players are in.
		window.scrollTo(0, 0);
		goto('/compare?' + params, { replace: true, reset: false });
	}
</script>

<svelte:head>
	<title>{pageTitle(t('menu.compare'))}</title>
</svelte:head>

<svelte:element this={pageTitleTag()} class="page-title">{t('compare.heading')}</svelte:element>
<p class="lead">{failed ? t('site.loadError') : t('compare.lead')}</p>

{#if loading}
	<Loader />
{:else}
	<div class="pair">
		<div class="picker">
			<span>{t('compare.playerA')}</span>
			<PlayerPicker
				id="compare-a"
				label={t('compare.playerA')}
				value={keyA}
				players={playersA}
				placeholder={t('compare.choose')}
				onselect={(key) => sync('a', key)}
			/>
		</div>
		<div class="picker">
			<span>{t('compare.playerB')}</span>
			<PlayerPicker
				id="compare-b"
				label={t('compare.playerB')}
				value={keyB}
				players={playersB}
				placeholder={t('compare.choose')}
				onselect={(key) => sync('b', key)}
			/>
		</div>
	</div>

	<div class="pair">
		<PlayerCard key={keyA} fallback={t('compare.slotA')} status={sideA.status} player={sideA.player} />
		<PlayerCard key={keyB} fallback={t('compare.slotB')} status={sideB.status} player={sideB.player} />
	</div>

	{#if same}
		<p class="muted">{t('compare.same')}</p>
	{:else if both}
		<Comparison a={both.a} b={both.b} {leaderboards} {advancements} />
	{:else if keyA && keyB}
		<!-- A side that failed says so on its card. -->
		{#if sideA.status === 'loading' || sideB.status === 'loading'}
			<Loader />
		{/if}
	{:else}
		<p class="muted need">{t('compare.needTwo')}</p>
	{/if}
{/if}

<style>
	.page-title {
		margin: 0.67em 0;
		font-size: 2rem;
		text-align: center;
	}

	.muted {
		color: var(--color-muted);
		text-align: center;
	}

	/* The pickers, and the cards below them alike. */
	.pair {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.7rem;
		margin-top: 1.25rem;
	}

	.picker {
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
		color: var(--color-muted);
	}

	.need {
		margin-top: 1.4rem;
	}

	@media (max-width: 700px) {
		.pair {
			grid-template-columns: 1fr;
		}
	}
</style>
