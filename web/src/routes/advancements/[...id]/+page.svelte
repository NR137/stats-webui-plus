<script lang="ts">
	import { page } from '$app/state';
	import { getAdvancement, getAdvancementMost, NotFoundError } from '#lib/api.ts';
	import { advancementDescription, advancementTitle, playerAdvancementHref } from '#lib/advancements.ts';
	import { pageTitleTag } from '#lib/branding.svelte.ts';
	import AdvancementIcon from '#lib/components/AdvancementIcon.svelte';
	import BackNav from '#lib/components/BackNav.svelte';
	import Podium from '#lib/components/Podium.svelte';
	import Loader from '#lib/components/Loader.svelte';
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import RankedList from '#lib/components/RankedList.svelte';
	import { formatValue } from '#lib/format.ts';
	import { boardHint, formatWhen, pageTitle, t } from '#lib/i18n/i18n.svelte.ts';
	import { pathToId } from '#lib/ids.ts';
	import { playerPath } from '#lib/players.ts';
	import type { AdvancementDetailResponse, LeaderboardResponse, PlayerRef } from '#lib/types.ts';

	type Status = 'loading' | 'ready' | 'notFound' | 'failed';
	type Entry = PlayerRef & { rank: number; value?: number; completedAt?: number | null };

	const path = $derived(page.params.id ?? '');
	const isMost = $derived(path === 'most');
	const advancementId = $derived(isMost ? '' : pathToId(path));

	let most = $state.raw<LeaderboardResponse | null>(null);
	let advancement = $state.raw<AdvancementDetailResponse | null>(null);
	let status = $state<Status>('loading');

	// A new address starts over: the old ranking must not show under the new title.
	$effect(() => {
		const wantMost = isMost;
		const id = advancementId;
		let cancelled = false;
		most = null;
		advancement = null;
		status = 'loading';
		const request: Promise<unknown> = wantMost
			? getAdvancementMost().then((answer) => {
					if (!cancelled) {
						most = answer;
					}
				})
			: getAdvancement(id).then((answer) => {
					if (!cancelled) {
						advancement = answer;
					}
				});
		request
			.then(() => {
				if (!cancelled) {
					status = 'ready';
				}
			})
			.catch((error) => {
				if (!cancelled) {
					status = error instanceof NotFoundError ? 'notFound' : 'failed';
				}
			});
		return () => {
			cancelled = true;
		};
	});

	const entries = $derived<Entry[]>(isMost ? (most?.entries ?? []) : (advancement?.entries ?? []));

	const title = $derived(
		isMost
			? t('advancements.most')
			: advancement
				? advancementTitle(advancement.id, advancement.title)
				: t('menu.advancements')
	);
	const description = $derived(
		advancement ? advancementDescription(advancement.id, advancement.description) : ''
	);

	const lead = $derived.by(() => {
		if (isMost) {
			return t('advancements.mostLead');
		}
		if (!advancement) {
			return '';
		}
		const parts = [description];
		if (advancement.hidden) {
			parts.push(t('advancements.hidden'));
		}
		if (advancement.experience > 0) {
			parts.push(t('advancements.reward', { xp: advancement.experience }));
		}
		return parts.filter(Boolean).join(' · ');
	});

	const hint = $derived(isMost ? boardHint('advancements') : description);
	const empty = $derived(isMost ? t('advancements.nobody') : t('advancements.empty'));

	function display(entry: Entry): string {
		if (isMost) {
			return formatValue(entry.value ?? 0, most?.unit || 'count');
		}
		return formatWhen(entry.completedAt);
	}

	// "Most" leads to the player's advancements; an advancement to the player's own page for it.
	function href(entry: Entry): string {
		if (isMost) {
			return playerPath(entry.name, entry.uuid) + '/advancements';
		}
		return playerAdvancementHref(entry.name, advancementId, entry.uuid);
	}
</script>

<svelte:head>
	<title>{pageTitle(title)}</title>
</svelte:head>

<BackNav />
<svelte:element this={pageTitleTag()} class="page-title">
	{#if isMost}
		<ItemIcon id="minecraft:experience_bottle" />
	{:else if advancement}
		<AdvancementIcon
			id={advancement.id}
			icon={advancement.icon}
			frame={advancement.frame}
			obtained={advancement.entries.length > 0}
		/>
	{/if}
	<span>{title}</span>
</svelte:element>
{#if lead}
	<p class="lead">{lead}</p>
{/if}

{#if status === 'failed'}
	<p class="message">{t('site.loadError')}</p>
{:else if status === 'notFound'}
	<p class="message">{t('advancements.notFound')}</p>
{:else if status === 'loading'}
	<Loader />
{:else if entries.length === 0}
	<p class="message">{empty}</p>
{:else}
	<Podium {entries} {display} {hint} {href} />
	{#key path}
		<RankedList {entries} value={display} {hint} {href} />
	{/key}
{/if}

<style>
	.page-title {
		display: flex;
		gap: 0.55rem;
		align-items: center;
		justify-content: center;
		margin: 0;
		font-size: 2rem;
		text-align: center;
	}
</style>
