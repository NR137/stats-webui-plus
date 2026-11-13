<script lang="ts">
	import Tooltip from '#lib/components/Tooltip.svelte';
	import { formatValue } from '#lib/format.ts';
	import { boardHint, formatRelative, formatWhen, t, type MessageKey } from '#lib/i18n/i18n.svelte.ts';
	import type { PlayerDetail, Time } from '#lib/types.ts';

	let { player }: { player: PlayerDetail } = $props();

	const BOARDS = ['deaths', 'current-life', 'distance-total', 'hostile-kills'];

	const boards = $derived(
		BOARDS.flatMap((id) => {
			const board = player.boards[id];
			return board ? [{ id, value: formatValue(board.value, board.unit) }] : [];
		})
	);

	// Players from before the mod have times worked out from the game's files: marked as estimates.
	function timeText(ms: Time, estimated: boolean): string {
		if (!ms) {
			return t('player.unknown');
		}
		return (estimated ? '≈ ' : '') + formatWhen(ms);
	}

	function timeHint(ms: Time, estimated: boolean, note: MessageKey): string {
		if (!ms) {
			return '';
		}
		return estimated ? `${formatRelative(ms)} · ${t(note)}` : formatRelative(ms);
	}
</script>

<dl class="facts">
	<div>
		<dt>{t('player.playTime')}</dt>
		<dd>
			<Tooltip text={boardHint('play-time')} align="start" focusable>
				{formatValue(player.playHours, 'hours')}
			</Tooltip>
		</dd>
	</div>
	<div>
		<dt>{t('player.actionsPerHour')}</dt>
		<dd>
			<Tooltip text={boardHint('grinders')} align="start" focusable>
				{formatValue(player.actionsPerHour, 'actions_per_hour')}
			</Tooltip>
		</dd>
	</div>
	<div>
		<dt>{t('player.firstJoin')}</dt>
		<dd>
			<Tooltip
				text={timeHint(player.firstJoin, player.firstJoinEstimated, 'player.firstJoinEstimate')}
				align="start"
				focusable
			>
				{timeText(player.firstJoin, player.firstJoinEstimated)}
			</Tooltip>
		</dd>
	</div>
	<div>
		<dt>{t('player.lastSeen')}</dt>
		<dd>
			{#if player.online}
				<span class="online">● {t('player.onlineNow')}</span>
			{:else}
				<Tooltip
					text={timeHint(player.lastLogout, player.lastLogoutEstimated, 'player.lastSeenEstimate')}
					align="end"
					focusable
				>
					{timeText(player.lastLogout, player.lastLogoutEstimated)}
				</Tooltip>
			{/if}
		</dd>
	</div>
	{#each boards as board (board.id)}
		<div>
			<dt>{t(`boards.${board.id}.name` as MessageKey)}</dt>
			<dd>
				<Tooltip text={boardHint(board.id)} align="start" focusable>
					<a href="/leaderboards/{board.id}">{board.value}</a>
				</Tooltip>
			</dd>
		</div>
	{/each}
</dl>

<style>
	.facts {
		display: grid;
		grid-column: 1 / -1;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 0.55rem 1.2rem;
		margin: 0;
	}

	dt {
		color: var(--color-muted);
		font-size: 0.85rem;
	}

	dd {
		margin: 0.1rem 0 0;
		color: var(--color-fg);
	}

	a {
		color: inherit;
	}

	.online {
		color: var(--color-accent);
	}

	@media (max-width: 680px) {
		.facts {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}
</style>
