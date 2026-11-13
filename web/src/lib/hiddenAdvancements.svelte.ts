// Whether the advancements the game hides are left out: a guard against spoilers for the visitor, so all of them, no
// matter who has unlocked them. The visitor's choice, remembered in this browser and shared by every page that lists
// advancements; on by default.
const STORAGE_KEY = 'stats-webui-plus-hide-hidden';

function stored(): boolean {
	try {
		return typeof localStorage === 'undefined' || localStorage.getItem(STORAGE_KEY) !== 'show';
	} catch {
		return true;
	}
}

export const hiddenAdvancements = $state<{ hide: boolean }>({ hide: stored() });

export function setHideHidden(hide: boolean): void {
	hiddenAdvancements.hide = hide;
	try {
		localStorage.setItem(STORAGE_KEY, hide ? 'hide' : 'show');
	} catch {
		// Private mode or storage blocked: the choice lasts until the page is closed.
	}
}

/** Whether to leave this advancement out: the game hides it, and the visitor doesn't want to see those. */
export function hiddenFromView(item: { hidden?: boolean }): boolean {
	return hiddenAdvancements.hide && Boolean(item.hidden);
}
