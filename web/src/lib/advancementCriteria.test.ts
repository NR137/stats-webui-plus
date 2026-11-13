import { afterEach, describe, expect, it } from 'vitest';
import { criterionPart, prettyCriterion } from './advancementCriteria';
import { i18n } from './i18n/i18n.svelte';
import { useIcons } from './mc/items.svelte';

// The entries of the server's icon table (/mc/icons.json) these tests need: a part without one falls back.
useIcons({
	'variant:wolf:minecraft:snowy': '/mc/face/wolf_snowy.png',
	'variant:cat:minecraft:black': '/mc/face/cat_black.png',
	'structure:desert-pyramid': '/mc/scene/desert_pyramid.png'
});

// a player's page for an advancement names its parts in the visitor's language: mobs, foods and
// trims by their official names (with icons), cat, wolf and frog variants and sherd sites by the names minecraft.wiki
// uses (the game has none), and anything else (data packs) by a readable form of its ID.

afterEach(() => {
	i18n.locale = 'en';
});

describe('criterionPart', () => {
	it('names variants the game has no names for, in the visitor language', () => {
		expect(criterionPart('minecraft:husbandry/complete_catalogue', 'minecraft:all_black').label).toBe('All Black');
		expect(criterionPart('minecraft:husbandry/complete_catalogue', 'minecraft:black').label).toBe('Tuxedo');
		i18n.locale = 'de';
		expect(criterionPart('minecraft:husbandry/whole_pack', 'minecraft:ashen').label).toBe('Aschgrau');
		expect(criterionPart('minecraft:husbandry/leash_all_frog_variants', 'minecraft:temperate').label).toBe('Gemäßigt');
		i18n.locale = 'fr';
		expect(criterionPart('minecraft:adventure/salvage_sherd', 'desert_well').label).toBe('Brossé dans un puits du désert');
	});

	it('shows each variant with its own sprite, an unknown one with its mob', () => {
		expect(criterionPart('minecraft:husbandry/whole_pack', 'minecraft:snowy').icon).toBe('variant:wolf:minecraft:snowy');
		// The tuxedo cat's ID is "black"; it has a sprite of its own too.
		expect(criterionPart('minecraft:husbandry/complete_catalogue', 'minecraft:black').icon).toBe(
			'variant:cat:minecraft:black'
		);
		expect(criterionPart('minecraft:husbandry/whole_pack', 'mypack:golden').icon).toBe('entity:minecraft:wolf');
	});

	it('shows mobs with their face and items with their icon', () => {
		expect(criterionPart('minecraft:adventure/kill_all_mobs', 'minecraft:zombie').icon).toBe('entity:minecraft:zombie');
		expect(criterionPart('minecraft:husbandry/balanced_diet', 'apple').icon).toBe('minecraft:apple');
	});

	it('names a trim by its pattern, as every template is just "Smithing Template"', () => {
		const part = criterionPart(
			'minecraft:adventure/trim_with_all_exclusive_armor_patterns',
			'armor_trimmed_minecraft:silence_armor_trim_smithing_template_smithing_trim'
		);
		expect(part.icon).toBe('minecraft:silence_armor_trim_smithing_template');
		// The official pattern names come with the name files; without them, the pattern from the ID.
		expect(part.label).toBe('Silence');
	});

	it('names criteria that are Mojang\'s own words by hand, with an icon', () => {
		const generic = criterionPart('minecraft:nether/loot_bastion', 'loot_bastion_other');
		expect(generic.label).toBe('Generic bastion chest');
		expect(generic.icon).toBe('minecraft:chest');
		i18n.locale = 'de';
		expect(criterionPart('minecraft:adventure/heart_transplanter', 'place_pale_oak_log').label).toBe(
			'Blasseichenstamm dazugesetzt'
		);
	});

	it('shows sherd sites with their structure', () => {
		expect(criterionPart('minecraft:adventure/salvage_sherd', 'desert_pyramid').icon).toBe('structure:desert-pyramid');
	});

	it('falls back to a readable ID, never a raw one', () => {
		// Without the name files loaded (as here), and for unknown variants or data pack criteria.
		expect(criterionPart('minecraft:adventure/adventuring_time', 'minecraft:cherry_grove').label).toBe(
			'Cherry grove'
		);
		expect(criterionPart('minecraft:husbandry/whole_pack', 'mypack:golden').label).toBe('Mypack:golden');
		expect(criterionPart('mypack:quests/collect', 'killed_by_something').label).toBe('Killed by something');
	});
});

describe('prettyCriterion', () => {
	it('drops the minecraft namespace and underscores', () => {
		expect(prettyCriterion('minecraft:deep_dark')).toBe('Deep dark');
		expect(prettyCriterion('has_sherd')).toBe('Has sherd');
	});
});
