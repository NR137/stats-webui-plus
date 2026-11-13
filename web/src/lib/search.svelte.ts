/**
 * Follows `read()` once it has stayed the same for `delayMs`, so typing in a search field doesn't filter on every key.
 * Starts with the current value.
 */
export function deferredValue<T>(read: () => T, delayMs = 140): { readonly current: T } {
	let current = $state(read());
	$effect(() => {
		const next = read();
		const timer = setTimeout(() => {
			current = next;
		}, delayMs);
		return () => clearTimeout(timer);
	});
	return {
		get current() {
			return current;
		}
	};
}

export const SEARCH_RESULT_CAP = 48;

/** While searching, at most `cap` results; otherwise all. */
export function cappedResults<T>(items: T[], searching: boolean, cap = SEARCH_RESULT_CAP): T[] {
	return searching ? items.slice(0, cap) : items;
}

const FIRST = 120;
const STEP = 400;
const MAX = 5000;

/**
 * How many tiles of a long list to show. Showing all 1,600 statistics of a group at once took a noticeable moment;
 * this way the first rows appear at once and the rest follows within a few frames.
 */
export class GrowingLimit {
	value = $state(FIRST);

	constructor(key: () => unknown) {
		// Before the page updates, so a new list (another tab, the zeros switch, a search) never draws in full first.
		$effect.pre(() => {
			key();
			this.value = FIRST;
			let frame = 0;
			const grow = () => {
				this.value = Math.min(this.value + STEP, MAX);
				if (this.value < MAX) {
					frame = requestAnimationFrame(grow);
				}
			};
			frame = requestAnimationFrame(grow);
			return () => cancelAnimationFrame(frame);
		});
	}
}
