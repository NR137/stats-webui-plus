import type { AfterNavigate, BeforeNavigate } from '$app/navigation';

// When the visitor last clicked, tapped or typed: a scroll still waiting for its page gives up then, so a page
// that only grows because of what they did (picking a player) doesn't jump.
let interactedAt = 0;
if (typeof window !== 'undefined') {
	for (const type of ['pointerdown', 'keydown']) {
		window.addEventListener(type, () => (interactedAt = performance.now()), { capture: true, passive: true });
	}
}

// Where each page was scrolled to when the visitor left it, to go back to that place.
const leftAt = new Map<string, number>();

export function rememberScrollBefore({ from }: BeforeNavigate) {
	if (from) {
		leftAt.set(from.url.href, window.scrollY);
	}
}

/**
 * Every page but the start page opens at its content: the banner and the header's menu scroll away (quickly, or at
 * once with reduced motion), and the slim menu bar takes the menu's place. Not when going back (the browser restores
 * the place), nor when the same page only changes its URL (a player's tabs, a search, the players compared).
 */
export function scrollToContentAfter({ from, to, type }: AfterNavigate) {
	if (!to) {
		return;
	}
	if (type === 'popstate') {
		restore(to.url.href);
		return;
	}
	if (to.url.pathname === '/') {
		return;
	}
	if (!samePage(from, to)) {
		scrollToContent(to.url.pathname);
	}
}

type Place = { route: { id: string | null }; params: Record<string, string | undefined> | null };

/** The same page with another URL: the same route and parameters, apart from a player's tab. */
export function samePage(from: Place | null, to: Place): boolean {
	return (
		from?.route.id === to.route.id &&
		JSON.stringify({ ...from?.params, tab: '' }) === JSON.stringify({ ...to.params, tab: '' })
	);
}

/**
 * Scrolls just past the header's menu, and never so far that the slim bar covers the content. Waits (up to a few
 * seconds) for the header and the content to load first; does nothing on a page too short to scroll, and stops if the visitor
 * scrolls or leaves meanwhile.
 */
export function scrollToContent(path: string = window.location.pathname) {
	const started = performance.now();
	const step = () => {
		if (window.location.pathname !== path || window.scrollY > 0 || interactedAt > started) {
			return;
		}
		const menu = document.querySelector('header .bar');
		const content = document.querySelector('main');
		if (!menu || !content) {
			return;
		}
		// The header's banner and fonts set where the menu ends: measure only once they are there. Only pictures that
		// are shown: hidden ones (the phones' menu list on a desktop) load lazily, that is never.
		const headerReady =
			document.fonts.status === 'loaded' &&
			[...document.querySelectorAll<HTMLImageElement>('header img')]
				.filter((image) => image.getClientRects().length > 0)
				.every((image) => image.complete);
		if (!headerReady && performance.now() - started < 4000) {
			setTimeout(step, 50);
			return;
		}
		const barHeight = document.querySelector('.sticky')?.getBoundingClientRect().height ?? 0;
		const menuBottom = menu.getBoundingClientRect().bottom + window.scrollY;
		const top = Math.max(menuBottom + 1, content.getBoundingClientRect().top + window.scrollY - barHeight - 4);
		if (document.documentElement.scrollHeight - window.innerHeight < top) {
			// Still loading, or a page too short to scroll that far.
			if (performance.now() - started < 4000) {
				setTimeout(step, 100);
			}
			return;
		}
		const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		window.scrollTo({ top, behavior: smooth ? 'smooth' : 'instant' });
	};
	step();
}

/**
 * Back (or forward) to a page: the browser puts it where it was right away, but a page that loads its data is still
 * short then, so the place gets cut off. Once the page is long enough again, go to where the visitor left it.
 */
function restore(href: string) {
	const target = leftAt.get(href);
	if (target === undefined) {
		return;
	}
	const started = performance.now();
	let expected = window.scrollY;
	const step = () => {
		if (window.location.href !== href || Math.abs(window.scrollY - expected) > 2 || Math.abs(window.scrollY - target) <= 2) {
			return;
		}
		if (document.documentElement.scrollHeight - window.innerHeight < target) {
			if (performance.now() - started < 4000) {
				setTimeout(step, 50);
			}
			return;
		}
		window.scrollTo({ top: target, behavior: 'instant' });
		expected = target;
	};
	setTimeout(step, 0);
}
