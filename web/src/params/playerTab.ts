import type { ParamMatcher } from '@sveltejs/kit';

// The optional last segment of a player URL: /players/<name>/advancements (/players/<name> shows the stats).
export const match = ((param: string): param is 'stats' | 'advancements' =>
	param === 'stats' || param === 'advancements') satisfies ParamMatcher;
