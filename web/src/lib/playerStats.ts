import type { SkillPart } from '#lib/types.ts';

// The seven skills; keep in step with Skills in Java.

export const skillOrder = ['milestones', 'combat', 'exploration', 'mining', 'building', 'farming', 'survival'] as const;

export type Skill = (typeof skillOrder)[number];

export function isSkill(id: string): id is Skill {
	return (skillOrder as readonly string[]).includes(id);
}

export const skillIcons: Record<Skill, string> = {
	milestones: 'minecraft:beacon',
	combat: 'minecraft:iron_sword',
	exploration: 'minecraft:filled_map',
	mining: 'minecraft:diamond_pickaxe',
	building: 'minecraft:bricks',
	farming: 'minecraft:wheat',
	survival: 'minecraft:golden_apple'
};

/** The bars on the player and compare pages. */
export const skillColors: Record<Skill, string> = {
	milestones: '#9b59d0',
	combat: '#c53a3a',
	exploration: '#55ff55',
	mining: '#4fc3f7',
	building: '#d9844a',
	farming: '#e6c35c',
	survival: '#f28aa0'
};

/** Each skill's share of the level. */
export const skillWeights: Record<Skill, number> = {
	milestones: 0.2,
	combat: 0.18,
	exploration: 0.15,
	mining: 0.14,
	building: 0.13,
	farming: 0.1,
	survival: 0.1
};

export function skillBoard(skill: Skill): string {
	return `skill-${skill}`;
}

// Ratios keep a decimal; everything else is a whole count, rounded down like the game's numbers.
const RATIOS = new Set(['hours_per_death', 'kills_per_death']);

const UNITS: Record<string, string> = {
	distance: ' km',
	hours_per_death: ' h'
};

/** "4,120 / 5,000 km": a part's value against the number that gives 100, in that locale. */
export function skillPartNumbers(part: SkillPart, locale: string): string {
	const ratio = RATIOS.has(part.id);
	const format = new Intl.NumberFormat(locale, { maximumFractionDigits: ratio ? 1 : 0 });
	const number = (value: number) => format.format(ratio ? value : Math.floor(value));
	return `${number(part.value)} / ${number(part.target)}${UNITS[part.id] ?? ''}`;
}
