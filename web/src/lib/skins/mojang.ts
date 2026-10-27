export type PlayerTextures = {
	name: string;
	uuid: string;
	slim: boolean;
	skinUrl: string;
	capeUrl: string | null;
};

type MojangProfile = {
	id: string;
	name: string;
	properties?: { name: string; value: string }[];
};

type TexturePayload = {
	textures?: {
		SKIN?: {
			url: string;
			metadata?: { model?: string };
		};
		CAPE?: {
			url: string;
		};
	};
};

const SESSION_ORIGIN = 'https://sessionserver.mojang.com';
const profileCache = new Map<string, Promise<PlayerTextures>>();
const imageCache = new Map<string, Promise<string>>();

export function formatUuid(uuid: string): string {
	const hex = uuid.replaceAll('-', '').toLowerCase();
	if (!/^[0-9a-f]{32}$/.test(hex)) {
		throw new Error('Invalid UUID');
	}
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function mojangProfileUrl(uuid: string): string {
	return `${SESSION_ORIGIN}/session/minecraft/profile/${formatUuid(uuid)}`;
}

export function viaMojangProxy(url: string): string {
	// The server's own files, like the default skin it names for players without a skin of their own.
	if (url.startsWith('/')) {
		return url;
	}
	if (url.startsWith(SESSION_ORIGIN)) {
		return `/mojang${url.slice(SESSION_ORIGIN.length)}`;
	}
	const parsed = new URL(url);
	if (parsed.hostname === 'textures.minecraft.net') {
		return `/mojang/textures${parsed.pathname}`;
	}
	return url;
}

export function getPlayerTextures(uuid: string): Promise<PlayerTextures> {
	const id = formatUuid(uuid);
	const cached = profileCache.get(id);
	if (cached) {
		return cached;
	}
	const pending = loadTextures(id);
	profileCache.set(id, pending);
	pending.catch(() => {
		profileCache.delete(id);
	});
	return pending;
}

/**
 * Minecraft ignores the hat layer of an old 64x32 skin when that part of the texture has no transparent pixel
 * (SkinTextureDownloader.doNotchTransparencyHack, made for Notch's skin). The skin renderer doesn't, which draws
 * an opaque box over the head, so apply the same rule here. Other skins are returned unchanged.
 */
async function applyLegacyHatRule(blob: Blob): Promise<Blob> {
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
		// Minecraft checks x 32-63, y 0-31 and, if nothing there is transparent, clears it; the lower half is made
		// opaque again right after, so effectively only the hat layer (y 0-15) becomes transparent.
		const area = context.getImageData(32, 0, 32, 32).data;
		for (let i = 3; i < area.length; i += 4) {
			if (area[i] < 128) {
				return blob;
			}
		}
		context.clearRect(32, 0, 32, 16);
		return await new Promise<Blob>((resolve) => canvas.toBlob((fixed) => resolve(fixed ?? blob), 'image/png'));
	} finally {
		bitmap.close();
	}
}

async function cachedImageUrl(url: string, transform?: (blob: Blob) => Promise<Blob>): Promise<string> {
	const cached = imageCache.get(url);
	if (cached) {
		return cached;
	}
	const pending = (async () => {
		const response = await fetch(url);
		if (!response.ok) {
			throw new Error(`${response.status} ${response.statusText}`);
		}
		const blob = await response.blob();
		return URL.createObjectURL(transform ? await transform(blob) : blob);
	})();
	imageCache.set(url, pending);
	pending.catch(() => {
		imageCache.delete(url);
	});
	return pending;
}

async function loadTextures(uuid: string): Promise<PlayerTextures> {
	// For players without a skin of their own, the server answers with a profile naming their default skin.
	const response = await fetch(viaMojangProxy(mojangProfileUrl(uuid)));
	if (!response.ok) {
		throw new Error(`${response.status} ${response.statusText}`);
	}
	const profile = (await response.json()) as MojangProfile;
	const encoded = profile.properties?.find((property) => property.name === 'textures')?.value;
	if (!encoded) {
		throw new Error('Missing textures');
	}
	const payload = JSON.parse(atob(encoded)) as TexturePayload;
	const skin = payload.textures?.SKIN;
	if (!skin?.url) {
		throw new Error('Missing skin');
	}
	const skinProxy = viaMojangProxy(skin.url);
	const capeProxy = payload.textures?.CAPE?.url ? viaMojangProxy(payload.textures.CAPE.url) : null;
	const [skinUrl, capeUrl] = await Promise.all([
		cachedImageUrl(skinProxy, applyLegacyHatRule),
		capeProxy ? cachedImageUrl(capeProxy) : Promise.resolve(null)
	]);
	return {
		name: profile.name,
		uuid,
		slim: skin.metadata?.model === 'slim',
		skinUrl,
		capeUrl
	};
}
