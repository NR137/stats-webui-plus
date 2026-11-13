// The web UI's texts in English: the reference for every language, which must have exactly these keys
// (type check and messages.test.ts). Nested by area; i18n.svelte.ts flattens them to 'area.key'.
export const en = {
	// The whole site.
	site: {
		// Any page or part of one whose data could not be loaded.
		loadError: 'That couldn\'t be loaded. Try again in a moment.',
		loading: 'Loading…',
		// The slogan in quotes (config siteHeadingSloganStyle "quote"), with this language's quotation marks.
		sloganQuote: '“{text}”',
	},

	// The menu and the links back.
	menu: {
		ranking: 'Ranking',
		players: 'Players',
		leaderboards: 'Leaderboards',
		advancements: 'Advancements',
		compare: 'Compare',
		stats: 'Statistics',
		about: 'How we rank',
		licenses: 'Licenses',
		back: 'Back',
		open: 'Menu',
		// The hotbar's last slot, with the pages that don't fit in one row.
		more: 'More',
		links: 'Links',
		top: 'Back to top',
	},

	// The language and theme switches.
	settings: {
		language: {
			label: 'Language',
			es: 'Español',
			en: 'English',
			fr: 'Français',
			de: 'Deutsch',
		},
		theme: {
			label: 'Theme',
			system: 'System',
			birch: 'Birch',
			deepslate: 'Deepslate',
		},
	},

	// The server's status in the header.
	server: {
		online: 'Online',
		offline: 'Offline',
		players: '{count} / {max} players online',
		copy: 'Click to copy the address',
		copied: 'Copied!',
	},

	// The footer.
	footer: {
		credit: '{plus} by NR137, built with AI · Inspired by {original} by borrageiros',
		disclaimer: 'Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.',
		assets: 'Minecraft names, textures and texts © Mojang · Font: {font} by macimas (OFL)',
	},

	// The start page: the ranking and its podium.
	ranking: {
		heading: 'Ranking',
		lead: 'The best on the server.',
		nobody: 'Nobody yet',
		rankingEmpty: 'No players with statistics yet.',
		search: 'Search players',
		podium: 'Podium',
	},

	// The player list.
	players: {
		heading: 'Players',
		lead: 'Everyone who has played on the server, by play time.',
		empty: 'No players with statistics yet.',
		count: '{count} players',
		countOne: '1 player',
		columns: {
			player: 'Player',
			hours: 'Hours',
			rate: 'Actions/h',
			score: 'Level',
			advancements: 'Advancements',
		},
	},

	// A player's page.
	player: {
		tiersLink: 'What are tiers?',
		noneAchievedInTab: 'Nothing achieved in this tab yet.',
		notFound: 'There\'s no player with this name on this server.',
		rankOf: 'Rank {rank} of {total}',
		playTime: 'Play time',
		actionsPerHour: 'Actions per hour',
		firstJoin: 'First joined',
		lastSeen: 'Last online',
		onlineNow: 'Online now',
		unknown: 'unknown',
		firstJoinEstimate: 'Estimated from the first advancement or recipe unlocked.',
		lastSeenEstimate: 'Estimated from the last time the server saved this player.',
		tiers: {
			stone: 'Stone',
			copper: 'Copper',
			iron: 'Iron',
			gold: 'Gold',
			diamond: 'Diamond',
			netherite: 'Netherite',
			dragon_egg: 'Dragon Egg',
		},
		tier: '{tier} tier',
		nextTier: '{count} more to {tier}',
		allAdvancements: 'All advancements achieved',
		skills: 'Skills',
		search: 'Search statistics',
		hideZeros: 'Hide zeros',
		noMatches: 'Nothing matches this search.',
		shown: '{shown} of {total}',
		stats: 'Statistics',
		sameName: 'Another player has the same name:',
		advancements: 'Advancements',
		achievedOnly: 'Achieved only',
	},

	// The seven skills the level is made of, and the parts each counts.
	skills: {
		combat: {
			name: 'Combat',
		},
		milestones: {
			name: 'Milestones',
		},
		mining: {
			name: 'Mining',
		},
		exploration: {
			name: 'Exploration',
		},
		building: {
			name: 'Building',
		},
		farming: {
			name: 'Farming',
		},
		survival: {
			name: 'Survival',
		},
		parts: {
			milestones: 'Milestones',
			repeats: 'Repeats',
			mob_types: 'Mob types',
			kills: 'Kills',
			biomes: 'Biomes',
			distance: 'Distance',
			ore_types: 'Ore types',
			ores: 'Ore points',
			block_types: 'Block types',
			crafted_types: 'Crafted items',
			placed: 'Blocks placed',
			food_types: 'Foods',
			animal_types: 'Animals bred',
			farming: 'Work',
			hours_per_death: 'Hours per death',
			kills_per_death: 'Kills per death',
		},
	},

	// The leaderboards page, a board, and every board by its ID: name, hint (one line), about (how it counts).
	boards: {
		page: {
			heading: 'Leaderboards',
			lead: 'Pick a leaderboard.',
			allStats: 'All statistics →',
		},
		board: {
			fallbackTitle: 'Leaderboard',
			notFound: 'This leaderboard doesn\'t exist.',
			empty: 'Nobody has this statistic yet. Be the first.',
		},
		categories: {
			rankings: 'Rankings',
			combat: 'Combat',
			mining: 'Mining',
			exploration: 'Exploration',
			life: 'Server life',
			deaths: 'Deaths',
			advancements: 'Advancements',
			totals: 'Totals',
			skills: 'Skills',
		},
		legends: {
			name: 'Legends',
			hint: 'Level 100 is a very good player in every skill.',
			about:
				'The level is made up of the seven skills: Milestones, Combat, Exploration, Mining, Building, Farming and Survival. 100 means very good at all of them; above that it keeps rising slowly. "How we rank" explains every part.',
		},
		veterans: {
			name: 'Veterans',
			hint: 'Real hours on the server.',
		},
		grinders: {
			name: 'Grinders',
			hint: 'Blocks mined and placed, distance and kills per hour played.',
			about:
				'What a player gets done per hour online: blocks mined and placed, distance traveled (a kilometer counts 200, Minecarts not at all) and hostile mobs killed (10 each, capped as in Combat), divided by the hours played. Players count once they have 2 hours of play time. AFK time adds hours but nothing else, and lowers the value.',
		},
		advancements: {
			name: 'Advancements',
			hint: 'Vanilla advancements achieved. Recipes don\'t count.',
		},
		'hostile-kills': {
			name: 'Hostile kills',
			hint: 'Hostile mobs killed.',
			about: 'Every hostile mob, Silverfish included.',
		},
		'hostile-no-silverfish': {
			name: 'Hostiles without Silverfish',
			hint: 'Hostile mobs killed, not counting Silverfish.',
			about: 'Every hostile mob except Silverfish, which farms kill by the thousands.',
		},
		'other-mobs': {
			name: 'Other mobs',
			hint: 'Mobs killed that aren\'t hostile.',
			about:
				'Every mob that isn\'t hostile: animals, fish, Squid, Bats, Villagers, golems, … Players have their own leaderboard.',
		},
		bosses: {
			name: 'Bosses',
			hint: 'Ender Dragon, Wither, Warden and Elder Guardian.',
		},
		'player-kills': {
			name: 'Players killed',
			hint: 'Players killed.',
		},
		deaths: {
			name: 'Total deaths',
			hint: 'How many times you\'ve died. Most first; when comparing, fewer wins.',
		},
		'kills-per-hour': {
			name: 'Kills per hour',
			hint: 'Mobs killed per hour played.',
			about: 'All mobs killed, farms included, divided by the hours played.',
		},
		'blocks-mined': {
			name: 'Blocks mined',
			hint: 'Total blocks mined.',
		},
		ores: {
			name: 'Ores',
			hint: 'Ores mined.',
		},
		'diamonds-mined': {
			name: 'Diamonds mined',
			hint: 'Diamond Ore and Deepslate Diamond Ore mined.',
		},
		'iron-mined': {
			name: 'Iron mined',
			hint: 'Iron Ore and Deepslate Iron Ore mined.',
		},
		'coal-mined': {
			name: 'Coal mined',
			hint: 'Coal Ore and Deepslate Coal Ore mined.',
		},
		'copper-mined': {
			name: 'Copper mined',
			hint: 'Copper Ore and Deepslate Copper Ore mined.',
		},
		'gold-mined': {
			name: 'Gold mined',
			hint: 'Gold Ore, Deepslate Gold Ore and Nether Gold Ore mined.',
		},
		'redstone-mined': {
			name: 'Redstone mined',
			hint: 'Redstone Ore and Deepslate Redstone Ore mined.',
		},
		'lapis-mined': {
			name: 'Lapis Lazuli mined',
			hint: 'Lapis Lazuli Ore and Deepslate Lapis Lazuli Ore mined.',
		},
		'emerald-mined': {
			name: 'Emeralds mined',
			hint: 'Emerald Ore and Deepslate Emerald Ore mined.',
		},
		'quartz-mined': {
			name: 'Nether Quartz mined',
			hint: 'Nether Quartz Ore mined.',
		},
		'deaths-per-hour': {
			name: 'Deaths per hour',
			hint: 'Deaths per hour played. Most first; when comparing, fewer wins.',
			about:
				'Deaths divided by the hours played. The most are at the top; when two players are compared, the one with fewer deaths per hour wins.',
		},
		'ancient-debris': {
			name: 'Ancient Debris',
			hint: 'Ancient Debris mined.',
		},
		'logs-mined': {
			name: 'Logs',
			hint: 'Logs mined.',
		},
		'tools-broken': {
			name: 'Items broken',
			hint: 'Total items worn out: tools, weapons, armor, … whose durability ran out.',
			about:
				'Items whose durability ran out in your hand or on your body: tools, weapons, armor, Elytra, Shields, Fishing Rods, …',
		},
		'distance-total': {
			name: 'Distance traveled',
			hint: 'Distance traveled in km, not counting flying and falling.',
			about:
				'Every way of getting around counts: walking, sprinting, swimming, Boats, Elytra, Horses, Happy Ghasts, Nautiluses, Minecarts, Striders, climbing, crouching, and walking on or under water. Flying in Creative mode and falling don\'t.',
		},
		'items-dropped': {
			name: 'Dropped',
			hint: 'Total items dropped.',
		},
		'items-picked-up': {
			name: 'Picked up',
			hint: 'Total items picked up.',
		},
		'items-used': {
			name: 'Items used',
			hint: 'Total items used: placed, eaten, swung, thrown, …',
			about:
				'Everything the game counts as using an item: placing blocks, eating and drinking, tools on blocks, weapons on mobs, throwing, Buckets, … and putting items into a Decorated Pot.',
		},
		'items-crafted': {
			name: 'Items crafted',
			hint: 'Total items crafted: Crafting Table, Furnace, Smithing Table, Stonecutter and trading.',
			about:
				'Everything the game counts as crafting: the inventory crafting grid and Crafting Table, Furnaces, Smithing Table, Stonecutter, Cartography Table, and trading with Villagers. Every item counts, so 4 Planks crafted at once count as 4.',
		},
		'blocks-placed': {
			name: 'Blocks placed',
			hint: 'Blocks placed (the game counts placing as using).',
			about:
				'The game doesn\'t count placed blocks on their own; it counts placing as using the item. This leaderboard adds up how often all block items were used, which is almost only placing (planting seeds counts too: they\'re placed as a plant).',
		},
		'mob-types': {
			name: 'Mob types',
			hint: 'How many different mob types were killed.',
			about:
				'Each mob type counts once, as soon as the player has killed one: hostile, neutral and passive alike.',
		},
		'kills-per-death': {
			name: 'Kills per death',
			hint:
				'Hostile mobs killed (not counting Silverfish) per death; with no deaths yet, the kills themselves.',
			about:
				'Hostile mobs killed (not counting Silverfish) divided by deaths. Anyone who has never died gets their kills as the value, as if they had died once.',
		},
		'distance-per-hour': {
			name: 'Distance per hour',
			hint: 'Distance traveled per hour played.',
			about: 'Distance traveled divided by the hours played: who gets around the most while online.',
		},
		'food-eaten': {
			name: 'Food eaten',
			hint: 'Food eaten, Cake slices included.',
			about: 'Everything eaten: every food item and the slices of a placed Cake.',
		},
		'current-life': {
			name: 'Current life',
			hint: 'Play time since the last death (the game only counts it while you\'re online).',
			about:
				'How long the player has been alive since their last death, in play time: the game only counts it while they\'re online, and starts again at zero with every death. Anyone who has never died counts from when they first joined.',
		},
		'distance-walk-sprint': {
			name: 'Walking and sprinting',
			hint: 'Distance walked and sprinted.',
		},
		'distance-elytra': {
			name: 'Elytra',
			hint: 'Distance flown with Elytra.',
		},
		'play-time': {
			name: 'Play time',
			hint: 'Real time on the server.',
		},
		'skill-milestones': {
			name: 'Milestones',
			hint: 'Bosses, raids, rare finds and items; each counts fully once.',
			about:
				'The big moments of a world: defeating the Ender Dragon, the Wither, the Warden and an Elder Guardian; winning a raid; finding Elytra, a Trident, the Dragon Egg, a Heart of the Sea, a Wither Skeleton Skull and a Sniffer Egg; crafting a Beacon, Netherite, a Conduit and a Mace; being saved by a Totem of Undying; eating an Enchanted Golden Apple. Each counts fully the first time (three quarters of the skill; 100 of the 118 points fill it). Defeating a boss again or crafting another Beacon, Netherite Ingot, Conduit or Mace adds a bit more (a quarter), less with every repeat. Raids, Totems and finds count only once, so a raid farm or picking up the same Elytra again adds nothing.',
		},
		'skill-combat': {
			name: 'Combat',
			hint: 'Hostile mob types and kills, capped per type.',
			about:
				'Hostile mob types killed, out of 30 (half the skill), and kills of hostile mobs (the other half, 100 at 4,000). Each type counts fully up to 250 kills, after that only the square root of the rest, so a farm adds little: 120,000 Zombified Piglins count as about 600 kills. Animals, fish and Villagers don\'t count.',
		},
		'skill-exploration': {
			name: 'Exploration',
			hint: 'Biomes visited and distance traveled.',
			about:
				'Biomes visited, out of 50 (half the skill): the Overworld biomes of the advancement "Adventuring Time" and the Nether biomes of "Hot Tourist Destinations". Plus distance traveled (the other half, 100 at 5,000 km): walking, sprinting, swimming, Boats, Elytra, Horses and other mounts, climbing. Minecarts count one fifth, because a Minecart loop keeps running while you\'re away. Creative flight and falling don\'t count.',
		},
		'skill-mining': {
			name: 'Mining',
			hint: 'Ore types and ores, the rarer the more they\'re worth.',
			about:
				'Ore types mined, out of 10 (30% of the skill), and the ores themselves (70%, 100 at 12,000 points), the rarer the more they\'re worth: Ancient Debris 15, Diamond 8, Emerald 6, Gold 3, Iron 2, Lapis Lazuli 1.5, Nether Quartz 1.2, Copper 1, Redstone 0.8, Coal 0.25. Each ore counts fully up to 1,500 blocks, after that only the square root of the rest. Stone, Dirt and Deepslate don\'t count.',
		},
		'skill-building': {
			name: 'Building',
			hint: 'Block types placed, items crafted and blocks placed.',
			about:
				'Block types placed, out of 400 (35% of the skill), item types crafted, out of 250 (15%), and blocks placed (half, 100 at 300,000). The game counts placing a block as using its item, and those uses are added up. Many different blocks count for more than lots of one.',
		},
		'skill-farming': {
			name: 'Farming',
			hint: 'Food, breeding, trading, fish and harvest.',
			about:
				'Foods eaten, out of 25 kinds (a quarter of the skill), animal types bred, out of 15 (a quarter, from the advancement "Two by Two"), and the work (half, 100 at 5,000): every animal bred, trades with Villagers (fully up to 2,000), fish caught (fully up to 1,000) and crops harvested (20 count as 1).',
		},
		'skill-survival': {
			name: 'Survival',
			hint: 'Hours played and kills per death.',
			about:
				'Staying alive: hours played per death (half the skill, 100 at 8 hours) and hostile mobs killed per death (half, 100 at 100). Both divide by deaths plus one, so anyone who has never died counts as if they had died once. This skill stops at 100.',
		},
	},

	// Units of the boards: name and hint.
	units: {
		hours: {
			name: 'hours',
			hint: 'Real time on the server.',
		},
		kills: {
			name: 'kills',
			hint: 'Mobs or players killed.',
		},
		count: {
			name: 'times',
			hint: 'How many times it happened.',
		},
		hearts: {
			name: 'hearts',
			hint: 'Damage in hearts.',
		},
		kills_per_hour: {
			name: 'kills/h',
			hint: 'Mobs killed per hour played.',
		},
		deaths_per_hour: {
			name: 'deaths/h',
			hint: 'Deaths per hour played.',
		},
		blocks: {
			name: 'blocks',
			hint: 'Number of blocks.',
		},
		km: {
			name: 'km',
			hint: 'Distance traveled in kilometers.',
		},
		km_per_hour: {
			name: 'km/h',
			hint: 'Kilometers per hour played.',
		},
		mob_types: {
			name: 'mob types',
			hint: 'Different mob types.',
		},
		ratio: {
			name: 'kills/death',
			hint: 'Mobs killed per death.',
		},
		level: {
			name: 'level',
			hint: 'The ranking level: 100 is a very good player in every skill.',
		},
		skill: {
			name: 'points',
			hint: '100 is a very good player in this skill; above that, the value keeps rising slowly.',
		},
		actions_per_hour: {
			name: 'actions/h',
			hint: 'Blocks mined and placed, distance and kills per hour played.',
		},
	},

	// The statistics page and the game's statistic groups.
	stats: {
		groupTabs: 'Statistic groups',
		groups: {
			custom: {
				short: 'General',
				name: 'General statistics',
				hint: '{name}.',
			},
			killed: {
				short: 'Killed',
				name: 'Mobs killed',
				hint: 'Killed: {name}.',
			},
			killed_by: {
				short: 'Deaths',
				name: 'Deaths by mobs',
				hint: 'Killed by: {name}.',
			},
			mined: {
				short: 'Mined',
				name: 'Blocks mined',
				hint: 'Mined: {name}.',
			},
			crafted: {
				short: 'Crafted',
				name: 'Crafted',
				hint: 'Crafted: {name}.',
			},
			used: {
				short: 'Used',
				name: 'Used',
				hint: 'Used: {name}.',
			},
			broken: {
				short: 'Broken',
				name: 'Broken',
				hint: 'Broken: {name}.',
			},
			picked_up: {
				short: 'Picked up',
				name: 'Picked up',
				hint: 'Picked up: {name}.',
			},
			dropped: {
				short: 'Dropped',
				name: 'Dropped',
				hint: 'Dropped: {name}.',
			},
		},
		lead: 'Everything Minecraft counts. Pick one to see its leaderboard.',
		heading: 'Statistics',
	},

	// The advancements pages, a player's advancement, criteria and variants.
	advancements: {
		heading: 'Advancements',
		lead: 'Who has achieved what.',
		most: 'Most advancements',
		mostLead: 'How many vanilla advancements each player has. Recipes don\'t count.',
		search: 'Search advancements',
		empty: 'Nobody has achieved this advancement yet.',
		variants: {
			cat: {
				tabby: 'Tabby',
				black: 'Tuxedo',
				red: 'Red',
				siamese: 'Siamese',
				british_shorthair: 'British Shorthair',
				calico: 'Calico',
				persian: 'Persian',
				ragdoll: 'Ragdoll',
				white: 'White',
				jellie: 'Jellie',
				all_black: 'All Black',
			},
			wolf: {
				pale: 'Pale',
				ashen: 'Ashen',
				black: 'Black',
				chestnut: 'Chestnut',
				rusty: 'Rusty',
				snowy: 'Snowy',
				spotted: 'Spotted',
				striped: 'Striped',
				woods: 'Woods',
			},
			frog: {
				temperate: 'Temperate',
				cold: 'Cold',
				warm: 'Warm',
			},
			site: {
				desert_pyramid: 'Brushed in a Desert Pyramid',
				desert_well: 'Brushed at a Desert Well',
				ocean_ruin_cold: 'Brushed in Cold Ocean Ruins',
				ocean_ruin_warm: 'Brushed in Warm Ocean Ruins',
				trail_ruins_common: 'Brushed in Trail Ruins (common loot)',
				trail_ruins_rare: 'Brushed in Trail Ruins (rare loot)',
				has_sherd: 'Pottery Sherd in inventory',
			},
		},
		player: {
			doneOn: 'Achieved: {when}',
			notYet: 'Not achieved yet',
			progress: '{done} of {total} done',
			partsDone: 'Done',
			partsMissing: 'Still missing',
			anyOf: 'Any one of these is enough:',
			allPlayers: 'All players with this advancement',
		},
		criteria: {
			heart_transplanter: {
				place_creaking_heart_awake: 'Creaking Heart placed (active)',
				place_creaking_heart_dormant: 'Creaking Heart placed (dormant)',
				place_pale_oak_log: 'Pale Oak Log placed to complete it',
			},
			distract_piglin: {
				distract_piglin: 'Gold item thrown to a Piglin',
				distract_piglin_directly: 'Gold Ingot handed to a Piglin',
			},
			loot_bastion: {
				loot_bastion_bridge: 'Chest in a Bridge',
				loot_bastion_hoglin_stable: 'Chest in a Hoglin Stable',
				loot_bastion_treasure: 'Chest in a Treasure Room',
				loot_bastion_other: 'Generic bastion chest',
			},
			root: {
				killed_something: 'Killed something',
				killed_by_something: 'Killed by something',
			},
			read_power_of_chiseled_bookshelf: {
				chiseled_bookshelf: 'Chiseled Bookshelf placed next to a Redstone Comparator',
				comparator: 'Redstone Comparator placed next to a Chiseled Bookshelf',
			},
			uh_oh: {
				give_tnt_directly: 'TNT handed to a Sulfur Cube',
				pick_up_dropped_tnt: 'Sulfur Cube picked up dropped TNT',
			},
		},
		reward: 'Reward: {xp} experience points',
		view: 'View',
		viewTiles: 'Tiles',
		viewTree: 'Tree',
		tabs: 'Advancement tabs',
		notFound: 'This advancement doesn\'t exist.',
		nobody: 'Nobody has any advancements yet.',
		holder: '1 player',
		holders: '{count} players',
		hidden: 'Hidden',
		hideHidden: 'Hide hidden advancements',
		hideHiddenHint:
			'Hides the advancements the game keeps secret, so they don\'t spoil the surprise.',
		doneOf: '{done} of {total}',
	},

	// The compare page.
	compare: {
		heading: 'Compare players',
		lead: 'Pick two players and see who\'s ahead in every skill and statistic.',
		verdict: '{name} wins {wins} of {total} skills.',
		verdictLevel: '{wins} skills each; {name} is ahead on level.',
		verdictEven: 'Dead even: the same skills and the same level.',
		skillParts: 'What it\'s made of',
		partTarget: '100 at {target}',
		vanilla: 'Statistics',
		vanillaEmpty: 'Neither of them has this yet.',
		playerA: 'Player A',
		playerB: 'Player B',
		choose: 'Choose',
		waiting: 'Waiting for statistics',
		unselected: 'Not selected',
		slotA: 'A',
		slotB: 'B',
		needTwo: 'Pick two players to compare them.',
		same: 'Pick two different players.',
		advancementsDiff: 'Unique advancements',
		onlyAdvancements: 'Only {name}',
		onlyAdvancementsEmpty: 'No unique advancements',
		otherAdvancements: 'Other advancements',
	},

	// The 3D skin viewer.
	skin: {
		head: 'Head',
		body: 'Body',
		actions: 'Skin controls',
		poses: {
			idle: 'Stand',
			walk: 'Walk',
			run: 'Run',
			fly: 'Fly',
		},
		pause: 'Pause',
		resume: 'Resume',
		autoRotate: 'Auto-rotate',
		scrollZoom: 'Zoom with the mouse wheel',
		equipment: {
			cape: 'Cape',
			elytra: 'Elytra',
			none: 'No cape',
		},
	},

	// How we rank.
	about: {
		howLink: 'How is this calculated?',
		heading: 'How we rank',
		lead:
			'Minecraft doesn\'t rate how well you play; it only counts what you do. A single statistic would reward the wrong thing: play time rewards standing AFK, raw kills reward a mob farm. That\'s why the ranking looks at seven skills and turns them into one level.',
		farmsHeading: 'Farms and AFK',
		farms:
			'We don\'t hide farms: kills from Silverfish and gold farms show up on the Hostile kills leaderboard, fish under Fish Caught. They just don\'t decide the level. Standing AFK earns play time, which only counts for Survival, and lowers actions per hour.',
		rankingsHeading: 'Three rankings',
		rankingLegends: 'Legends: the highest level.',
		rankingVeterans: 'Veterans: the most hours on the server.',
		tiersHeading: 'Advancement tiers',
		tiers:
			'Every player page shows, as a bar with a tier, what share of the vanilla advancements that player has achieved. The tiers are named after Minecraft materials and are just for fun: they don\'t count toward the level (Milestones counts some of the same feats from the statistics).',
		unitsHeading: 'Units that make sense',
		units: 'Distances in km, time in hours, damage in hearts. Never centimeters or ticks.',
		levelHeading: 'The level',
		level:
			'Every skill gets a score: 100 is a very good player in that skill, someone who has seen and done most of it. The level mixes the skills with fixed weights, so level 100 means very good at everything. Like the experience level in the game, it\'s a whole number, rounded down.',
		levelOpen:
			'There\'s no upper limit. Above 100 a skill keeps rising, but slowly: twice as much as a very good player adds only a little, so being extremely good at one thing never outweighs being good at everything. The bars on the player pages go from 0 to 100 and stay full above that.',
		levelOwn:
			'Your level depends only on what you\'ve done, never on the other players: nobody loses points because someone else played more. Outside of Survival it never goes down, because the game\'s statistics only grow. On a tie, whoever needed less play time comes first; after that, players are sorted alphabetically by name.',
		partsHeading: 'Breadth and depth',
		parts:
			'Every skill is made of two kinds of parts. Breadth counts how many different things you\'ve done: mob types, biomes, ores, block types. It\'s full at a fixed number and never goes beyond it, and no farm can fake it. Depth counts how much you\'ve done, on a curve: the first hundred count a lot, the next hundred a bit less. It reaches 100 at what an experienced player has after a few hundred hours.',
		caps:
			'Anything a farm can do endlessly has a soft cap: kills count fully up to 250 per mob type, ores up to 1,500 per kind, trades up to 2,000 and fish up to 1,000. Beyond that only the square root counts, so a gold farm\'s 120,000 Zombified Piglins count as about 600 kills. A farm still adds a little; it just can\'t win a skill on its own.',
		skillsHeading: 'The seven skills',
		skillWeight: '{weight} of the level',
		dyingHeading: 'Dying',
		dying:
			'A death only lowers Survival, one tenth of the level. The longer you\'ve played, the less a death counts: an experienced player loses far less than a tenth of a level, which usually doesn\'t show at all. Only a new player\'s first deaths cost about one and a half levels, and they get those back once they\'ve played about as long again without dying as they had before that death.',
		rankingGrinders:
			'Grinders: what a player gets done per hour online. Blocks mined and placed, distance (a kilometer counts 200, Minecarts not at all) and hostile mobs killed (10 each, capped as above), divided by the hours played, from 2 hours of play time. AFK time adds hours but nothing else.',
	},

	// The licenses page.
	licenses: {
		heading: 'Licenses',
		lead: 'Who made the things this website is built from, and under which licenses they\'re used.',
		minecraftHeading: 'Minecraft',
		minecraft:
			'Minecraft and the game\'s names, textures and texts belong to Mojang and Microsoft. This website shows them under the {guidelines}.',
		guidelines: 'Minecraft Usage Guidelines',
		guidelinesUrl: 'https://www.minecraft.net/en-us/usage-guidelines',
		download:
			'The mod contains no files from the game. Each server downloads Minecraft\'s textures and texts from Mojang, as the launcher does, and draws the icons from them.',
		skins: 'Skins belong to their players. They\'re loaded from Mojang, and from GeyserMC for Bedrock players.',
		projectHeading: 'Stats WebUI Plus',
		project:
			'{plus} is free software under the MIT License.',
		showText: 'Show license text',
		fontHeading: 'Fonts',
		font:
			'The texts use {font} by macimas: a free recreation of the Minecraft font, not a file from the game, under the SIL Open Font License 1.1.',
		// The fonts the site heading and page titles use: only those this server uses are named.
		fontBoth:
			'The site heading and the page titles use {font} by {author}, also under the SIL Open Font License 1.1; its license text is in the list below.',
		fontSite:
			'The site heading uses {font} by {author}, also under the SIL Open Font License 1.1; its license text is in the list below.',
		fontTitles:
			'The page titles use {font} by {author}, also under the SIL Open Font License 1.1; its license text is in the list below.',
		// The server owner's own font for the site heading; their text about it follows.
		fontCustom: 'The site heading uses {name}, a font chosen by this server:',
		packagesHeading: 'Open-source software in this website',
		packages: 'The website contains code from these packages. The same notices as a single file: {file}.',
		loading: 'Loading…',
		error: 'The license list couldn\'t be loaded.',
	},

	// The server owner's own pages.
	pages: {
		notFoundTitle: 'Page not found',
		notFound: 'This page doesn\'t exist.',
	},
} as const;

type Shape<T> = { [K in keyof T]: T[K] extends string ? string : Shape<T[K]> };

/** A language: the same keys as English, any texts. */
export type Messages = Shape<typeof en>;

/** Every text's key, 'area.key' as t() takes it. */
export type MessageKey = Paths<typeof en>;

type Paths<T> = {
	[K in keyof T & string]: T[K] extends string ? K : `${K}.${Paths<T[K]>}`;
}[keyof T & string];
