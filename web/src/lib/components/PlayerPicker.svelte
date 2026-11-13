<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import PlayerAvatar from '#lib/components/PlayerAvatar.svelte';
	import SearchField from '#lib/components/SearchField.svelte';
	import { closeMenu, headerMenu, registerMenuRoot, toggleMenu } from '#lib/components/headerMenu.svelte.ts';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import { playerKey } from '#lib/players.ts';
	import type { PlayerSummary } from '#lib/types.ts';

	let {
		id,
		label,
		value,
		players,
		placeholder,
		onselect
	}: {
		// The menu's ID in headerMenu: one open menu at a time.
		id: string;
		label: string;
		// The chosen player's key, '' for none.
		value: string;
		players: PlayerSummary[];
		placeholder: string;
		onselect: (key: string) => void;
	} = $props();

	let query = $state('');

	const open = $derived(headerMenu.open === id);
	const choices = $derived(
		players.map((player) => {
			const key = playerKey(player.name, player.uuid);
			// Where several players share the name, the end of the UUID tells them apart.
			const text = key === player.name ? player.name : `${player.name} (${player.uuid.slice(-8)})`;
			return { player, key, text };
		})
	);
	const chosen = $derived(choices.find((choice) => choice.key === value));
	const needle = $derived(query.trim().toLowerCase());
	const matches = $derived(
		needle ? choices.filter((choice) => choice.player.name.toLowerCase().includes(needle)) : choices
	);

	// Closing clears the search, so the menu opens with the whole list again.
	$effect(() => {
		if (!open) {
			query = '';
		}
	});

	function choose(key: string) {
		onselect(key);
		closeMenu();
	}

	function onSearchKey(event: KeyboardEvent) {
		if (event.key === 'Enter' && matches.length === 1) {
			event.preventDefault();
			choose(matches[0].key);
		}
	}
</script>

<div class="wrap" class:open use:registerMenuRoot>
	<button
		type="button"
		class="mc-btn trigger"
		aria-haspopup="listbox"
		aria-expanded={open}
		aria-label="{label}: {chosen?.text ?? (value || placeholder)}"
		onclick={() => toggleMenu(id)}
	>
		{#if chosen}
			<PlayerAvatar uuid={chosen.player.uuid} variant="head" scale={5} />
			<span class="text">{chosen.text}</span>
		{:else}
			<span class="text">{value || placeholder}</span>
		{/if}
		<span class="chevron" class:up={open}><Icon name="chevron" /></span>
	</button>
	{#if open}
		<div class="menu panel">
			<div class="search">
				<SearchField
					bind:value={query}
					placeholder={t('ranking.search')}
					label={t('ranking.search')}
					autofocus
					onkeydown={onSearchKey}
				/>
			</div>
			<ul class="list" role="listbox" aria-label={label}>
				{#if !needle}
					<li role="none">
						<button
							type="button"
							class="option"
							class:on={value === ''}
							role="option"
							aria-selected={value === ''}
							onclick={() => choose('')}>{placeholder}</button
						>
					</li>
				{/if}
				{#each matches as choice (choice.key)}
					<li role="none">
						<button
							type="button"
							class="option"
							class:on={choice.key === value}
							role="option"
							aria-selected={choice.key === value}
							onclick={() => choose(choice.key)}
						>
							<PlayerAvatar uuid={choice.player.uuid} variant="head" scale={5} />
							<span class="text">{choice.text}</span>
						</button>
					</li>
				{/each}
				{#if needle && matches.length === 0}
					<li role="none" class="none">{t('player.noMatches')}</li>
				{/if}
			</ul>
		</div>
	{/if}
</div>

<style>
	.wrap {
		position: relative;
		width: 100%;
	}

	/* Above the other picker and the page while open, so its menu isn't covered. */
	.wrap.open {
		z-index: 40;
	}

	.trigger {
		position: relative;
		gap: 0.55rem;
		justify-content: center;
		width: 100%;
	}

	.chevron {
		position: absolute;
		top: 50%;
		right: 0.8rem;
		display: flex;
		transform: translateY(-50%);
	}

	.chevron.up :global(.glyph) {
		transform: rotate(180deg);
	}

	.menu {
		position: absolute;
		top: calc(100% + 0.35rem);
		left: 0;
		right: 0;
		z-index: 30;
		display: flex;
		flex-direction: column;
		max-height: 18rem;
		padding: 0.2rem;
		border: 2px solid var(--color-border);
		/* Opaque like tooltips: the list lies over the page. */
		background: var(--color-tooltip);
	}

	.search {
		flex-shrink: 0;
		margin-bottom: 0.2rem;
	}

	.search :global(input) {
		min-height: 2.2rem;
	}

	.list {
		min-height: 0;
		margin: 0;
		padding: 0;
		overflow-y: auto;
		list-style: none;
	}

	.list li + li {
		margin-top: 0.2rem;
	}

	.option {
		gap: 0.55rem;
		justify-content: center;
		width: 100%;
		min-height: 2.8rem;
	}

	.none {
		padding: 0.55rem 0.7rem;
		color: var(--color-muted);
	}
</style>
