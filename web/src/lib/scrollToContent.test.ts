import { describe, expect, it } from 'vitest';
import { samePage } from './scrollToContent.ts';

// Pages open at their content, except when the same page only changes its URL: a player's tabs, a search, the players
// compared (those keep where the visitor is).
const at = (id: string, params: Record<string, string> = {}) => ({ route: { id }, params });
const player = (name: string, tab?: string) => at('/players/[name]/[[tab=playerTab]]', tab ? { name, tab } : { name });

describe('samePage', () => {
	it("a player's tabs are the same page", () => {
		expect(samePage(player('Steve'), player('Steve', 'advancements'))).toBe(true);
	});

	it('the same page with another query (search, the players compared)', () => {
		expect(samePage(at('/compare'), at('/compare'))).toBe(true);
	});

	it('another player, or another page, is not', () => {
		expect(samePage(player('Steve'), player('Alex'))).toBe(false);
		expect(samePage(at('/players'), at('/leaderboards'))).toBe(false);
	});

	it('the first page of a visit is not', () => {
		expect(samePage(null, at('/players'))).toBe(false);
	});
});
