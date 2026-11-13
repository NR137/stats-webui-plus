import { branding } from '#lib/branding.svelte.ts';

/**
 * A player's page: by name, or by UUID where several players share the name (case aside). Floodgate cuts long Bedrock
 * gamertags to 16 characters with its prefix and turns spaces into "_", so two Bedrock players can end up with the same
 * name; the server tells them apart by UUID only.
 */
export function playerPath(name: string, uuid?: string): string {
	const key = uuid && branding.nameClashes.includes(name.toLowerCase()) ? uuid : name;
	return `/players/${encodeURIComponent(key)}`;
}

/** What identifies a player in addresses and pickers: the name, or the UUID where the name is shared. */
export function playerKey(name: string, uuid: string): string {
	return branding.nameClashes.includes(name.toLowerCase()) ? uuid : name;
}
