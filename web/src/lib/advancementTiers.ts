// Advancement progress tiers on player pages, named after Minecraft materials. Only shown, never stored.
// The upper tiers need much more progress, since the last advancements take far longer than the first ones.
// `color` is the bar fill, colored like the material (the dragon egg's gradient also shimmers, see .tier-fill).
export const advancementTiers = [
	{ id: 'stone', min: 0, icon: 'minecraft:cobblestone', color: '#8f8f8f' },
	{ id: 'copper', min: 0.1, icon: 'minecraft:copper_ingot', color: '#d0784a' },
	{ id: 'iron', min: 0.25, icon: 'minecraft:iron_ingot', color: '#e2e2e2' },
	{ id: 'gold', min: 0.4, icon: 'minecraft:gold_ingot', color: '#f8d23c' },
	{ id: 'diamond', min: 0.6, icon: 'minecraft:diamond', color: '#4fe3d6' },
	{ id: 'netherite', min: 0.8, icon: 'minecraft:netherite_ingot', color: '#7b6371' },
	{ id: 'dragon_egg', min: 1, icon: 'minecraft:dragon_egg', color: 'linear-gradient(90deg, #4b1a6e, #c25cf5, #4b1a6e)' }
] as const;

export type AdvancementTier = (typeof advancementTiers)[number];

export type TierProgress = {
	tier: AdvancementTier;
	// Share of completed advancements, 0 to 1.
	share: number;
	next: AdvancementTier | null;
	// Advancements still missing to reach `next`.
	missing: number;
};

export function advancementProgress(done: number, total: number): TierProgress {
	const share = total > 0 ? Math.min(1, done / total) : 0;
	let index = 0;
	advancementTiers.forEach((tier, i) => {
		if (share >= tier.min) {
			index = i;
		}
	});
	const next = advancementTiers[index + 1] ?? null;
	return {
		tier: advancementTiers[index],
		share,
		next,
		missing: next ? Math.max(1, Math.ceil(next.min * total) - done) : 0
	};
}
