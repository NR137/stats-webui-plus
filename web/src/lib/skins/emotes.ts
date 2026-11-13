import { BoneIndex, degToRad, quatFromEuler, registerAnimation } from '@daidr/minecraft-skin-renderer';

/** The podium's still poses: our own, as the game has no emotes. */
export const emotes = ['cheer', 'wave', 'dab', 'hero', 'fistPump'] as const;
export type Emote = (typeof emotes)[number];

type Angles = [x: number, y: number, z: number];

const poses: Record<Emote, Partial<Record<BoneIndex, Angles>>> = {
	// Both arms up in a V, the head a little raised.
	cheer: {
		[BoneIndex.Head]: [-12, 0, 0],
		[BoneIndex.RightArm]: [0, 0, -155],
		[BoneIndex.LeftArm]: [0, 0, 155]
	},
	// One arm raised high to the side.
	wave: {
		[BoneIndex.Head]: [-4, 0, 0],
		[BoneIndex.RightArm]: [0, 0, -140],
		[BoneIndex.LeftArm]: [0, 0, 4]
	},
	// The head bowed into one arm held across the face, the other arm stretched up and out.
	dab: {
		[BoneIndex.Head]: [28, -25, 10],
		[BoneIndex.RightArm]: [-105, 0, 45],
		[BoneIndex.LeftArm]: [0, 0, 125]
	},
	// Proud like a superhero, fists on the hips: the arms a little out to the sides and back, the chin up.
	hero: {
		[BoneIndex.Head]: [-14, 0, 0],
		[BoneIndex.RightArm]: [22, 0, -24],
		[BoneIndex.LeftArm]: [22, 0, 24]
	},
	// One arm punched straight up, the other down at the side, the head raised.
	fistPump: {
		[BoneIndex.Head]: [-16, 0, 0],
		[BoneIndex.RightArm]: [0, 0, -172],
		[BoneIndex.LeftArm]: [0, 0, 6]
	}
};

let registered = false;

/** Registers the poses as one-frame animations, so a viewer takes them with playAnimation. */
export function registerEmotes() {
	if (registered) {
		return;
	}
	registered = true;
	for (const emote of emotes) {
		registerAnimation({
			name: `podium-${emote}`,
			duration: 1,
			loop: true,
			tracks: Object.entries(poses[emote]).map(([bone, [x, y, z]]) => ({
				boneIndex: Number(bone) as BoneIndex,
				keyframes: [0, 1].map((time) => ({
					time,
					rotation: quatFromEuler(degToRad(x), degToRad(y), degToRad(z))
				}))
			}))
		});
	}
}
