import type { Action } from 'svelte/action';

// One open menu at a time across the header (pages, language, theme, the sticky bar, player pickers): opening one
// closes the others.
export const headerMenu = $state<{ open: string | null }>({ open: null });

const roots = new Set<HTMLElement>();

export function toggleMenu(id: string): void {
	headerMenu.open = headerMenu.open === id ? null : id;
}

export function closeMenu(): void {
	headerMenu.open = null;
}

/** Marks an element as part of a menu: pressing inside it doesn't close the open menu. */
export const registerMenuRoot: Action<HTMLElement> = (node) => {
	roots.add(node);
	return {
		destroy() {
			roots.delete(node);
		}
	};
};

if (typeof document !== 'undefined') {
	// Capture phase: a menu's own handlers may stop the event, and a press outside must close it all the same.
	document.addEventListener(
		'pointerdown',
		(event) => {
			if (headerMenu.open === null) {
				return;
			}
			const target = event.target;
			if (target instanceof Node && [...roots].some((root) => root.contains(target))) {
				return;
			}
			closeMenu();
		},
		true
	);
	document.addEventListener('keydown', (event) => {
		if (event.key === 'Escape' && headerMenu.open !== null) {
			closeMenu();
		}
	});
}
