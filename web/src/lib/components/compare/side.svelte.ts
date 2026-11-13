import { getPlayer } from '#lib/api.ts';
import type { PlayerDetail } from '#lib/types.ts';

export type SideStatus = 'none' | 'loading' | 'failed' | 'loaded';

/** One side of the comparison: loads its player whenever its key changes. */
export class PlayerSide {
	status = $state<SideStatus>('none');
	player = $state<PlayerDetail | null>(null);

	constructor(key: () => string) {
		$effect(() => {
			const current = key();
			this.player = null;
			if (!current) {
				this.status = 'none';
				return;
			}
			this.status = 'loading';
			// An answer for a key that is no longer chosen is ignored.
			let stale = false;
			getPlayer(current).then(
				(player) => {
					if (!stale) {
						this.player = player;
						this.status = 'loaded';
					}
				},
				() => {
					if (!stale) {
						this.status = 'failed';
					}
				}
			);
			return () => {
				stale = true;
			};
		});
	}
}
