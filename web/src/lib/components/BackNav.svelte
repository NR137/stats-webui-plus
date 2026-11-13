<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import { afterNavigate } from '$app/navigation';
	import { page } from '$app/state';

	// The page this one was reached from inside the site (path and query), or null when it was opened directly.
	let previous = $state<string | null>(null);

	afterNavigate((navigation) => {
		// A direct page load comes with a "from" that has no URL; reading one there broke client-side navigation.
		const from = navigation.type === 'enter' ? null : navigation.from;
		previous = from?.url ? `${from.url.pathname}${from.url.search}` : null;
	});

	/** The page above this one, for visitors who came from elsewhere (a shared link, a bookmark). */
	function parentPath(path: string): string {
		const playerAdvancement = path.match(/^\/players\/([^/]+)\/advancements\/./);
		if (playerAdvancement) {
			return `/players/${playerAdvancement[1]}/advancements`;
		}
		for (const section of ['/leaderboards', '/advancements', '/players']) {
			if (path.startsWith(`${section}/`)) {
				return section;
			}
		}
		return '/';
	}

	const href = $derived(previous ?? parentPath(page.url.pathname));

	function onclick(event: MouseEvent) {
		if (!previous || event.defaultPrevented || event.button !== 0) {
			return;
		}
		if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
			return;
		}
		// Back in the history rather than to the address: the previous page comes back as it was left (scroll position,
		// snapshot), and the history doesn't grow by an entry.
		event.preventDefault();
		history.back();
	}
</script>

<a class="mc-btn back" {href} aria-label={t('menu.back')} {onclick}>
	<Icon name="back" />
	<span>{t('menu.back')}</span>
</a>

<style>
	.back {
		display: inline-flex;
		width: auto;
		min-height: 2.15rem;
		margin-bottom: 1.1rem;
		padding: 0.28rem 0.9rem;
		vertical-align: top;
	}
</style>
