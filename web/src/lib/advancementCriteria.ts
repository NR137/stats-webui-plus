import { t } from '$lib/i18n/i18n.svelte';
import type { MessageKey } from '$lib/i18n/messages/es';
import { officialName, type NameKind } from '$lib/mc/names.svelte';
import { hasIcon } from '$lib/mc/items';

// One part of an advancement as a player's advancement page shows it: a name in the visitor's language and, where
// there is one, an icon (an item or "entity:<id>" for McItem).
export type CriterionPart = { label: string; icon?: string };

// What the criteria of an advancement name. Minecraft's criteria are plain IDs (minecraft:plains, apple, ...); which
// registry they belong to only shows from the advancement.
type Kind = 'biome' | 'entity' | 'item' | 'trim' | 'cat' | 'wolf' | 'frog' | 'site';

const KINDS: Record<string, Kind> = {
	'minecraft:adventure/adventuring_time': 'biome',
	'minecraft:nether/explore_nether': 'biome',
	'minecraft:adventure/kill_a_mob': 'entity',
	'minecraft:adventure/kill_all_mobs': 'entity',
	'minecraft:husbandry/bred_all_animals': 'entity',
	'minecraft:husbandry/balanced_diet': 'item',
	'minecraft:husbandry/fishy_business': 'item',
	'minecraft:husbandry/tactical_fishing': 'item',
	'minecraft:husbandry/plant_seed': 'item',
	'minecraft:husbandry/plant_any_sniffer_seed': 'item',
	'minecraft:story/obtain_armor': 'item',
	'minecraft:story/shiny_gear': 'item',
	'minecraft:adventure/trim_with_any_armor_pattern': 'trim',
	'minecraft:adventure/trim_with_all_exclusive_armor_patterns': 'trim',
	'minecraft:husbandry/complete_catalogue': 'cat',
	'minecraft:husbandry/whole_pack': 'wolf',
	'minecraft:husbandry/leash_all_frog_variants': 'frog',
	'minecraft:adventure/salvage_sherd': 'site'
};

// Criteria that are no IDs but Mojang's own words ("loot_bastion_other", "killed_by_something"): named by hand in
// the message files (criterion.<advancement>.<criterion>; the wiki has no names for them either), with an icon.
// Comments say why an icon was chosen where it isn't simply the thing named.
const SPECIAL: Record<string, Record<string, string>> = {
	'minecraft:adventure/heart_transplanter': {
		place_creaking_heart_awake: 'minecraft:creaking_heart',
		place_creaking_heart_dormant: 'minecraft:creaking_heart',
		// The wiki's sprite of a creaking heart between pale oak logs: the log placed last completes that.
		place_pale_oak_log: 'structure:creaking-heart-pale-oak'
	},
	'minecraft:nether/distract_piglin': {
		// Any of 28 gold things piglins love can be thrown (#piglin_loved); the gold block stands for them all.
		distract_piglin: 'minecraft:gold_block',
		distract_piglin_directly: 'minecraft:gold_ingot'
	},
	'minecraft:nether/loot_bastion': {
		// A chest of each bastion part's own loot table; the game shows no difference, so all are plain chests.
		loot_bastion_bridge: 'minecraft:chest',
		loot_bastion_hoglin_stable: 'minecraft:chest',
		loot_bastion_treasure: 'minecraft:chest',
		loot_bastion_other: 'minecraft:chest'
	},
	'minecraft:adventure/root': {
		// Killed anything: a weapon.
		killed_something: 'minecraft:iron_sword',
		// Was killed by anything: Steve's face with crossed-out eyes, made by the texture script from the game's skin
		// (a mob's skull would look like the mob that was killed, a plain head like a player killed).
		killed_by_something: 'special:player-killed'
	},
	'minecraft:adventure/read_power_of_chiseled_bookshelf': {
		chiseled_bookshelf: 'minecraft:chiseled_bookshelf',
		comparator: 'minecraft:comparator'
	},
	'minecraft:husbandry/uh_oh': {
		give_tnt_directly: 'minecraft:tnt',
		// The sulfur cube picked the TNT up itself: its face.
		pick_up_dropped_tnt: 'entity:minecraft:sulfur_cube'
	}
};

// Respecting the Remnants: brushing suspicious sand or gravel at one of these sites (each an archaeology loot table),
// shown with the wiki's sprite of the structure; trail ruins have two tables (common and rare loot), so both get the
// trail ruins sprite. Having a pottery sherd is a requirement of its own: one sherd (the archer's) stands for all.
const SITE_ICONS: Record<string, string> = {
	desert_pyramid: 'structure:desert-pyramid',
	desert_well: 'structure:desert-well',
	ocean_ruin_cold: 'structure:ocean-ruin',
	ocean_ruin_warm: 'structure:warm-ocean-ruins',
	trail_ruins_common: 'structure:trail-ruins',
	trail_ruins_rare: 'structure:trail-ruins',
	has_sherd: 'minecraft:archer_pottery_sherd'
};

// The mob whose variants these are: the icon of a variant without a sprite of its own (e.g. from a data pack).
const VARIANT_MOB: Partial<Record<Kind, string>> = {
	cat: 'entity:minecraft:cat',
	wolf: 'entity:minecraft:wolf',
	frog: 'entity:minecraft:frog'
};

function withNamespace(id: string): string {
	return id.includes(':') ? id : `minecraft:${id}`;
}

/** "killed_by_something" -> "Killed by something": for criteria nothing else names (data packs, special ones). */
export function prettyCriterion(criterion: string): string {
	const plain = criterion.replace(/^minecraft:/, '').replace(/[_/]+/g, ' ').trim();
	return plain.charAt(0).toUpperCase() + plain.slice(1);
}

function named(kinds: NameKind[], id: string): string | undefined {
	for (const kind of kinds) {
		const name = officialName(kind, id);
		if (name) {
			return name;
		}
	}
	return undefined;
}

/** Name and icon of one criterion of an advancement, in the current language. */
export function criterionPart(advancement: string, criterion: string): CriterionPart {
	const kind = KINDS[advancement];
	const id = withNamespace(criterion);
	const plain = criterion.replace(/^minecraft:/, '');
	const special = SPECIAL[advancement]?.[criterion];
	if (special) {
		const key = `criterion.${advancement.split('/').pop()}.${criterion}` as MessageKey;
		const label = t(key);
		return { label: label === key ? prettyCriterion(criterion) : label, icon: special };
	}
	switch (kind) {
		case 'biome': {
			const sprite = `biome:${id}`;
			return {
				label: officialName('biome', id) ?? prettyCriterion(criterion),
				icon: hasIcon(sprite) ? sprite : undefined
			};
		}
		case 'entity':
			return { label: officialName('entity', id) ?? prettyCriterion(criterion), icon: `entity:${id}` };
		case 'item':
			// Seeds are planted as their crop block (wheat, melon_stem, ...).
			return { label: named(['item', 'block'], id) ?? prettyCriterion(criterion), icon: id };
		case 'trim': {
			// "armor_trimmed_minecraft:silence_armor_trim_smithing_template_smithing_trim": the template used.
			const template = withNamespace(criterion.replace(/^armor_trimmed_/, '').replace(/_smithing_trim$/, ''));
			// Every template item is just "Smithing Template"; the pattern has the name ("Silence Armor Trim").
			const pattern = template.replace(/_armor_trim_smithing_template$/, '');
			const label = officialName('trim', pattern) ?? officialName('item', template);
			return { label: label ?? prettyCriterion(pattern), icon: template };
		}
		case 'cat':
		case 'wolf':
		case 'frog':
		case 'site': {
			// Variants and structures have no names in the game (it never shows them); ours follow minecraft.wiki.
			const key = `variant.${kind}.${plain}` as MessageKey;
			const label = t(key);
			// The variant's own sprite (texture script, from minecraft.wiki), else the mob's; a site's structure.
			const sprite = kind === 'site' ? (SITE_ICONS[plain] ?? '') : `variant:${kind}:${id}`;
			return {
				label: label === key ? prettyCriterion(criterion) : label,
				icon: hasIcon(sprite) ? sprite : VARIANT_MOB[kind]
			};
		}
		default: {
			// Unknown advancement (a data pack): an ID that names something gets that name.
			const label = criterion.includes(':') ? named(['item', 'block', 'entity', 'biome'], id) : undefined;
			return { label: label ?? prettyCriterion(criterion) };
		}
	}
}
