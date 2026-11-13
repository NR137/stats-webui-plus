<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import { closeMenu, headerMenu, registerMenuRoot, toggleMenu } from '#lib/components/headerMenu.svelte.ts';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import { tabAttributes } from '#lib/menu.ts';

	type Entry = { href: string; label: string; icon: string; newTab?: boolean; active: boolean };

	let {
		links,
		currentPage,
		currentIcon = null,
		subEntries = [],
		headerMenuVisible
	}: {
		links: Entry[];
		currentPage: string;
		currentIcon?: string | null;
		// The row below the header's hotbar (the owner's links and pages), small buttons in the list on phones.
		subEntries?: Entry[];
		headerMenuVisible: boolean;
	} = $props();

	const open = $derived(headerMenu.open === 'sticky');
	// The page a slot leads to while it is hovered or focused, named in place of the current one.
	let previewed = $state<string | null>(null);
	// Kept while its list is open, even when the header's menu scrolls back into view under it.
	const shown = $derived(!headerMenuVisible || open);

	function reducedMotion() {
		return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
	}

	function toTop() {
		closeMenu();
		window.scrollTo({ top: 0, behavior: reducedMotion() ? 'auto' : 'smooth' });
	}

	// The list hangs under a fixed bar; scrolling on would leave it over content that has moved on. A little scrolling
	// (a finger resting on the screen, the browser's bars moving) keeps it open.
	const SCROLL_TOLERANCE = 64;
	let openedAt = 0;

	$effect(() => {
		if (open) {
			openedAt = window.scrollY;
		}
	});

	function onscroll() {
		if (headerMenu.open === 'sticky' && Math.abs(window.scrollY - openedAt) > SCROLL_TOLERANCE) {
			closeMenu();
		}
	}
</script>

<svelte:window {onscroll} />

<div class="sticky" class:shown aria-hidden={!shown} inert={!shown} use:registerMenuRoot>
	<div class="inner">
		<!-- The hotbar in small: the pages' icons (their names on hover), the current page named beside them. -->
		<nav class="row" aria-label={t('menu.open')}>
			<span class="slots">
				{#each links as link (link.href)}
					<a
						class="mini"
						class:on={link.active}
						aria-current={link.active ? 'page' : undefined}
						aria-label={link.label}
						title={link.label}
						href={link.href}
						{...tabAttributes(link.newTab)}
						onpointerenter={() => (previewed = link.label)}
						onpointerleave={() => (previewed = null)}
						onfocus={() => (previewed = link.label)}
						onblur={() => (previewed = null)}><ItemIcon id={link.icon} compact /></a
					>
				{/each}
			</span>
			<!-- Every name the label can show, on top of each other: the widest sets its width, so the slots never move. -->
			<span class="current" aria-hidden={previewed !== null}>
				{#each [currentPage, ...links.map((link) => link.label), ...subEntries.map((entry) => entry.label)] as name, index (index)}
					<span
						class="name"
						class:shown={previewed === null ? index === 0 : index > 0 && name === previewed}
						class:preview={index > 0}>{name}</span
					>
				{/each}
			</span>
		</nav>
		<button
			type="button"
			class="mc-btn toggle"
			aria-expanded={open}
			aria-controls="sticky-menu"
			aria-label="{t('menu.open')}: {currentPage}"
			onclick={() => toggleMenu('sticky')}
		>
			<Icon name="menu" />
			{#if currentIcon}
				<ItemIcon id={currentIcon} compact />
			{/if}
			<span class="page">{currentPage}</span>
		</button>
		<!-- The owner's links and pages from below the header's hotbar: small grey buttons with their icons, at the right end
		     with the ↑ button, apart from the pages. -->
		{#if subEntries.length > 0}
			<span class="sub-icons">
				{#each subEntries as entry (entry.href)}
					<a
						class="mc-btn icon-btn"
						class:on={entry.active}
						aria-current={entry.active ? 'page' : undefined}
						aria-label={entry.label}
						title={entry.label}
						href={entry.href}
						{...tabAttributes(entry.newTab)}
						onpointerenter={() => (previewed = entry.label)}
						onpointerleave={() => (previewed = null)}
						onfocus={() => (previewed = entry.label)}
						onblur={() => (previewed = null)}><ItemIcon id={entry.icon} compact /></a
					>
				{/each}
			</span>
		{/if}
		<button type="button" class="mc-btn top" aria-label={t('menu.top')} title={t('menu.top')} onclick={toTop}
			>↑</button
		>
	</div>
	<nav id="sticky-menu" class="list" class:open aria-label={t('menu.open')}>
		{#each links as link (link.href)}
			<a
				class="mc-btn"
				class:on={link.active}
				aria-current={link.active ? 'page' : undefined}
				href={link.href}
				{...tabAttributes(link.newTab)}
				onclick={closeMenu}><ItemIcon id={link.icon} compact />{link.label}</a
			>
		{/each}
		{#if subEntries.length > 0}
			<span class="sub">
				{#each subEntries as entry (entry.href)}
					<a
						class="mc-btn"
						class:on={entry.active}
						aria-current={entry.active ? 'page' : undefined}
						href={entry.href}
						{...tabAttributes(entry.newTab)}
						onclick={closeMenu}><ItemIcon id={entry.icon} compact />{entry.label}</a
					>
				{/each}
			</span>
		{/if}
	</nav>
</div>

<style>
	/* The page's own background (the theme's --page-background), so the bar looks cut out of it. */
	.sticky {
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		z-index: 90;
		border-bottom: 2px solid var(--color-border);
		box-shadow: 0 2px 0 #00000040;
		background: var(--page-background);
		/* Its shadow too must leave the window. */
		transform: translateY(calc(-100% - 4px));
		image-rendering: pixelated;
		transition: transform 0.18s ease-out;
	}

	.sticky.shown {
		transform: none;
	}

	@media (prefers-reduced-motion: reduce) {
		.sticky {
			transition: none;
		}
	}

	.inner {
		display: flex;
		gap: 0.6rem;
		align-items: center;
		justify-content: center;
		max-width: 68rem;
		margin: 0 auto;
		padding: 0.35rem 1rem;
	}

	/* The small hotbar and the current page's name, centred in all the room before the ↑ button at the right end. */
	.row {
		display: flex;
		flex: 1 1 auto;
		gap: 0.7rem;
		align-items: center;
		justify-content: center;
		min-width: 0;
	}

	.slots {
		display: flex;
	}

	/* Like the header's hotbar slots, small and with the icon only. */
	.mini {
		display: grid;
		place-items: center;
		box-sizing: border-box;
		width: 2.6rem;
		height: 2.6rem;
		margin-left: -2px;
		border: 2px solid #8b8b8b;
		box-shadow: inset 0 0 0 2px #373737;
		background: #000000a6;
	}

	.mini:first-child {
		margin-left: 0;
	}

	.mini:hover,
	.mini:focus-visible {
		z-index: 1;
		border-color: #c6c6c6;
		outline: none;
	}

	.mini.on {
		z-index: 2;
		border-color: #ffffff;
	}

	/* The names stacked in one grid cell, only one visible: the current page in gold, a hovered slot's page in the
	   text colour, so it reads as "where this goes". */
	.current {
		display: grid;
		white-space: nowrap;
	}

	.name {
		grid-area: 1 / 1;
		color: var(--color-gold);
		visibility: hidden;
	}

	.name.preview {
		color: var(--color-text);
	}

	.name.shown {
		visibility: visible;
	}

	/* At the right end, before the ↑ button. */
	.sub-icons {
		display: flex;
		flex: 0 0 auto;
		gap: 0.3rem;
	}

	.sticky .icon-btn {
		width: 2.2rem;
		min-height: 2.2rem;
		padding: 0;
	}

	/* The owner's links and pages below the pages, smaller and side by side, as under the header's menu. */
	.sub {
		display: flex;
		flex-wrap: wrap;
		gap: 0.35rem;
	}

	.sub .mc-btn {
		flex: 1 1 0;
		min-width: 0;
		min-height: 2rem;
		font-size: 0.85rem;
	}

	.sticky .mc-btn {
		min-height: 1.9rem;
		padding: 0.15rem 0.55rem;
		font-size: 0.85rem;
		white-space: nowrap;
	}

	.sticky .top {
		flex: 0 0 auto;
		min-width: 1.9rem;
		padding: 0.15rem 0.4rem;
	}

	.sticky .on {
		border-color: var(--button-border-hover);
		background: var(--button-face-hover);
		color: #ffffa0;
	}

	.toggle {
		display: none;
		min-width: 0;
	}

	.list .mc-btn {
		justify-content: flex-start;
	}

	.list {
		display: none;
		flex-direction: column;
		gap: 0.35rem;
		max-width: 68rem;
		margin: 0 auto;
		padding: 0 1rem 0.6rem;
	}

	@media (max-width: 640px) {
		.row,
		.sub-icons {
			display: none;
		}

		.sticky .toggle {
			display: inline-flex;
			flex: 1 1 auto;
		}

		.list.open {
			display: flex;
		}
	}
</style>
