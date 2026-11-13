<script lang="ts">
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import LevelBar from '#lib/components/LevelBar.svelte';
	import PlayerAvatar from '#lib/components/PlayerAvatar.svelte';
	import AdvancementTierRow from '#lib/components/player/AdvancementTierRow.svelte';
	import PlayerFacts from '#lib/components/player/PlayerFacts.svelte';
	import PlayerSkills from '#lib/components/player/PlayerSkills.svelte';
	import { formatValue } from '#lib/format.ts';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import type { AdvancementsResponse, PlayerDetail } from '#lib/types.ts';

	let { player, catalog }: { player: PlayerDetail; catalog: AdvancementsResponse | null } = $props();

	const advancementTotal = $derived(player.advancements.total || catalog?.total || 0);
</script>

<section class="hero">
	<div class="head">
		<div class="identity">
			<PlayerAvatar uuid={player.uuid} variant="head" />
			<div class="who">
				<svelte:element this={pageTitleTag()} class="page-title name player-name">{player.name}</svelte:element>
				{#if player.sameName.length > 0}
					<!-- Floodgate can give two Bedrock players the same name. -->
					<div class="same-name">
						{t('player.sameName')}
						{#each player.sameName as other, index (other.uuid)}{index > 0 ? ', ' : ''}<a
								href="/players/{encodeURIComponent(other.uuid)}">{other.name} ({other.uuid.slice(-8)})</a
							>{/each}
					</div>
				{/if}
			</div>
		</div>
		<div class="level-box">
			<LevelBar
				level={player.level}
				display={formatValue(player.level, 'level')}
				rank={t('player.rankOf', { rank: player.levelRank, total: player.trackedPlayers })}
				place={player.levelRank}
			/>
		</div>
		<PlayerFacts {player} />
	</div>

	<div class="portrait">
		<PlayerAvatar uuid={player.uuid} variant="body" />
	</div>

	<div class="info">
		<PlayerSkills skills={player.skills} />
		{#if advancementTotal > 0}
			<AdvancementTierRow done={player.advancements.done} total={advancementTotal} />
		{/if}
	</div>
</section>

<style>
	.hero {
		display: grid;
		/* The skin as wide as leaves the skills room for their part lines in one line (in English). */
		grid-template-columns: 23rem minmax(0, 1fr);
		gap: 1.5rem 1.25rem;
		align-items: stretch;
		margin-bottom: 1.8rem;
	}

	/* The head and name level with the big level number, not with the middle of the whole level box. */
	.head {
		display: grid;
		grid-column: 1 / -1;
		grid-template-columns: minmax(0, 1fr) auto;
		gap: 0.8rem 1.5rem;
		align-items: start;
	}

	.identity {
		display: flex;
		gap: 0.75rem;
		align-items: center;
		min-width: 0;
	}

	/* Side by side with the level: the name line is a little taller than the head, so this brings the head level with
	   the level number. */
	@media (min-width: 681px) {
		.identity {
			margin-top: -0.2rem;
		}
	}

	.who {
		min-width: 0;
	}

	/* On phones the longest Minecraft name (16 characters) fits next to the head. */
	.name {
		margin: 0;
		overflow: hidden;
		font-size: clamp(1.25rem, 6.5vw, 2rem);
		text-overflow: ellipsis;
		white-space: nowrap;
		overflow-wrap: normal;
		hyphens: manual;
	}

	.same-name {
		margin-top: 0.3rem;
		color: var(--color-muted);
		font-size: 0.8rem;
	}

	.same-name a {
		text-decoration: underline 2px dotted;
		text-underline-offset: 0.25em;
	}

	.same-name a:hover,
	.same-name a:focus-visible {
		color: var(--color-hover);
	}

	/* The level number no bigger than the name next to it; on phones, where it sits below, a little smaller. */
	.level-box {
		--level-size: clamp(1.15rem, 5.5vw, 2rem);
		min-width: min(364px, 60vw);
	}

	.portrait {
		display: flex;
		min-height: 20rem;
	}

	.portrait :global(.skin),
	.portrait :global(.stage) {
		width: 100%;
		height: 100%;
		min-height: 20rem;
	}

	.info {
		display: flex;
		flex-direction: column;
		min-width: 0;
	}

	/* The advancements below the skills, when there are any. */
	.info > :global(* + *) {
		margin-top: 1.2rem;
	}

	@media (max-width: 680px) {
		.hero,
		.head {
			grid-template-columns: minmax(0, 1fr);
		}

		.level-box :global(.level) {
			justify-items: start;
		}

		.portrait {
			height: min(26rem, 110vw);
		}
	}
</style>
