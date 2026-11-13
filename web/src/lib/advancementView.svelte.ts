// How advancements are listed: as tiles, or as the game's tree. The visitor's choice, remembered in this browser and
// shared by every page that lists advancements.
export type AdvancementView = 'tiles' | 'tree';

const STORAGE_KEY = 'stats-webui-plus-advancement-view';

function stored(): AdvancementView {
	try {
		return typeof localStorage !== 'undefined' && localStorage.getItem(STORAGE_KEY) === 'tree' ? 'tree' : 'tiles';
	} catch {
		return 'tiles';
	}
}

export const advancementView = $state<{ mode: AdvancementView }>({ mode: stored() });

export function setAdvancementView(mode: AdvancementView): void {
	advancementView.mode = mode;
	try {
		localStorage.setItem(STORAGE_KEY, mode);
	} catch {
		// Private mode or storage blocked: the choice lasts until the page is closed.
	}
}
