/** A player's skin and cape, ready for the skin library (object URLs). */
export type PlayerTextures = {
	name: string;
	uuid: string;
	slim: boolean;
	skin: string;
	cape: string | null;
};

type Profile = {
	name?: string;
	properties?: { name?: string; value?: string }[];
};

type TexturesPayload = {
	textures?: {
		SKIN?: { url?: string; metadata?: { model?: string } };
		CAPE?: { url?: string };
	};
};

const SESSION_SERVER = 'https://sessionserver.mojang.com';
const TEXTURE_SERVER = 'https://textures.minecraft.net';

/** Lower case with dashes (8-4-4-4-12); throws for anything that isn't 32 hex digits. */
export function normaliseUuid(uuid: string): string {
	const hex = uuid.replaceAll('-', '').toLowerCase();
	if (!/^[0-9a-f]{32}$/.test(hex)) {
		throw new Error(`Not a UUID: ${uuid}`);
	}
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Mojang's addresses through the server's forwarding, so browsers never contact Mojang directly. */
export function forwarded(url: string): string {
	if (url.startsWith(TEXTURE_SERVER)) {
		return `/mojang/textures${url.slice(TEXTURE_SERVER.length)}`;
	}
	if (url.startsWith(SESSION_SERVER)) {
		return `/mojang${url.slice(SESSION_SERVER.length)}`;
	}
	return url;
}

function decodeBase64Json(value: string): unknown {
	const bytes = Uint8Array.from(atob(value), (char) => char.charCodeAt(0));
	return JSON.parse(new TextDecoder().decode(bytes));
}

/** The textures property of a profile: name, skin address, cape address and model. */
export function parseProfile(profile: Profile): {
	name: string;
	skinUrl: string;
	capeUrl: string | null;
	slim: boolean;
} {
	const property = profile.properties?.find((entry) => entry.name === 'textures');
	if (!property?.value) {
		throw new Error('The profile has no textures');
	}
	const textures = (decodeBase64Json(property.value) as TexturesPayload).textures;
	const skinUrl = textures?.SKIN?.url;
	if (!skinUrl) {
		throw new Error('The profile has no skin');
	}
	return {
		name: profile.name ?? '',
		skinUrl,
		capeUrl: textures?.CAPE?.url || null,
		slim: textures?.SKIN?.metadata?.model === 'slim'
	};
}

/**
 * Whether the hat area of an old 64×32 skin (x 32–63, y 0–31 as RGBA) has no transparent pixel, in which case Minecraft
 * ignores the hat layer (SkinTextureDownloader.doNotchTransparencyHack).
 */
export function isOpaque(rgba: ArrayLike<number>): boolean {
	for (let i = 3; i < rgba.length; i += 4) {
		if (rgba[i] < 128) {
			return false;
		}
	}
	return true;
}

/**
 * The library doesn't apply Minecraft's rule for old 64×32 skins and draws an opaque box over the head, so the skin is
 * fixed here before it is used. Minecraft clears the whole area but makes its lower half opaque again right after, so
 * effectively only the hat layer (y 0–15) becomes transparent.
 */
async function fixOldSkin(blob: Blob): Promise<Blob> {
	const bitmap = await createImageBitmap(blob);
	try {
		if (bitmap.width !== 64 || bitmap.height !== 32) {
			return blob;
		}
		const canvas = document.createElement('canvas');
		canvas.width = 64;
		canvas.height = 32;
		const context = canvas.getContext('2d', { willReadFrequently: true });
		if (!context) {
			return blob;
		}
		context.drawImage(bitmap, 0, 0);
		if (!isOpaque(context.getImageData(32, 0, 32, 32).data)) {
			return blob;
		}
		context.clearRect(32, 0, 32, 16);
		return await new Promise<Blob>((resolve) => canvas.toBlob((png) => resolve(png ?? blob), 'image/png'));
	} finally {
		bitmap.close();
	}
}

// Shared promises for the page's lifetime; a failure is forgotten so a later request tries again.
const textureLoads = new Map<string, Promise<PlayerTextures>>();
const imageLoads = new Map<string, Promise<string>>();

function cached<T>(cache: Map<string, Promise<T>>, key: string, load: () => Promise<T>): Promise<T> {
	let promise = cache.get(key);
	if (!promise) {
		promise = load();
		cache.set(key, promise);
		promise.catch(() => {
			if (cache.get(key) === promise) {
				cache.delete(key);
			}
		});
	}
	return promise;
}

function loadImage(url: string, skin: boolean): Promise<string> {
	return cached(imageLoads, `${skin ? 'skin' : 'cape'}:${url}`, async () => {
		const response = await fetch(forwarded(url));
		if (!response.ok) {
			throw new Error(`${url}: ${response.status}`);
		}
		const blob = await response.blob();
		return URL.createObjectURL(skin ? await fixOldSkin(blob) : blob);
	});
}

/** A player's name, model, skin and cape by UUID (with or without dashes). */
export function loadTextures(uuid: string): Promise<PlayerTextures> {
	let id: string;
	try {
		id = normaliseUuid(uuid);
	} catch (error) {
		return Promise.reject(error);
	}
	return cached(textureLoads, id, async () => {
		const response = await fetch(forwarded(`${SESSION_SERVER}/session/minecraft/profile/${id}`));
		if (!response.ok) {
			throw new Error(`Profile ${id}: ${response.status}`);
		}
		const profile = parseProfile((await response.json()) as Profile);
		const [skin, cape] = await Promise.all([
			loadImage(profile.skinUrl, true),
			profile.capeUrl ? loadImage(profile.capeUrl, false) : Promise.resolve(null)
		]);
		return { name: profile.name, uuid: id, slim: profile.slim, skin, cape };
	});
}
