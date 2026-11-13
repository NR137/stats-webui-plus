<script lang="ts" module>
	export type HotbarEntry = { href: string; label: string; icon: string; newTab?: boolean; active: boolean };
</script>

<script lang="ts">
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import { closeMenu, headerMenu, registerMenuRoot, toggleMenu } from '#lib/components/headerMenu.svelte.ts';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import { tabAttributes } from '#lib/menu.ts';

	// The site's pages as the game's hotbar: a slot per page with its item icon and name, the current one framed like
	// the selected slot. What doesn't fit in one row goes into a last slot, "More", that opens a list of the rest.
	let { entries, label }: { entries: HotbarEntry[]; label: string } = $props();

	// A slot's width in rem (see .slot), and how much two neighbours overlap (their shared 3px border).
	const SLOT_REM = 9.4;
	const OVERLAP_PX = 3;

	let row = $state<HTMLElement>();
	let capacity = $state(Infinity);

	$effect(() => {
		const node = row;
		if (!node) {
			return;
		}
		const measure = () => {
			const rem = parseFloat(getComputedStyle(document.documentElement).fontSize);
			const slot = SLOT_REM * rem - OVERLAP_PX;
			capacity = Math.max(2, Math.floor((node.clientWidth + OVERLAP_PX) / slot));
		};
		const observer = new ResizeObserver(measure);
		observer.observe(node);
		measure();
		return () => observer.disconnect();
	});

	const overflows = $derived(entries.length > capacity);
	const shown = $derived(overflows ? entries.slice(0, capacity - 1) : entries);
	const rest = $derived(overflows ? entries.slice(capacity - 1) : []);
	const moreOpen = $derived(headerMenu.open === 'hotbar-more');
</script>

{#snippet slot(entry: HotbarEntry)}
	<a
		class="slot"
		class:on={entry.active}
		aria-current={entry.active ? 'page' : undefined}
		href={entry.href}
		{...tabAttributes(entry.newTab)}
	>
		<ItemIcon id={entry.icon} />
		<span class="name">{entry.label}</span>
	</a>
{/snippet}

<nav class="hotbar" aria-label={label} bind:this={row}>
	{#each shown as entry (entry.href)}
		{@render slot(entry)}
	{/each}
	{#if overflows}
		<div class="more-wrap" use:registerMenuRoot>
			<button
				type="button"
				class="slot more"
				class:on={rest.some((entry) => entry.active)}
				aria-expanded={moreOpen}
				aria-controls="hotbar-more"
				onclick={() => toggleMenu('hotbar-more')}
			>
				<ItemIcon id="minecraft:chest" />
				<span class="name">{t('menu.more')}</span>
			</button>
			{#if moreOpen}
				<div class="more-list panel" id="hotbar-more">
					{#each rest as entry (entry.href)}
						<a
							class="mc-btn"
							class:on={entry.active}
							aria-current={entry.active ? 'page' : undefined}
							href={entry.href}
							{...tabAttributes(entry.newTab)}
							onclick={closeMenu}
						>
							<ItemIcon id={entry.icon} compact />
							{entry.label}
						</a>
					{/each}
				</div>
			{/if}
		</div>
	{/if}
</nav>

<style>
	.hotbar {
		display: flex;
		justify-content: center;
		width: 100%;
	}

	/* The game's hotbar: dark see-through slots in a grey frame; they share their borders. Light text with a shadow in
	   every theme, as the slots stay dark. Names wrap (with hyphens where the language has them) rather than widen. */
	.slot {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
		align-items: center;
		box-sizing: border-box;
		width: 9.4rem;
		min-height: 0;
		margin: 0 0 0 -3px;
		padding: 0.5rem 0.25rem 0.45rem;
		border: 3px solid #8b8b8b;
		border-radius: 0;
		box-shadow: inset 0 0 0 2px #373737;
		background: #000000a6;
		color: #e2e5ea;
		font: inherit;
		font-size: 0.9rem;
		line-height: 1.15;
		text-align: center;
		text-shadow: 2px 2px 0 #000000;
		overflow-wrap: normal;
		hyphens: auto;
		hyphenate-limit-chars: 8 4 4;
		cursor: pointer;
	}

	.slot:first-child {
		margin-left: 0;
	}

	.slot:hover,
	.slot:focus-visible {
		z-index: 1;
		border-color: #c6c6c6;
		outline: none;
		box-shadow: inset 0 0 0 2px #373737;
		background: #000000a6;
		color: #ffffa0;
	}

	/* The current page, framed like the game's selected slot. */
	.slot.on {
		z-index: 2;
		border-color: #ffffff;
		box-shadow:
			inset 0 0 0 2px #000000,
			0 0 0 2px #000000;
		color: #ffffa0;
	}

	.name {
		max-width: 100%;
	}

	.more-wrap {
		position: relative;
		margin-left: -3px;
	}

	.more-wrap .slot {
		height: 100%;
		margin: 0;
	}

	/* The rest of the pages, under the More slot, opening leftwards so it stays in the window. */
	.more-list {
		position: absolute;
		top: calc(100% + 0.35rem);
		right: 0;
		z-index: 30;
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
		min-width: 14rem;
		padding: 0.3rem;
		background: var(--color-tooltip);
	}

	.more-list .mc-btn {
		justify-content: flex-start;
	}

	.more-list .on {
		border-color: var(--button-border-hover);
		color: #ffffa0;
	}
</style>
