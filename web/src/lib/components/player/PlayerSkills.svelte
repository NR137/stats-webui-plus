<script lang="ts">
	import ItemIcon from '#lib/components/ItemIcon.svelte';
	import './meters.css';
	import { i18n, t, tOptional, type MessageKey } from '#lib/i18n/i18n.svelte.ts';
	import { skillBoard, skillColors, skillIcons, skillOrder, skillPartNumbers } from '#lib/playerStats.ts';
	import type { Skill } from '#lib/types.ts';

	let { skills }: { skills: Record<string, Skill> } = $props();

	const present = $derived(skillOrder.flatMap((id) => (skills[id] ? [{ id, skill: skills[id] }] : [])));
</script>

{#if present.length > 0}
	<div class="meters">
		<p class="meters-caption">{t('player.skills')}{' '}<a class="meters-how" href="/about">{t('about.howLink')}</a></p>
		{#each present as { id, skill } (id)}
			<div class="meter">
				<span class="meter-icon"><ItemIcon id={skillIcons[id]} /></span>
				<div class="meter-line">
					<a href="/leaderboards/{skillBoard(id)}">{t(`skills.${id}.name` as MessageKey)}</a>
					<span class="meter-value">{Math.floor(skill.value)}</span>
				</div>
				<!-- 100 is very good, more is possible: past it the bar stays full. -->
				<span class="meter-bar"
					><span
						class="meter-fill"
						style:width="{Math.min(100, Math.max(0, skill.value))}%"
						style:background={skillColors[id]}
					></span></span
				>
				{#if skill.parts.length > 0}
					<p class="parts">
						{#each skill.parts as part, index (part.id)}{#if index > 0}<span class="separator" aria-hidden="true"
									>·</span
								>{' '}{/if}<span class="part"
								>{tOptional(`skills.parts.${part.id}`, part.id)}
								<span class="numbers">{skillPartNumbers(part, i18n.locale)}</span></span
							>{/each}
					</p>
				{/if}
			</div>
		{/each}
	</div>
{/if}

<style>
	.meter-line a {
		color: inherit;
	}

	.parts {
		grid-column: 2;
		margin: 0;
		color: var(--color-muted);
		font-size: 0.78rem;
		line-height: 1.35;
	}

	.separator {
		margin: 0 0.35rem;
	}

	/* A part wider than the column puts its numbers on the next line instead of sticking out. */
	.part {
		display: inline-block;
		max-width: 100%;
	}

	.numbers {
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}
</style>
