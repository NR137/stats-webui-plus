import { describe, expect, it } from 'vitest';
import { isSkill, skillBoard, skillOrder, skillPartNumbers, skillWeights } from './playerStats.ts';
import type { SkillPart } from './types.ts';

const part = (id: string, value: number, target: number): SkillPart => ({ id, value, target, weight: 1, score: 0 });

describe('skills', () => {
	it('seven skills whose weights make up the whole level', () => {
		expect(skillOrder).toHaveLength(7);
		expect(Object.values(skillWeights).reduce((sum, weight) => sum + weight, 0)).toBeCloseTo(1, 10);
	});

	it('each has its own board', () => {
		expect(isSkill('mining')).toBe(true);
		expect(isSkill('crafts')).toBe(false);
		expect(skillBoard('mining')).toBe('skill-mining');
	});
});

describe('skillPartNumbers', () => {
	it("counts are whole, rounded down like the game's numbers, in the visitor's locale", () => {
		expect(skillPartNumbers(part('kills', 5617.9, 4000), 'en')).toBe('5,617 / 4,000');
		expect(skillPartNumbers(part('kills', 5617.9, 4000), 'de')).toBe('5.617 / 4.000');
	});

	it('ratios keep a decimal; distances and hours have their unit', () => {
		expect(skillPartNumbers(part('kills_per_death', 95.24, 100), 'en')).toBe('95.2 / 100');
		expect(skillPartNumbers(part('hours_per_death', 5.33, 8), 'en')).toBe('5.3 / 8 h');
		expect(skillPartNumbers(part('distance', 3318.7, 5000), 'en')).toBe('3,318 / 5,000 km');
	});
});
