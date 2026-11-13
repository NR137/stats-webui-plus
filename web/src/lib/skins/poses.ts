import type { BackEquipment } from '@daidr/minecraft-skin-renderer';
import type { PlayerTextures } from '#lib/skins/textures.ts';

/** In the order the pose button cycles through them. */
export const poses = ['idle', 'walk', 'run', 'fly'] as const;
export type Pose = (typeof poses)[number];

export const equipments = ['cape', 'elytra', 'none'] as const satisfies readonly BackEquipment[];

export function isPose(value: unknown): value is Pose {
	return poses.includes(value as Pose);
}

export function nextPose(pose: Pose): Pose {
	return poses[(poses.indexOf(pose) + 1) % poses.length];
}

export function nextEquipment(equipment: BackEquipment): BackEquipment {
	return equipments[(equipments.indexOf(equipment) + 1) % equipments.length];
}

export function equipmentFor(textures: PlayerTextures): BackEquipment {
	return textures.cape ? 'cape' : 'none';
}

export const FOV = 70;

/**
 * The camera distance that fits the body into a canvas: 32 units tall and, with swinging arms and legs, up to about 18
 * wide from any angle; flying it lies flat and is about 34 wide. It fills 70 % of the height (or of the width on a
 * narrow canvas), below 1 also because perspective makes the head and feet, nearer to the camera, look bigger.
 */
export function fittedZoom(width: number, height: number, pose: Pose): number {
	const poseWidth = pose === 'fly' ? 34 : 18;
	const visible = Math.max(32, (poseWidth * height) / Math.max(width, 1)) / 0.7;
	return visible / 2 / Math.tan(((FOV / 2) * Math.PI) / 180);
}
