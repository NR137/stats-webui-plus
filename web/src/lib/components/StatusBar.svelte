<script lang="ts">
	import { branding, refreshServerStatus, serverStatus } from '$lib/branding.svelte';
	import { t } from '$lib/i18n/i18n.svelte';

	const REFRESH_MS = 30_000;

	let copied = $state(false);

	// Only poll while the page is visible; when the visitor comes back, refresh at once so the numbers aren't stale.
	$effect(() => {
		let timer: ReturnType<typeof setInterval> | null = null;
		const start = () => {
			timer ??= setInterval(refreshServerStatus, REFRESH_MS);
		};
		const stop = () => {
			if (timer) {
				clearInterval(timer);
				timer = null;
			}
		};
		const onVisibilityChange = () => {
			if (document.hidden) {
				stop();
			} else {
				void refreshServerStatus();
				start();
			}
		};
		if (!document.hidden) {
			start();
		}
		document.addEventListener('visibilitychange', onVisibilityChange);
		return () => {
			stop();
			document.removeEventListener('visibilitychange', onVisibilityChange);
		};
	});

	// navigator.clipboard only exists on HTTPS or localhost; plain http://<ip>:<port> needs the old way.
	function copyText(text: string): Promise<void> {
		if (navigator.clipboard && window.isSecureContext) {
			return navigator.clipboard.writeText(text);
		}
		const field = document.createElement('textarea');
		field.value = text;
		field.setAttribute('readonly', '');
		field.style.position = 'fixed';
		field.style.opacity = '0';
		document.body.append(field);
		field.select();
		const ok = document.execCommand('copy');
		field.remove();
		return ok ? Promise.resolve() : Promise.reject(new Error('copy failed'));
	}

	function copyAddress() {
		copyText(branding.serverAddress)
			.then(() => {
				copied = true;
				setTimeout(() => (copied = false), 1500);
			})
			.catch(() => undefined);
	}
</script>

<div class="status-bar">
	<span class="state" class:offline={!serverStatus.online}>
		<span class="dot" aria-hidden="true"></span>
		{serverStatus.online ? t('status.online') : t('status.offline')}
	</span>
	{#if branding.serverAddress}
		<button type="button" class="address" title={t('status.copy')} onclick={copyAddress}>
			{copied ? t('status.copied') : branding.serverAddress}
		</button>
	{/if}
	{#if serverStatus.online && serverStatus.players !== null && serverStatus.maxPlayers !== null}
		<span class="players">{t('status.players', { count: serverStatus.players, max: serverStatus.maxPlayers })}</span>
	{/if}
</div>

<style>
	.status-bar {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		align-items: center;
		gap: 0.35rem 1.2rem;
		color: var(--color-muted);
		font-size: 0.9rem;
	}

	.state {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
		color: var(--color-accent);
	}

	.state.offline {
		color: #e04040;
	}

	.dot {
		width: 0.55rem;
		height: 0.55rem;
		background: currentColor;
		box-shadow: 0 0 0 2px var(--color-border);
	}

	/* Plain text, not the site's big Minecraft button style that every <button> gets. */
	.address {
		all: unset;
		cursor: pointer;
		color: var(--color-text);
		border-bottom: 1px dotted currentColor;
	}

	.address:hover,
	.address:focus-visible {
		color: var(--color-hover);
	}

	.players {
		font-variant-numeric: tabular-nums;
	}
</style>
