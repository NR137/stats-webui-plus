/** The nested texts of a language as one flat list, 'area.key' -> text (made once per language). */
export function flatten(tree: object, prefix = '', into: Record<string, string> = {}): Record<string, string> {
	for (const [key, value] of Object.entries(tree)) {
		if (typeof value === 'string') {
			into[prefix + key] = value;
		} else {
			flatten(value, `${prefix}${key}.`, into);
		}
	}
	return into;
}
