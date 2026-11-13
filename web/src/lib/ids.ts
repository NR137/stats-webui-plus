// Game IDs in page addresses. The game's own things go without their namespace ("minecraft:story/root" ->
// "story/root"); other namespaces keep it, with a plain colon as in the game's commands ("somemod:quests/first").
// Each part is encoded on its own, so slashes stay folders and a colon stays readable.

const GAME = 'minecraft:';

/** "minecraft:story/root" -> "story/root"; "somemod:quests/first" -> "somemod:quests/first". */
export function idToPath(id: string): string {
	const short = id.startsWith(GAME) ? id.slice(GAME.length) : id;
	return short
		.split('/')
		.map((part) => encodeURIComponent(part).replaceAll('%3A', ':'))
		.join('/');
}

/** The reverse, from an address's (already decoded) parts: "story/root" -> "minecraft:story/root". */
export function pathToId(path: string): string {
	const first = path.split('/', 1)[0];
	return first.includes(':') ? path : GAME + path;
}
