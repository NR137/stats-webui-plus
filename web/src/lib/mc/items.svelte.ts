import { mcFile } from './files';

// Which picture each ID uses. The server writes the list (/mc/icons.json) when it draws the game's textures, so it
// knows the IDs whose picture isn't simply named after them (blocks drawn from their top, mobs as faces, ...).
// Raw: the list has thousands of entries and is only ever replaced as a whole, so deep reactivity would only cost.
let icons = $state.raw<Record<string, string>>({});
let loading: Promise<void> | null = null;

/** Fetches the icon list once; after a failure the next call tries again (the layout calls it on a language switch). */
export function loadIcons(): Promise<void> {
	loading ??= fetch(mcFile('/mc/icons.json'))
		.then((response) => {
			if (!response.ok) {
				throw new Error(`icons.json: ${response.status}`);
			}
			return response.json() as Promise<Record<string, string>>;
		})
		.then((map) => {
			icons = map;
		})
		.catch(() => {
			loading = null;
		});
	return loading;
}

/** Sets the icon list directly (tests, which have no server). */
export function useIcons(map: Record<string, string>): void {
	icons = map;
}

// Mob faces are kept apart from the items of the same name (minecraft:pig is no item, but a spawn egg might be).
export const ENTITY_ICON_PREFIX = 'entity:';

/** entity:minecraft:zombie and minecraft:zombie -> zombie. */
export function shortItemId(id: string): string {
	const plain = id.startsWith(ENTITY_ICON_PREFIX) ? id.slice(ENTITY_ICON_PREFIX.length) : id;
	return plain.startsWith('minecraft:') ? plain.slice('minecraft:'.length) : plain;
}

/** The letter shown when there is no picture at all. */
export function itemLetter(id: string): string {
	return shortItemId(id).charAt(0).toUpperCase();
}

export function hasIcon(key: string): boolean {
	return Object.hasOwn(icons, key);
}

/**
 * Other names the same picture may be listed under: wall signs are drawn like the standing ones, wall_torch like
 * torch, and statistics name some things in the plural.
 */
function aliases(name: string): string[] {
	return [
		name.replace(/_wall_sign$/, '_sign'),
		name.replace(/_wall_hanging_sign$/, '_hanging_sign'),
		name.replace(/^wall_/, ''),
		name.replace(/s$/, '')
	].filter((alias) => alias !== name);
}

/** The addresses to try for an ID, best first (without mcFile: ItemIcon adds that, and the size variants). */
export function itemTextureUrls(id: string): string[] {
	const urls: (string | undefined)[] = [];
	if (id.startsWith(ENTITY_ICON_PREFIX)) {
		urls.push(icons[id], ...itemTextureUrls(id.slice(ENTITY_ICON_PREFIX.length)));
	} else {
		const name = shortItemId(id);
		urls.push(icons[id], ...aliases(name).map((alias) => icons[`minecraft:${alias}`]));
		// Without an entry, the usual places of the game's textures; the first that loads wins.
		urls.push(
			`/mc/item/${name}.png`,
			`/mc/inv/${name}.png`,
			`/mc/iso/${name}.png`,
			`/mc/block/${name}.png`,
			`/mc/item/${name}_spawn_egg.png`,
			`/mc/block/${name}_front.png`,
			`/mc/block/${name}_top.png`,
			`/mc/block/${name}_side.png`,
			`/mc/block/${name}_0.png`,
			`/mc/block/${name}_still.png`
		);
	}
	return [...new Set(urls.filter((url): url is string => !!url))];
}

