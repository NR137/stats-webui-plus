import { createSkinViewer, use, type RendererPlugin, type SkinViewer } from '@daidr/minecraft-skin-renderer';
import { renderAvatar } from '@daidr/minecraft-skin-renderer/canvas2d';
import { WebGLRendererPlugin } from '@daidr/minecraft-skin-renderer/webgl';
import { registerEmotes, type Emote } from '#lib/skins/emotes.ts';
import { equipmentFor, fittedZoom, FOV, type Pose } from '#lib/skins/poses.ts';
import { patchShaders, SHADER_WARNING } from '#lib/skins/shaders.ts';
import type { PlayerTextures } from '#lib/skins/textures.ts';

let registered = false;

function registerRenderer() {
	if (registered) {
		return;
	}
	registered = true;
	const { shaders, patched } = patchShaders(
		WebGLRendererPlugin.getComposedShaders?.() ?? WebGLRendererPlugin.shaders
	);
	if (!patched) {
		console.warn(SHADER_WARNING);
	}
	const plugin: RendererPlugin = { ...WebGLRendererPlugin, shaders, getComposedShaders: () => shaders };
	use(plugin);
}

export function playPose(viewer: SkinViewer, pose: Pose) {
	if (pose === 'walk') {
		viewer.playAnimation(pose, { speed: 0.4 });
	} else {
		viewer.playAnimation(pose);
	}
}

/** Degrees around the player: 0 from the front, 90 the side, 180 the back. */
export function setAngle(viewer: SkinViewer, degrees: number) {
	viewer.setRotation((degrees * Math.PI) / 180, Math.PI / 2);
}

function nextFrame(): Promise<void> {
	return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}

function textureOptions(textures: PlayerTextures) {
	return {
		skin: textures.skin,
		...(textures.cape ? { cape: textures.cape } : {}),
		backEquipment: equipmentFor(textures),
		slim: textures.slim
	};
}

/** The player page's and compare cards' turning body. */
export async function createBodyViewer(canvas: HTMLCanvasElement, textures: PlayerTextures): Promise<SkinViewer> {
	registerRenderer();
	canvas.width = Math.max(160, canvas.clientWidth);
	canvas.height = Math.max(240, canvas.clientHeight);
	const zoom = fittedZoom(canvas.width, canvas.height, 'walk');
	const viewer = await createSkinViewer({
		canvas,
		...textureOptions(textures),
		enableRotate: true,
		enableZoom: false,
		autoRotate: false,
		fov: FOV,
		zoom
	});
	viewer.setAutoRotate(false);
	setAngle(viewer, 0);
	viewer.setZoom(zoom);
	playPose(viewer, 'walk');
	viewer.startRenderLoop();
	return viewer;
}

// The podium's players stand on the blocks, seen a little from above like the blocks' tops. A narrow field of view
// keeps the perspective close to the blocks' straight-on drawing. The camera looks at the player's middle and shows 46
// units: the player (32 tall) and 7 above for raised arms, so as much below.
const PODIUM_FOV = 20;
const PODIUM_PHI = 1.32;
const PODIUM_ZOOM = 46 / 2 / Math.tan((PODIUM_FOV / 2) * (Math.PI / 180));

function podiumPose(viewer: SkinViewer, emote: Emote | null) {
	viewer.setRotation(0, PODIUM_PHI);
	viewer.setZoom(PODIUM_ZOOM);
	if (emote) {
		registerEmotes();
		viewer.playAnimation(`podium-${emote}`);
	} else {
		viewer.playAnimation('idle');
	}
}

/** A player standing still on the ranking's podium, facing the visitor. */
export async function createPodiumViewer(
	canvas: HTMLCanvasElement,
	textures: PlayerTextures,
	emote: Emote | null
): Promise<SkinViewer> {
	registerRenderer();
	// The box has its size only after a frame.
	await nextFrame();
	canvas.width = Math.max(1, Math.round(canvas.clientWidth));
	canvas.height = Math.max(1, Math.round(canvas.clientHeight));
	const viewer = await createSkinViewer({
		canvas,
		...textureOptions(textures),
		enableRotate: false,
		enableZoom: false,
		autoRotate: false,
		zoom: PODIUM_ZOOM,
		fov: PODIUM_FOV
	});
	podiumPose(viewer, emote);
	viewer.startRenderLoop();
	// One frame of the idle animation sets the limbs; then it stays a still picture.
	await nextFrame();
	viewer.pauseAnimation();
	viewer.render();
	return viewer;
}

/** Another player in an existing body or podium viewer (`podium`: the podium's emote, or null for none). */
export async function switchPlayer(
	viewer: SkinViewer,
	textures: PlayerTextures,
	podium: { emote: Emote | null } | null
) {
	await viewer.setSkin(textures.skin);
	await viewer.setCape(textures.cape);
	viewer.setSlim(textures.slim);
	viewer.setBackEquipment(equipmentFor(textures));
	if (podium) {
		podiumPose(viewer, podium.emote);
		viewer.pauseAnimation();
		viewer.render();
	}
}

/** The flat head. */
export function drawHead(canvas: HTMLCanvasElement, textures: PlayerTextures, scale: number): Promise<void> {
	return renderAvatar(canvas, { skin: textures.skin, slim: textures.slim, scale, overlayInflated: true });
}
