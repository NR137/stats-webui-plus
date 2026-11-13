<script lang="ts">
	import type { IconName } from '#lib/components/iconNames.ts';

	let { name }: { name: IconName } = $props();

	// The Union Flag's diagonals are clipped per instance; ids must be unique on the page.
	const uid = $props.id();
	const flags: IconName[] = ['es', 'en', 'fr', 'de'];
</script>

{#if flags.includes(name)}
	<svg class="glyph flag" viewBox="0 0 18 12" preserveAspectRatio="none" aria-hidden="true">
		{#if name === 'es'}
			<rect width="18" height="12" fill="#aa151b" />
			<rect y="3" width="18" height="6" fill="#f1bf00" />
		{:else if name === 'en'}
			<clipPath id="{uid}-quarters">
				<path d="M9 6h9v6zv6h-9zh-9v-6zv-6h9z" />
			</clipPath>
			<rect width="18" height="12" fill="#012169" />
			<path d="M0 0l18 12M18 0L0 12" stroke="#ffffff" stroke-width="2.4" />
			<path d="M0 0l18 12M18 0L0 12" stroke="#c8102e" stroke-width="1.6" clip-path="url(#{uid}-quarters)" />
			<path d="M9 0v12M0 6h18" stroke="#ffffff" stroke-width="4" />
			<path d="M9 0v12M0 6h18" stroke="#c8102e" stroke-width="2.4" />
		{:else if name === 'fr'}
			<rect width="18" height="12" fill="#ffffff" />
			<rect width="6" height="12" fill="#0055a4" />
			<rect x="12" width="6" height="12" fill="#ef4135" />
		{:else}
			<rect width="18" height="12" fill="#000000" />
			<rect y="4" width="18" height="4" fill="#dd0000" />
			<rect y="8" width="18" height="4" fill="#ffce00" />
		{/if}
	</svg>
{:else}
	<svg
		class="glyph"
		viewBox="0 0 24 24"
		fill="none"
		stroke="currentColor"
		stroke-width="1.8"
		stroke-linecap="round"
		stroke-linejoin="round"
		aria-hidden="true"
	>
		{#if name === 'sun'}
			<circle cx="12" cy="12" r="4" />
			<path
				d="M12 2.5V5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8"
			/>
		{:else if name === 'moon'}
			<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z" />
		{:else if name === 'monitor'}
			<rect x="3" y="4" width="18" height="12" rx="1.5" />
			<path d="M12 16v4M8 20h8" />
		{:else if name === 'chevron'}
			<path d="M6 9l6 6 6-6" />
		{:else if name === 'menu'}
			<path d="M4 6h16M4 12h16M4 18h16" />
		{:else if name === 'back'}
			<path d="M19 12H5M11 6l-6 6 6 6" />
		{:else if name === 'poseIdle'}
			<circle cx="12" cy="4.5" r="2" />
			<path d="M12 7v7M12 9l-2.5 5M12 9l2.5 5M12 14l-1.5 7M12 14l1.5 7" />
		{:else if name === 'poseWalk'}
			<circle cx="12" cy="4.5" r="2" />
			<path d="M12 7l-0.5 7M12 9l-3 4M12 9l3 3.5M11.5 14L9 21M11.5 14l3 6.5" />
		{:else if name === 'poseRun'}
			<circle cx="14" cy="4.5" r="2" />
			<path d="M13.3 7l-2.8 6.5M12.8 8.5l4 2-1 2.5M12.8 8.5L8.5 11M10.5 13.5l4.5 2.5-1 5M10.5 13.5L6.5 19" />
		{:else if name === 'poseFly'}
			<circle cx="5.5" cy="10" r="2" />
			<path d="M8 11.5h8.5M16.5 11.5l5-2M16.5 11.5l5 2.5M9.5 12L3.5 15.5M10.5 12l-5 4.5" />
		{:else if name === 'pause'}
			<path d="M9 5v14M15 5v14" />
		{:else if name === 'play'}
			<path d="M8 5v14l11-7z" />
		{:else if name === 'rotate'}
			<path d="M19.5 13.5a7.5 7.5 0 1 1-2.2-6.8L20 9.5M20 4.5v5h-5" />
		{:else if name === 'zoom'}
			<circle cx="10.5" cy="10.5" r="6.5" />
			<path d="M15.3 15.3l5.2 5.2M10.5 7.5v6M7.5 10.5h6" />
		{:else if name === 'cape' || name === 'capeOff'}
			<path d="M5.5 4.5h13M7.5 4.5L6 20.5h12L16.5 4.5M12 7.5v10" />
			{#if name === 'capeOff'}
				<path d="M3.5 3l17 18" />
			{/if}
		{:else if name === 'elytra'}
			<path d="M11 4C7 5 5.5 9 5.5 20.5l3.5-2.5 2 1.5zM13 4c4 1 5.5 5 5.5 16.5L15 18l-2 1.5z" />
		{/if}
	</svg>
{/if}

<style>
	.glyph {
		display: block;
		flex-shrink: 0;
		width: 1.05rem;
		height: 1.05rem;
	}

	.flag {
		width: 1.15rem;
		height: 0.78rem;
		border-radius: 2px;
		clip-path: inset(0 round 2px);
	}
</style>
