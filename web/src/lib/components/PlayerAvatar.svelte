<script lang="ts">
	import { untrack } from 'svelte';
	import type { BackEquipment, SkinViewer } from '@daidr/minecraft-skin-renderer';
	import Icon from '#lib/components/Icon.svelte';
	import type { IconName } from '#lib/components/iconNames.ts';
	import Loader from '#lib/components/Loader.svelte';
	import { branding } from '#lib/branding.svelte.ts';
	import { t } from '#lib/i18n/i18n.svelte.ts';
	import type { Emote } from '#lib/skins/emotes.ts';
	import { loadTextures } from '#lib/skins/textures.ts';
	import {
		equipmentFor,
		fittedZoom,
		isPose,
		nextEquipment,
		nextPose,
		type Pose
	} from '#lib/skins/poses.ts';
	import { createBodyViewer, createPodiumViewer, drawHead, playPose, setAngle, switchPlayer } from '#lib/skins/viewer.ts';

	let {
		uuid,
		variant,
		scale = 8,
		controls = true,
		presentation = 'default',
		emote
	}: {
		uuid: string;
		variant: 'head' | 'body';
		scale?: number;
		controls?: boolean;
		presentation?: 'default' | 'podium';
		// The podium's pose.
		emote?: Emote;
	} = $props();

	const podium = $derived(variant === 'body' && presentation === 'podium');

	let headCanvas = $state<HTMLCanvasElement>();
	let viewport = $state<HTMLDivElement>();
	let bodyCanvas = $state.raw<HTMLCanvasElement | null>(null);
	let viewer = $state.raw<SkinViewer | null>(null);

	let failed = $state(false);
	let loading = $state(true);
	let pose = $state<Pose>('walk');
	let posePicked = false;
	let rotatePicked = false;
	let paused = $state(false);
	let autoRotate = $state(false);
	let scrollZoom = $state(false);
	let equipment = $state<BackEquipment>('none');

	const poseIcons: Record<Pose, IconName> = { idle: 'poseIdle', walk: 'poseWalk', run: 'poseRun', fly: 'poseFly' };
	const equipmentIcons: Record<BackEquipment, IconName> = { cape: 'cape', elytra: 'elytra', none: 'capeOff' };

	$effect(() => {
		const canvas = headCanvas;
		if (variant !== 'head' || !canvas || !uuid) {
			return;
		}
		const id = uuid;
		const size = scale;
		let outdated = false;
		failed = false;
		loading = true;
		(async () => {
			try {
				const textures = await loadTextures(id);
				if (outdated) {
					return;
				}
				await drawHead(canvas, textures, size);
				if (!outdated) {
					loading = false;
				}
			} catch {
				if (!outdated) {
					failed = true;
					loading = false;
				}
			}
		})();
		return () => {
			outdated = true;
		};
	});

	// Shows a uuid in the current body or podium viewer; replaced whenever the presentation changes.
	let showPlayer = $state.raw<((id: string) => void) | null>(null);

	$effect(() => {
		if (variant !== 'body' || !viewport) {
			return;
		}
		const box: HTMLDivElement = viewport;
		const isPodium = presentation === 'podium';
		untrack(() => {
			failed = false;
			loading = true;
			viewer = null;
			pose = isPodium ? 'idle' : 'walk';
			posePicked = false;
			rotatePicked = false;
			paused = isPodium;
			autoRotate = false;
			scrollZoom = false;
			equipment = 'none';
		});

		let alive = true;
		let own: SkinViewer | null = null;
		let canvas: HTMLCanvasElement | null = null;
		// One uuid after the other: a later one waits for the one before.
		let queue = Promise.resolve();

		async function show(id: string) {
			if (!alive) {
				return;
			}
			failed = false;
			loading = true;
			try {
				const textures = await loadTextures(id);
				if (!alive) {
					return;
				}
				if (own) {
					await switchPlayer(own, textures, isPodium ? { emote: untrack(() => emote) ?? null } : null);
					if (!alive) {
						return;
					}
					equipment = equipmentFor(textures);
					loading = false;
					return;
				}
				// The library takes over the canvas, so every viewer gets a fresh one.
				const fresh = document.createElement('canvas');
				box.append(fresh);
				canvas = fresh;
				bodyCanvas = fresh;
				let created: SkinViewer;
				try {
					created = isPodium
						? await createPodiumViewer(fresh, textures, untrack(() => emote) ?? null)
						: await createBodyViewer(fresh, textures);
				} catch (error) {
					fresh.remove();
					if (canvas === fresh) {
						canvas = null;
						bodyCanvas = null;
					}
					throw error;
				}
				if (!alive) {
					created.dispose();
					fresh.remove();
					return;
				}
				own = created;
				viewer = created;
				equipment = equipmentFor(textures);
				loading = false;
			} catch {
				if (!alive) {
					return;
				}
				// Once a viewer exists the previous player simply stays.
				if (!own) {
					failed = true;
				}
				loading = false;
			}
		}

		showPlayer = (id) => {
			queue = queue.then(() => show(id));
		};

		return () => {
			alive = false;
			showPlayer = null;
			own?.dispose();
			own = null;
			canvas?.remove();
			canvas = null;
			bodyCanvas = null;
			viewer = null;
		};
	});

	$effect(() => {
		const show = showPlayer;
		const id = uuid;
		if (show && id) {
			show(id);
		}
	});

	$effect(() => {
		bodyCanvas?.setAttribute('aria-label', t('skin.body'));
	});

	function fitCamera() {
		const box = viewport;
		if (!viewer || !box || scrollZoom) {
			return;
		}
		const width = box.clientWidth;
		const height = box.clientHeight;
		if (width > 0 && height > 0) {
			viewer.setZoom(fittedZoom(width, height, pose));
		}
	}

	// The server's avatar settings; again when they change, as the viewer can exist before /api/status has answered.
	$effect(() => {
		const current = viewer;
		if (!current || podium) {
			return;
		}
		const configured: Pose = isPose(branding.avatarPose) ? branding.avatarPose : 'walk';
		const angle = branding.avatarAngle;
		const rotate = branding.avatarRotate;
		untrack(() => {
			if (!posePicked) {
				pose = configured;
				paused = false;
				playPose(current, pose);
				fitCamera();
			}
			setAngle(current, angle);
			if (!rotatePicked) {
				autoRotate = rotate;
				current.setAutoRotate(rotate);
			}
		});
	});

	// The viewport changes with the window, a rotated phone, or the info column next to it growing once its data is in.
	$effect(() => {
		const current = viewer;
		const box = viewport;
		if (!current || !box || podium) {
			return;
		}
		const observer = new ResizeObserver(() => {
			const width = box.clientWidth;
			const height = box.clientHeight;
			if (width > 0 && height > 0) {
				current.resize(width, height);
				fitCamera();
			}
		});
		observer.observe(box);
		return () => observer.disconnect();
	});

	function changePose() {
		if (!viewer) {
			return;
		}
		pose = nextPose(pose);
		posePicked = true;
		paused = false;
		playPose(viewer, pose);
		fitCamera();
	}

	function togglePause() {
		if (!viewer) {
			return;
		}
		if (paused) {
			viewer.resumeAnimation();
		} else {
			viewer.pauseAnimation();
		}
		paused = !paused;
	}

	function toggleRotate() {
		if (!viewer) {
			return;
		}
		autoRotate = !autoRotate;
		rotatePicked = true;
		viewer.setAutoRotate(autoRotate);
	}

	function toggleScrollZoom() {
		if (!viewer) {
			return;
		}
		scrollZoom = !scrollZoom;
		viewer.setEnableZoom(scrollZoom);
		// Turning it off returns to the fitted distance.
		fitCamera();
	}

	function changeEquipment() {
		if (!viewer) {
			return;
		}
		equipment = nextEquipment(equipment);
		viewer.setBackEquipment(equipment);
	}

	// Pressing a button must not start turning the player.
	function keepFromViewer(event: PointerEvent) {
		event.stopPropagation();
	}
</script>

{#snippet action(text: string, icon: IconName, onclick: () => void, pressed?: boolean)}
	<button
		type="button"
		class:on={pressed}
		aria-pressed={pressed}
		title={text}
		aria-label={text}
		{onclick}
		onpointerdown={keepFromViewer}
	>
		<Icon name={icon} />
	</button>
{/snippet}

<div
	class="skin"
	class:head={variant === 'head'}
	class:body={variant === 'body'}
	class:podium
	class:failed
	class:loading
>
	<div class="stage">
		{#if variant === 'head'}
			<canvas bind:this={headCanvas} aria-label={t('skin.head')}></canvas>
		{:else}
			<div class="viewport" bind:this={viewport}></div>
		{/if}
		{#if loading && !failed}
			<Loader embed />
		{/if}
		{#if failed}
			<span class="error">{t('site.loadError')}</span>
		{/if}
		{#if variant === 'body' && !podium && controls && viewer && !failed}
			<div class="actions" role="group" aria-label={t('skin.actions')}>
				{@render action(t(`skin.poses.${pose}`), poseIcons[pose], changePose)}
				{@render action(paused ? t('skin.resume') : t('skin.pause'), paused ? 'play' : 'pause', togglePause, paused)}
				{@render action(t('skin.autoRotate'), 'rotate', toggleRotate, autoRotate)}
				{@render action(t('skin.scrollZoom'), 'zoom', toggleScrollZoom, scrollZoom)}
				{@render action(t(`skin.equipment.${equipment}`), equipmentIcons[equipment], changeEquipment)}
			</div>
		{/if}
	</div>
</div>

<style>
	.skin {
		position: relative;
		display: inline-flex;
		flex-direction: column;
		flex-shrink: 0;
		align-items: stretch;
	}

	.stage {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	canvas,
	.viewport :global(canvas) {
		display: block;
		image-rendering: pixelated;
	}

	.head .stage,
	.head canvas {
		width: 2.5rem;
		height: 2.5rem;
	}

	.body .stage {
		width: 14rem;
		height: 20rem;
		overflow: hidden;
		border: 2px solid var(--color-border);
		box-shadow: var(--panel-edge);
		background: var(--color-card);
		cursor: grab;
	}

	.body .stage:active {
		cursor: grabbing;
	}

	.viewport,
	.viewport :global(canvas) {
		width: 100%;
		height: 100%;
	}

	.podium,
	.podium .stage {
		width: 100%;
		height: 100%;
	}

	.podium .stage {
		border: none;
		box-shadow: none;
		background: transparent;
		cursor: default;
	}

	/* Hidden rather than removed, so the size stays. */
	.loading canvas,
	.failed canvas,
	.loading .viewport :global(canvas),
	.failed .viewport :global(canvas) {
		visibility: hidden;
	}

	.error {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0.4rem;
		color: var(--color-muted);
		font-size: 0.72rem;
		text-align: center;
	}

	.actions {
		position: absolute;
		left: 0.45rem;
		bottom: 0.45rem;
		z-index: 1;
		display: flex;
		gap: 0.28rem;
	}

	.actions button {
		display: grid;
		place-items: center;
		width: 2rem;
		height: 2rem;
		min-height: 0;
		padding: 0;
	}

	.actions button:hover,
	.actions button.on {
		color: var(--color-hover);
	}

	.actions button :global(svg) {
		width: 1rem;
		height: 1rem;
	}
</style>
