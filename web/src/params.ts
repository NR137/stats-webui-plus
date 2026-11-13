import { defineParams } from '@sveltejs/kit/params';

export const params = defineParams({
	// The optional last segment of a player URL: /players/<name>/advancements (/players/<name> shows the stats).
	playerTab: (param: string) => (param === 'stats' || param === 'advancements' ? param : undefined)
});
