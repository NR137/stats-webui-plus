// The game's files the server draws (/mc/...). The page names what they are made from (<meta name="mc-files">, set
// by the mod: "26.3-18"); addresses with that stamp change with every new version or format, so the browser may keep
// them for good: the icons, pictures, the game's texts and the icon list alike, as one drawing makes them all. Without
// a stamp (downloads off or failed, so only the server's own English texts; or the web dev server), the plain
// addresses, which the browser checks again each time.
const stamp =
	typeof document === 'undefined'
		? null
		: (document.querySelector('meta[name="mc-files"]')?.getAttribute('content') ?? null);

/** /mc/item/apple.png -> /mc/26.3-18/item/apple.png; other addresses stay as they are. */
export function mcFile(path: string): string {
	return stamp && path.startsWith('/mc/') ? `/mc/${stamp}/${path.slice('/mc/'.length)}` : path;
}
