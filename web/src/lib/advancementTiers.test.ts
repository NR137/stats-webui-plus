import { describe, expect, it } from 'vitest';
import { advancementProgress } from './advancementTiers';

// CHANGELOG 1.3.0: Stone, Copper (10 %), Iron (25 %), Gold (40 %), Diamond (60 %), Netherite (80 %) and
// Dragon Egg (100 %); the tooltip tells how many advancements are missing for the next tier.
describe('advancementProgress', () => {
	it.each([
		[0, 'stone'],
		[9, 'stone'],
		[10, 'copper'],
		[24, 'copper'],
		[25, 'iron'],
		[40, 'gold'],
		[60, 'diamond'],
		[80, 'netherite'],
		[99, 'netherite'],
		[100, 'dragon_egg']
	])('%i of 100 is %s', (done, tier) => {
		expect(advancementProgress(done, 100).tier.id).toBe(tier);
	});

	it('counts what is missing for the next tier', () => {
		expect(advancementProgress(0, 100)).toMatchObject({ next: { id: 'copper' }, missing: 10 });
		expect(advancementProgress(9, 100)).toMatchObject({ next: { id: 'copper' }, missing: 1 });
		expect(advancementProgress(10, 100)).toMatchObject({ next: { id: 'iron' }, missing: 15 });
		expect(advancementProgress(99, 100)).toMatchObject({ next: { id: 'dragon_egg' }, missing: 1 });
	});

	it('has nothing after the dragon egg', () => {
		expect(advancementProgress(100, 100)).toMatchObject({ next: null, missing: 0, share: 1 });
	});

	it('rounds missing advancements up to whole ones', () => {
		// Copper needs 10 % of 7 = 0.7, so one advancement.
		expect(advancementProgress(0, 7)).toMatchObject({ next: { id: 'copper' }, missing: 1 });
		const progress = advancementProgress(0, 7);
		expect(Number.isInteger(progress.missing)).toBe(true);
	});

	it('reaching the next tier needs exactly the missing ones', () => {
		for (const total of [7, 50, 122, 125]) {
			for (let done = 0; done < total; done++) {
				const progress = advancementProgress(done, total);
				expect(progress.next).not.toBeNull();
				expect(advancementProgress(done + progress.missing, total).tier.id).toBe(progress.next!.id);
				if (progress.missing > 1) {
					expect(advancementProgress(done + progress.missing - 1, total).tier.id).toBe(progress.tier.id);
				}
			}
		}
	});

	it('share is between 0 and 1', () => {
		expect(advancementProgress(30, 120).share).toBeCloseTo(0.25);
		expect(advancementProgress(0, 0).share).toBeGreaterThanOrEqual(0);
		expect(advancementProgress(0, 0).share).toBeLessThanOrEqual(1);
		expect(advancementProgress(0, 0).tier.id).toBe('stone');
	});
});
