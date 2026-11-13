<script lang="ts">
	import HotbarMenu from '#lib/components/HotbarMenu.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import LanguageSwitcher from '#lib/components/LanguageSwitcher.svelte';
	import ThemeSwitcher from '#lib/components/ThemeSwitcher.svelte';
	import { closeMenu, headerMenu, registerMenuRoot, toggleMenu } from '#lib/components/headerMenu.svelte.ts';
	import { branding } from '#lib/branding.svelte.ts';
	import { languageOrder, localeSettings, t } from '#lib/i18n/i18n.svelte.ts';
	import { subMenu, tabAttributes, type MenuEntry } from '#lib/menu.ts';

	// The header's menu: on desktops the pages as a hotbar, the owner's other links and pages in a row below it with
	// language and theme; on phones one button with the current page that opens all of them as a list. `visible` says
	// whether it is on screen (the sticky bar takes over when it isn't).
	type Entry = MenuEntry & { active: boolean };
	let {
		links,
		subEntries,
		current,
		currentPage,
		visible = $bindable(true)
	}: {
		links: Entry[];
		subEntries: Entry[];
		current: MenuEntry | null;
		currentPage: string;
		visible?: boolean;
	} = $props();

	// On phones the pages are behind one button that names the current page; it closes after choosing one.
	const menuOpen = $derived(headerMenu.open === 'nav');

	const measureContext = typeof document === 'undefined' ? null : document.createElement('canvas').getContext('2d');

	// Custom link buttons are all as wide as the longest label in any enabled language (between 8 and 16rem), so a
	// language switch never changes the row; only labels longer than that wrap. The labels come from the server's
	// config, so this is measured in the browser, in the buttons' font (0.85rem, see .links below).
	const linkLabels = $derived(
		localeSettings.enabled.flatMap((locale) => subMenu(branding, languageOrder(locale), '').map((entry) => entry.label))
	);
	let linkWidth = $state(0);

	$effect(() => {
		const labels = linkLabels;
		if (!measureContext || labels.length === 0) {
			return;
		}
		const measure = () => {
			const rem = parseFloat(getComputedStyle(document.documentElement).fontSize);
			measureContext.font = `${0.85 * rem}px ${getComputedStyle(document.body).fontFamily}`;
			const text = Math.max(...labels.map((label) => measureContext.measureText(label).width));
			// 0.8rem padding and a 2px border on each side, and a pixel to spare against rounding.
			// With the icon in front (1.5rem and a 0.45rem gap).
			const width = Math.ceil(text + 1.6 * rem + 1.95 * rem + 4 + 1);
			linkWidth = Math.min(16 * rem, Math.max(8 * rem, width));
		};
		measure();
		document.fonts?.ready.then(measure);
	});

	let bar = $state<HTMLElement>();
	$effect(() => {
		const node = bar;
		if (!node) {
			return;
		}
		const observer = new IntersectionObserver(([entry]) => {
			visible = entry.isIntersecting;
		});
		observer.observe(node);
		return () => observer.disconnect();
	});
</script>

<div
	class="bar"
	class:open={menuOpen}
	style:--link-width={linkWidth ? `${linkWidth}px` : undefined}
	use:registerMenuRoot
	bind:this={bar}
>
	<button
		type="button"
		class="mc-btn toggle"
		aria-expanded={menuOpen}
		aria-controls="site-menu"
		aria-label="{t('menu.open')}: {currentPage}"
		onclick={() => toggleMenu('nav')}
	>
		<Icon name="menu" />
		{#if current}
			<ItemIcon id={current.icon} compact />
		{/if}
		<span>{currentPage}</span>
	</button>
	<div class="pages" id="site-menu">
		<!-- Desktop: the hotbar. Phones: the same pages as a list behind the menu button. -->
		<div class="hotbar-wrap">
			<HotbarMenu entries={links} label={t('menu.open')} />
		</div>
		<nav class="menu" aria-label={t('menu.open')}>
			{#each links as link (link.href)}
				<a
					class="mc-btn"
					class:on={link.active}
					aria-current={link.active ? 'page' : undefined}
					href={link.href}
					{...tabAttributes(link.newTab)}><ItemIcon id={link.icon} compact />{link.label}</a
				>
			{/each}
		</nav>
		{#if subEntries.length > 0}
			<nav class="links" aria-label={t('menu.links')}>
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
			</nav>
		{/if}
	</div>
	<div class="settings">
		<LanguageSwitcher compact />
		<ThemeSwitcher compact />
	</div>
</div>

<style>
	/* The pages as a hotbar across the header's whole width (one row; what doesn't fit goes into its "More" slot), the
	   custom links (Map, Discord, ...), language and theme in a row of their own below it. */
	.bar {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		justify-content: center;
		width: 100%;
	}

	.toggle {
		display: none;
	}

	.pages {
		display: contents;
	}

	/* min-width: 0, so the hotbar measures the room there is, not the slots it shows (it never got narrower). */
	.hotbar-wrap {
		display: flex;
		flex: 0 0 100%;
		justify-content: center;
		min-width: 0;
		margin-bottom: 0.15rem;
	}

	/* The list of pages is the phones' menu (see below); the desktop has the hotbar. */
	.menu {
		display: none;
	}

	.links {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		justify-content: center;
	}

	/* The second row (custom links, language and theme as icon buttons) is smaller than the pages above it, and like
	   them its buttons have a fixed width, so a language switch moves nothing. */
	.links .mc-btn,
	.settings :global(.trigger) {
		min-height: 2rem;
		padding: 0.15rem 0.8rem;
		font-size: 0.85rem;
	}

	.links .mc-btn {
		flex: 0 0 var(--link-width, 8rem);
	}

	/* When a long link label wraps onto two lines, every button in the row grows with it. */
	.settings :global(.wrap) {
		display: flex;
	}

	/* Language and theme: after the custom links; on phones next to the menu button. */
	.settings {
		display: flex;
		gap: 0.5rem;
	}

	/* Phones: one button with the current page opens the pages below it; language and theme sit next to it. */
	@media (max-width: 640px) {
		.bar {
			display: grid;
			grid-template-columns: 1fr auto;
			grid-template-areas:
				'toggle settings'
				'pages pages';
			width: min(24rem, 100%);
		}

		.toggle {
			display: inline-flex;
			grid-area: toggle;
			min-width: 0;
		}

		/* The current page on one line (French "Classement général" wrapped and made the header taller); on very
		   narrow screens it ends in "…" instead. */
		.toggle span {
			min-width: 0;
			overflow: hidden;
			text-overflow: ellipsis;
			white-space: nowrap;
		}

		.settings {
			grid-area: settings;
		}

		.pages {
			display: none;
			flex-direction: column;
			grid-area: pages;
			gap: 0.5rem;
		}

		.bar.open .pages {
			display: flex;
		}

		.hotbar-wrap {
			display: none;
		}

		/* The pages as a list, each with its item icon. */
		.menu {
			display: flex;
			flex-direction: column;
			gap: 0.5rem;
		}

		.menu .mc-btn {
			justify-content: flex-start;
		}

		.links .mc-btn {
			flex: 1 1 0;
			min-width: 0;
			min-height: 2.5rem;
			font-size: 1rem;
		}

		.settings :global(.trigger) {
			min-height: 2.5rem;
			padding: 0;
		}

		/* Plain icon squares here, to leave the menu button as much room as possible. */
		.bar .settings :global(.trigger) {
			width: 2.5rem;
		}

		.settings :global(.chevron) {
			display: none;
		}
	}
</style>
