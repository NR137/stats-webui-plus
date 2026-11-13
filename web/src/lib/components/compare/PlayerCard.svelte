<script lang="ts">
	import LevelBar from '#lib/components/LevelBar.svelte';
	import Loader from '#lib/components/Loader.svelte';
	import PlayerAvatar from '#lib/components/PlayerAvatar.svelte';
	import { formatValue } from '#lib/format.ts';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import { playerPath } from '#lib/players.ts';
	import type { PlayerDetail } from '#lib/types.ts';
	import type { SideStatus } from './side.svelte';

	let {
		key,
		fallback,
		status,
		player
	}: {
		// The chosen player's key, '' for none.
		key: string;
		// The side's name while nobody is chosen ("A").
		fallback: string;
		status: SideStatus;
		player: PlayerDetail | null;
	} = $props();
</script>

<section class="card panel">
	{#if status === 'loaded' && player}
		<div class="who">
			<PlayerAvatar uuid={player.uuid} variant="head" />
			<h2><a class="name" href={playerPath(player.name, player.uuid)}>{player.name}</a></h2>
		</div>
		<PlayerAvatar uuid={player.uuid} variant="body" />
		<LevelBar
			level={player.level}
			display={formatValue(player.level, 'level')}
			rank={t('player.rankOf', { rank: player.levelRank, total: player.trackedPlayers })}
			place={player.levelRank}
			align="center"
		/>
	{:else}
		<h2>{key || fallback}</h2>
		<p class="muted">
			{#if status === 'failed'}
				{t('site.loadError')}
			{:else if status === 'loading'}
				<Loader compact />
			{:else if key}
				{t('compare.waiting')}
			{:else}
				{t('compare.unselected')}
			{/if}
		</p>
	{/if}
</section>

<style>
	.card {
		display: flex;
		flex-direction: column;
		gap: 0.7rem;
		align-items: center;
		padding: 0.85rem 0.95rem;
		text-align: center;
	}

	h2 {
		margin: 0;
	}

	.who {
		display: flex;
		gap: 0.55rem;
		align-items: center;
		justify-content: center;
	}

	.name {
		text-decoration: underline dotted 2px;
		text-underline-offset: 0.25em;
	}

	.name:hover,
	.name:focus-visible {
		color: var(--color-hover);
	}

	.muted {
		color: var(--color-muted);
		text-align: center;
	}
</style>
