import type { Messages } from './en';

export const fr: Messages = {
	// The whole site.
	site: {
		// Any page or part of one whose data could not be loaded.
		loadError: 'Impossible de charger ce contenu. Réessayez dans un instant.',
		loading: 'Chargement…',
		// The slogan in quotes (config siteHeadingSloganStyle "quote"), with this language's quotation marks.
		sloganQuote: '« {text} »',
	},

	// The menu and the links back.
	menu: {
		ranking: 'Classement',
		players: 'Joueurs',
		leaderboards: 'Tableaux',
		advancements: 'Progrès',
		compare: 'Comparer',
		stats: 'Statistiques',
		about: 'Notre classement',
		licenses: 'Licences',
		back: 'Retour',
		open: 'Menu',
		// The hotbar's last slot, with the pages that don't fit in one row.
		more: 'Plus',
		links: 'Liens',
		top: 'Haut de page',
	},

	// The language and theme switches.
	settings: {
		language: {
			label: 'Langue',
			es: 'Español',
			en: 'English',
			fr: 'Français',
			de: 'Deutsch',
		},
		theme: {
			label: 'Thème',
			system: 'Système',
			birch: 'Bouleau',
			deepslate: 'Ardoise des abîmes',
		},
	},

	// The server's status in the header.
	server: {
		online: 'En ligne',
		offline: 'Hors ligne',
		players: '{count} / {max} joueurs en ligne',
		copy: 'Cliquez pour copier l\'adresse',
		copied: 'Copié !',
	},

	// The footer.
	footer: {
		credit: '{plus} par NR137, conçu avec l\'IA · Inspiré de {original} par borrageiros',
		disclaimer: 'Ce n\'est pas un produit officiel de Minecraft. Il n\'est ni approuvé par Mojang ou Microsoft, ni associé à eux.',
		assets: 'Noms, textures et textes de Minecraft © Mojang · Police : {font} par macimas (OFL)',
	},

	// The start page: the ranking and its podium.
	ranking: {
		heading: 'Classement',
		lead: 'Les meilleurs du serveur.',
		nobody: 'Personne pour l\'instant',
		rankingEmpty: 'Aucun joueur avec des statistiques pour l\'instant.',
		search: 'Rechercher des joueurs',
		podium: 'Podium',
	},

	// The player list.
	players: {
		heading: 'Joueurs',
		lead: 'Tous ceux qui ont joué sur le serveur, par temps de jeu.',
		empty: 'Aucun joueur avec des statistiques pour l\'instant.',
		count: '{count} joueurs',
		countOne: '1 joueur',
		columns: {
			player: 'Joueur',
			hours: 'Heures',
			rate: 'Actions/h',
			score: 'Niveau',
			advancements: 'Progrès',
		},
	},

	// A player's page.
	player: {
		tiersLink: 'Que sont les paliers ?',
		noneAchievedInTab: 'Rien d\'accompli dans cet onglet pour l\'instant.',
		notFound: 'Aucun joueur de ce nom sur ce serveur.',
		rankOf: 'Rang {rank} sur {total}',
		playTime: 'Temps de jeu',
		actionsPerHour: 'Actions par heure',
		firstJoin: 'Première connexion',
		lastSeen: 'Dernière connexion',
		onlineNow: 'En ligne',
		unknown: 'inconnue',
		firstJoinEstimate: 'Estimée d\'après le premier progrès ou la première recette débloqués.',
		lastSeenEstimate: 'Estimée d\'après la dernière sauvegarde de ce joueur par le serveur.',
		tiers: {
			stone: 'Pierre',
			copper: 'Cuivre',
			iron: 'Fer',
			gold: 'Or',
			diamond: 'Diamant',
			netherite: 'Netherite',
			dragon_egg: 'Oeuf de dragon',
		},
		tier: 'Palier {tier}',
		nextTier: 'Encore {count} pour {tier}',
		allAdvancements: 'Tous les progrès accomplis',
		skills: 'Compétences',
		search: 'Rechercher des statistiques',
		hideZeros: 'Masquer les zéros',
		noMatches: 'Aucun résultat pour cette recherche.',
		shown: '{shown} sur {total}',
		stats: 'Statistiques',
		sameName: 'Un autre joueur porte le même nom :',
		advancements: 'Progrès',
		achievedOnly: 'Accomplis uniquement',
	},

	// The seven skills the level is made of, and the parts each counts.
	skills: {
		combat: {
			name: 'Combat',
		},
		milestones: {
			name: 'Exploits',
		},
		mining: {
			name: 'Minage',
		},
		exploration: {
			name: 'Exploration',
		},
		building: {
			name: 'Construction',
		},
		farming: {
			name: 'Agriculture',
		},
		survival: {
			name: 'Survie',
		},
		parts: {
			milestones: 'Exploits',
			repeats: 'Répétitions',
			mob_types: 'Types de créatures',
			kills: 'Éliminations',
			biomes: 'Biomes',
			distance: 'Distance',
			ore_types: 'Types de minerais',
			ores: 'Points de minerai',
			block_types: 'Types de blocs',
			crafted_types: 'Objets fabriqués',
			placed: 'Blocs posés',
			food_types: 'Aliments',
			animal_types: 'Animaux élevés',
			farming: 'Travail',
			hours_per_death: 'Heures par mort',
			kills_per_death: 'Éliminations par mort',
		},
	},

	// The leaderboards page, a board, and every board by its ID: name, hint (one line), about (how it counts).
	boards: {
		page: {
			heading: 'Tableaux',
			lead: 'Choisissez un tableau.',
			allStats: 'Toutes les statistiques →',
		},
		board: {
			fallbackTitle: 'Tableau',
			notFound: 'Ce tableau n\'existe pas.',
			empty: 'Personne n\'a encore cette statistique. Soyez le premier !',
		},
		categories: {
			rankings: 'Classements',
			combat: 'Combat',
			mining: 'Minage',
			exploration: 'Exploration',
			life: 'Vie du serveur',
			deaths: 'Morts',
			advancements: 'Progrès',
			totals: 'Totaux',
			skills: 'Compétences',
		},
		legends: {
			name: 'Légendes',
			hint: 'Le niveau 100 correspond à un très bon joueur dans chaque compétence.',
			about:
				'Le niveau se compose des sept compétences : Exploits, Combat, Exploration, Minage, Construction, Agriculture et Survie. 100 signifie très bon dans toutes ; au-delà, il continue de monter lentement. « Notre classement » explique chaque élément.',
		},
		veterans: {
			name: 'Vétérans',
			hint: 'Heures réelles sur le serveur.',
		},
		grinders: {
			name: 'Grinders',
			hint: 'Blocs minés et posés, distance et éliminations par heure de jeu.',
			about:
				'Ce qu\'un joueur accomplit par heure en ligne : blocs minés et posés, distance parcourue (un kilomètre compte 200, les wagonnets rien du tout) et monstres tués (10 chacun, plafonnés comme en Combat), divisés par les heures de jeu. Les joueurs comptent à partir de 2 heures de temps de jeu. Le temps AFK ajoute des heures mais rien d\'autre, et fait baisser la valeur.',
		},
		advancements: {
			name: 'Progrès',
			hint: 'Progrès vanilla accomplis. Les recettes ne comptent pas.',
		},
		'hostile-kills': {
			name: 'Monstres tués',
			hint: 'Monstres tués.',
			about: 'Tous les monstres, poissons d\'argent compris.',
		},
		'hostile-no-silverfish': {
			name: 'Monstres sans poissons d\'argent',
			hint: 'Monstres tués, sans compter les poissons d\'argent.',
			about: 'Tous les monstres sauf les poissons d\'argent, que les fermes tuent par milliers.',
		},
		'other-mobs': {
			name: 'Autres créatures',
			hint: 'Créatures tuées qui ne sont pas hostiles.',
			about:
				'Toutes les créatures qui ne sont pas hostiles : animaux, poissons, poulpes, chauves-souris, villageois, golems, … Les joueurs ont leur propre tableau.',
		},
		bosses: {
			name: 'Boss',
			hint: 'Ender Dragon, Wither, Warden et grand gardien.',
		},
		'player-kills': {
			name: 'Joueurs tués',
			hint: 'Joueurs tués.',
		},
		deaths: {
			name: 'Morts au total',
			hint: 'Nombre de fois où vous êtes mort. Le plus en tête ; en comparaison, le moins gagne.',
		},
		'kills-per-hour': {
			name: 'Éliminations par heure',
			hint: 'Créatures tuées par heure de jeu.',
			about: 'Toutes les créatures tuées, fermes comprises, divisées par les heures de jeu.',
		},
		'blocks-mined': {
			name: 'Blocs minés',
			hint: 'Total des blocs minés.',
		},
		ores: {
			name: 'Minerais',
			hint: 'Minerais minés.',
		},
		'diamonds-mined': {
			name: 'Diamants minés',
			hint: 'Minerai de diamant et minerai de diamant des abîmes minés.',
		},
		'iron-mined': {
			name: 'Fer miné',
			hint: 'Minerai de fer et minerai de fer des abîmes minés.',
		},
		'coal-mined': {
			name: 'Charbon miné',
			hint: 'Minerai de charbon et minerai de charbon des abîmes minés.',
		},
		'copper-mined': {
			name: 'Cuivre miné',
			hint: 'Minerai de cuivre et minerai de cuivre des abîmes minés.',
		},
		'gold-mined': {
			name: 'Or miné',
			hint: 'Minerai d\'or, minerai d\'or des abîmes et minerai d\'or du Nether minés.',
		},
		'redstone-mined': {
			name: 'Redstone minée',
			hint: 'Minerai de redstone et minerai de redstone des abîmes minés.',
		},
		'lapis-mined': {
			name: 'Lapis-lazuli miné',
			hint: 'Minerai de lapis-lazuli et minerai de lapis-lazuli des abîmes minés.',
		},
		'emerald-mined': {
			name: 'Émeraudes minées',
			hint: 'Minerai d\'émeraude et minerai d\'émeraude des abîmes minés.',
		},
		'quartz-mined': {
			name: 'Quartz du Nether miné',
			hint: 'Minerai de quartz du Nether miné.',
		},
		'deaths-per-hour': {
			name: 'Morts par heure',
			hint: 'Morts par heure de jeu. Le plus en tête ; en comparaison, le moins gagne.',
			about:
				'Les morts divisées par les heures de jeu. Le plus grand nombre est en tête ; quand on compare deux joueurs, celui qui a le moins de morts par heure gagne.',
		},
		'ancient-debris': {
			name: 'Débris antiques',
			hint: 'Débris antiques minés.',
		},
		'logs-mined': {
			name: 'Bûches',
			hint: 'Bûches minées.',
		},
		'tools-broken': {
			name: 'Objets cassés',
			hint: 'Total des objets usés : outils, armes, armures, … dont la durabilité est épuisée.',
			about:
				'Les objets dont la durabilité s\'est épuisée en main ou sur vous : outils, armes, armures, élytres, boucliers, cannes à pêche, …',
		},
		'distance-total': {
			name: 'Distance parcourue',
			hint: 'Distance parcourue en km, sans le vol ni les chutes.',
			about:
				'Tous les moyens de se déplacer comptent : marche, course, nage, bateaux, élytres, chevaux, ghasts joyeux, nautiles, wagonnets, arpenteurs, escalade, accroupissement, et marche sur ou sous l\'eau. Le vol en mode Créatif et les chutes ne comptent pas.',
		},
		'items-dropped': {
			name: 'Objets jetés',
			hint: 'Total des objets jetés.',
		},
		'items-picked-up': {
			name: 'Objets ramassés',
			hint: 'Total des objets ramassés.',
		},
		'items-used': {
			name: 'Objets utilisés',
			hint: 'Total des objets utilisés : posés, mangés, maniés, lancés, …',
			about:
				'Tout ce que le jeu compte comme utilisation d\'un objet : poser des blocs, manger et boire, outils sur des blocs, armes sur des créatures, lancers, seaux, … et ranger des objets dans un vase décoré.',
		},
		'items-crafted': {
			name: 'Objets fabriqués',
			hint: 'Total des objets fabriqués : établi, fourneau, table de forge, tailleur de pierre et commerce.',
			about:
				'Tout ce que le jeu compte comme fabrication : la grille de fabrication de l\'inventaire et l\'établi, les fourneaux, la table de forge, le tailleur de pierre, la table de cartographie et le commerce avec les villageois. Chaque objet compte, donc 4 planches fabriquées d\'un coup comptent pour 4.',
		},
		'blocks-placed': {
			name: 'Blocs posés',
			hint: 'Blocs posés (le jeu compte la pose comme une utilisation).',
			about:
				'Le jeu ne compte pas les blocs posés à part ; il compte la pose comme une utilisation de l\'objet. Ce tableau additionne le nombre d\'utilisations de tous les objets de type bloc, ce qui correspond presque uniquement à la pose (planter des graines compte aussi : elles sont posées comme une plante).',
		},
		'mob-types': {
			name: 'Types de créatures',
			hint: 'Nombre de types de créatures différents tués.',
			about:
				'Chaque type de créature compte une fois, dès que le joueur en a tué une : hostiles, neutres et passives.',
		},
		'kills-per-death': {
			name: 'Éliminations par mort',
			hint:
				'Monstres tués (sans les poissons d\'argent) par mort ; sans aucune mort, les éliminations elles-mêmes.',
			about:
				'Monstres tués (sans les poissons d\'argent) divisés par le nombre de morts. Un joueur qui n\'est jamais mort obtient ses éliminations comme valeur, comme s\'il était mort une fois.',
		},
		'distance-per-hour': {
			name: 'Distance par heure',
			hint: 'Distance parcourue par heure de jeu.',
			about: 'Distance parcourue divisée par les heures de jeu : qui se déplace le plus quand il est en ligne.',
		},
		'food-eaten': {
			name: 'Nourriture mangée',
			hint: 'Nourriture mangée, parts de gâteau comprises.',
			about: 'Tout ce qui a été mangé : chaque aliment et les parts d\'un gâteau posé.',
		},
		'current-life': {
			name: 'Vie actuelle',
			hint: 'Temps de jeu depuis la dernière mort (le jeu ne le compte que lorsque vous êtes en ligne).',
			about:
				'Depuis combien de temps le joueur est en vie depuis sa dernière mort, en temps de jeu : le jeu ne le compte que lorsqu\'il est en ligne et repart de zéro à chaque mort. Un joueur qui n\'est jamais mort compte depuis sa première connexion.',
		},
		'distance-walk-sprint': {
			name: 'Marche et course',
			hint: 'Distance parcourue en marchant et en courant.',
		},
		'distance-elytra': {
			name: 'Élytres',
			hint: 'Distance parcourue en élytres.',
		},
		'play-time': {
			name: 'Temps de jeu',
			hint: 'Temps réel sur le serveur.',
		},
		'skill-milestones': {
			name: 'Exploits',
			hint: 'Boss, raids, trouvailles et objets rares ; chacun compte pleinement une fois.',
			about:
				'Les grands moments d\'un monde : vaincre l\'Ender Dragon, le Wither, le Warden et un grand gardien ; remporter un raid ; trouver des élytres, un trident, l\'oeuf de dragon, un coeur de la mer, un crâne de Wither squelette et un oeuf de renifleur ; fabriquer une balise, de la netherite, un conduit et une masse ; être sauvé par un totem d\'immortalité ; manger une pomme dorée enchantée. Chacun compte pleinement la première fois (trois quarts de la compétence ; 100 des 118 points la remplissent). Vaincre de nouveau un boss ou fabriquer une autre balise, un autre lingot de netherite, un autre conduit ou une autre masse ajoute un peu plus (un quart), de moins en moins à chaque répétition. Les raids, les totems et les trouvailles ne comptent qu\'une fois : une ferme à raids ou ramasser à nouveau les mêmes élytres n\'ajoute rien.',
		},
		'skill-combat': {
			name: 'Combat',
			hint: 'Types de monstres et éliminations, plafonnées par type.',
			about:
				'Types de monstres tués, sur 30 (la moitié de la compétence), et monstres tués (l\'autre moitié, 100 à 4 000). Chaque type compte pleinement jusqu\'à 250 éliminations, ensuite seule la racine carrée du reste compte, donc une ferme ajoute peu : 120 000 piglins zombifiés comptent pour environ 600 éliminations. Les animaux, les poissons et les villageois ne comptent pas.',
		},
		'skill-exploration': {
			name: 'Exploration',
			hint: 'Biomes visités et distance parcourue.',
			about:
				'Biomes visités, sur 50 (la moitié de la compétence) : les biomes de la Surface du progrès « Partons à l\'aventure ! » et les biomes du Nether de « Voyage au bout de l\'enfer ». S\'y ajoute la distance parcourue (l\'autre moitié, 100 à 5 000 km) : marche, course, nage, bateaux, élytres, chevaux et autres montures, escalade. Les wagonnets comptent pour un cinquième, car une boucle de wagonnet continue de tourner pendant votre absence. Le vol en mode Créatif et les chutes ne comptent pas.',
		},
		'skill-mining': {
			name: 'Minage',
			hint: 'Types de minerais et minerais, plus ils sont rares, plus ils valent.',
			about:
				'Types de minerais minés, sur 10 (30 % de la compétence), et les minerais eux-mêmes (70 %, 100 à 12 000 points), plus ils sont rares, plus ils valent : débris antiques 15, diamant 8, émeraude 6, or 3, fer 2, lapis-lazuli 1,5, quartz du Nether 1,2, cuivre 1, redstone 0,8, charbon 0,25. Chaque minerai compte pleinement jusqu\'à 1 500 blocs, ensuite seule la racine carrée du reste compte. La roche, la terre et l\'ardoise des abîmes ne comptent pas.',
		},
		'skill-building': {
			name: 'Construction',
			hint: 'Types de blocs posés, objets fabriqués et blocs posés.',
			about:
				'Types de blocs posés, sur 400 (35 % de la compétence), types d\'objets fabriqués, sur 250 (15 %), et blocs posés (la moitié, 100 à 300 000). Le jeu compte la pose d\'un bloc comme une utilisation de son objet, et ces utilisations sont additionnées. Beaucoup de blocs différents valent plus qu\'une grande quantité d\'un seul.',
		},
		'skill-farming': {
			name: 'Agriculture',
			hint: 'Nourriture, élevage, commerce, pêche et récoltes.',
			about:
				'Aliments mangés, sur 25 sortes (un quart de la compétence), types d\'animaux élevés, sur 15 (un quart, d\'après le progrès « Deux par deux »), et le travail (la moitié, 100 à 5 000) : chaque animal élevé, les échanges avec les villageois (pleinement jusqu\'à 2 000), les poissons pêchés (pleinement jusqu\'à 1 000) et les cultures récoltées (20 comptent pour 1).',
		},
		'skill-survival': {
			name: 'Survie',
			hint: 'Heures jouées et éliminations par mort.',
			about:
				'Rester en vie : heures jouées par mort (la moitié de la compétence, 100 à 8 heures) et monstres tués par mort (la moitié, 100 à 100). Les deux sont divisés par le nombre de morts plus un, donc un joueur qui n\'est jamais mort compte comme s\'il était mort une fois. Cette compétence s\'arrête à 100.',
		},
	},

	// Units of the boards: name and hint.
	units: {
		hours: {
			name: 'heures',
			hint: 'Temps réel sur le serveur.',
		},
		kills: {
			name: 'éliminations',
			hint: 'Créatures ou joueurs tués.',
		},
		count: {
			name: 'fois',
			hint: 'Nombre de fois où c\'est arrivé.',
		},
		hearts: {
			name: 'cœurs',
			hint: 'Dégâts en cœurs.',
		},
		kills_per_hour: {
			name: 'élim./h',
			hint: 'Créatures tuées par heure de jeu.',
		},
		deaths_per_hour: {
			name: 'morts/h',
			hint: 'Morts par heure de jeu.',
		},
		blocks: {
			name: 'blocs',
			hint: 'Nombre de blocs.',
		},
		km: {
			name: 'km',
			hint: 'Distance parcourue en kilomètres.',
		},
		km_per_hour: {
			name: 'km/h',
			hint: 'Kilomètres par heure de jeu.',
		},
		mob_types: {
			name: 'types de créatures',
			hint: 'Types de créatures différents.',
		},
		ratio: {
			name: 'élim./mort',
			hint: 'Créatures tuées par mort.',
		},
		level: {
			name: 'niveau',
			hint: 'Le niveau du classement : 100 correspond à un très bon joueur dans chaque compétence.',
		},
		skill: {
			name: 'points',
			hint: '100 correspond à un très bon joueur dans cette compétence ; au-delà, la valeur continue de monter lentement.',
		},
		actions_per_hour: {
			name: 'actions/h',
			hint: 'Blocs minés et posés, distance et éliminations par heure de jeu.',
		},
	},

	// The statistics page and the game's statistic groups.
	stats: {
		groupTabs: 'Groupes de statistiques',
		groups: {
			custom: {
				short: 'Général',
				name: 'Statistiques générales',
				hint: '{name}.',
			},
			killed: {
				short: 'Tués',
				name: 'Créatures tuées',
				hint: 'Tué : {name}.',
			},
			killed_by: {
				short: 'Morts',
				name: 'Morts causées par des créatures',
				hint: 'Tué par : {name}.',
			},
			mined: {
				short: 'Minés',
				name: 'Blocs minés',
				hint: 'Miné : {name}.',
			},
			crafted: {
				short: 'Fabriqués',
				name: 'Objets fabriqués',
				hint: 'Fabriqué : {name}.',
			},
			used: {
				short: 'Utilisés',
				name: 'Objets utilisés',
				hint: 'Utilisé : {name}.',
			},
			broken: {
				short: 'Cassés',
				name: 'Objets cassés',
				hint: 'Cassé : {name}.',
			},
			picked_up: {
				short: 'Ramassés',
				name: 'Objets ramassés',
				hint: 'Ramassé : {name}.',
			},
			dropped: {
				short: 'Jetés',
				name: 'Objets jetés',
				hint: 'Jeté : {name}.',
			},
		},
		lead: 'Tout ce que Minecraft compte. Choisissez-en une pour voir son tableau.',
		heading: 'Statistiques',
	},

	// The advancements pages, a player's advancement, criteria and variants.
	advancements: {
		heading: 'Progrès',
		lead: 'Qui a accompli quoi.',
		most: 'Le plus de progrès',
		mostLead: 'Combien de progrès vanilla chaque joueur a accomplis. Les recettes ne comptent pas.',
		search: 'Rechercher des progrès',
		empty: 'Personne n\'a encore accompli ce progrès.',
		variants: {
			cat: {
				tabby: 'Tigré brun',
				black: 'Bicolore',
				red: 'Tigré roux',
				siamese: 'Siamois',
				british_shorthair: 'British Shorthair',
				calico: 'Calico',
				persian: 'Persan',
				ragdoll: 'Ragdoll',
				white: 'Angora',
				jellie: 'Jellie',
				all_black: 'Panthère',
			},
			wolf: {
				pale: 'Blanc',
				ashen: 'Cendré',
				black: 'Noir',
				chestnut: 'Marron',
				rusty: 'Roux',
				snowy: 'Des neiges',
				spotted: 'Tacheté',
				striped: 'À rayures',
				woods: 'Des bois',
			},
			frog: {
				temperate: 'Tempérée',
				cold: 'Froide',
				warm: 'Chaude',
			},
			site: {
				desert_pyramid: 'Brossé dans une pyramide du désert',
				desert_well: 'Brossé dans un puits du désert',
				ocean_ruin_cold: 'Brossé dans des ruines océaniques froides',
				ocean_ruin_warm: 'Brossé dans des ruines océaniques chaudes',
				trail_ruins_common: 'Brossé dans des ruines de sentier (butin commun)',
				trail_ruins_rare: 'Brossé dans des ruines de sentier (butin rare)',
				has_sherd: 'Tesson de poterie dans l\'inventaire',
			},
		},
		player: {
			doneOn: 'Accompli : {when}',
			notYet: 'Pas encore accompli',
			progress: '{done} sur {total} accomplis',
			partsDone: 'Accompli',
			partsMissing: 'Manquant',
			anyOf: 'Un seul de ces éléments suffit :',
			allPlayers: 'Tous les joueurs ayant ce progrès',
		},
		criteria: {
			heart_transplanter: {
				place_creaking_heart_awake: 'Coeur de grinceur posé (actif)',
				place_creaking_heart_dormant: 'Coeur de grinceur posé (en sommeil)',
				place_pale_oak_log: 'Bûche de chêne pâle posée pour le compléter',
			},
			distract_piglin: {
				distract_piglin: 'Objet en or lancé à un piglin',
				distract_piglin_directly: 'Lingot d\'or donné à un piglin',
			},
			loot_bastion: {
				loot_bastion_bridge: 'Coffre d\'un pont',
				loot_bastion_hoglin_stable: 'Coffre d\'une écurie à hoglins',
				loot_bastion_treasure: 'Coffre d\'une salle au trésor',
				loot_bastion_other: 'Coffre générique de bastion',
			},
			root: {
				killed_something: 'Tuer quelque chose',
				killed_by_something: 'Être tué par quelque chose',
			},
			read_power_of_chiseled_bookshelf: {
				chiseled_bookshelf: 'Bibliothèque sculptée posée à côté d\'un comparateur de redstone',
				comparator: 'Comparateur de redstone posé à côté d\'une bibliothèque sculptée',
			},
			uh_oh: {
				give_tnt_directly: 'TNT donnée à un cube de soufre',
				pick_up_dropped_tnt: 'Un cube de soufre a ramassé de la TNT lâchée',
			},
		},
		reward: 'Récompense : {xp} points d\'expérience',
		view: 'Affichage',
		viewTiles: 'Tuiles',
		viewTree: 'Arbre',
		tabs: 'Onglets de progrès',
		notFound: 'Ce progrès n\'existe pas.',
		nobody: 'Personne n\'a encore de progrès.',
		holder: '1 joueur',
		holders: '{count} joueurs',
		hidden: 'Caché',
		hideHidden: 'Masquer les progrès cachés',
		hideHiddenHint:
			'Masque les progrès que le jeu garde secrets, pour ne pas gâcher la surprise.',
		doneOf: '{done} sur {total}',
	},

	// The compare page.
	compare: {
		heading: 'Comparer des joueurs',
		lead: 'Choisissez deux joueurs et voyez qui mène dans chaque compétence et statistique.',
		verdict: '{name} remporte {wins} compétences sur {total}.',
		verdictLevel: '{wins} compétences chacun ; {name} a le meilleur niveau.',
		verdictEven: 'Égalité parfaite : les mêmes compétences et le même niveau.',
		skillParts: 'Composition',
		partTarget: '100 à {target}',
		vanilla: 'Statistiques',
		vanillaEmpty: 'Aucun des deux n\'a encore cette statistique.',
		playerA: 'Joueur A',
		playerB: 'Joueur B',
		choose: 'Choisir',
		waiting: 'En attente des statistiques',
		unselected: 'Non sélectionné',
		slotA: 'A',
		slotB: 'B',
		needTwo: 'Choisissez deux joueurs pour les comparer.',
		same: 'Choisissez deux joueurs différents.',
		advancementsDiff: 'Progrès exclusifs',
		onlyAdvancements: 'Seulement {name}',
		onlyAdvancementsEmpty: 'Aucun progrès exclusif',
		otherAdvancements: 'Autres progrès',
	},

	// The 3D skin viewer.
	skin: {
		head: 'Tête',
		body: 'Corps',
		actions: 'Commandes du skin',
		poses: {
			idle: 'Immobile',
			walk: 'Marcher',
			run: 'Courir',
			fly: 'Voler',
		},
		pause: 'Pause',
		resume: 'Reprendre',
		autoRotate: 'Rotation auto',
		scrollZoom: 'Zoomer avec la molette',
		equipment: {
			cape: 'Cape',
			elytra: 'Élytres',
			none: 'Sans cape',
		},
	},

	// How we rank.
	about: {
		howLink: 'Comment est-ce calculé ?',
		heading: 'Notre classement',
		lead:
			'Minecraft ne note pas votre façon de jouer ; il compte seulement ce que vous faites. Une seule statistique récompenserait la mauvaise chose : le temps de jeu récompense l\'AFK, les éliminations brutes récompensent une ferme à créatures. C\'est pourquoi le classement examine sept compétences et en tire un seul niveau.',
		farmsHeading: 'Fermes et AFK',
		farms:
			'Nous ne cachons pas les fermes : les éliminations des fermes à poissons d\'argent et à or apparaissent dans le tableau « Monstres tués », les poissons dans « Poissons pêchés ». Elles ne décident simplement pas du niveau. Rester AFK rapporte du temps de jeu, qui ne compte que pour la Survie, et fait baisser les actions par heure.',
		rankingsHeading: 'Trois classements',
		rankingLegends: 'Légendes : le niveau le plus élevé.',
		rankingVeterans: 'Vétérans : le plus d\'heures sur le serveur.',
		tiersHeading: 'Paliers de progrès',
		tiers:
			'Chaque page de joueur montre, sous forme de barre avec un palier, quelle part des progrès vanilla ce joueur a accomplie. Les paliers portent des noms de matériaux de Minecraft et sont juste pour le plaisir : ils ne comptent pas dans le niveau (les Exploits comptent certains des mêmes faits d\'après les statistiques).',
		unitsHeading: 'Des unités qui ont du sens',
		units: 'Les distances en km, le temps en heures, les dégâts en cœurs. Jamais de centimètres ni de ticks.',
		levelHeading: 'Le niveau',
		level:
			'Chaque compétence reçoit un score : 100 correspond à un très bon joueur dans cette compétence, quelqu\'un qui en a vu et fait l\'essentiel. Le niveau combine les compétences avec des poids fixes, donc le niveau 100 signifie très bon en tout. Comme le niveau d\'expérience du jeu, c\'est un nombre entier, arrondi à l\'inférieur.',
		levelOpen:
			'Il n\'y a pas de limite supérieure. Au-delà de 100, une compétence continue de monter, mais lentement : faire deux fois plus qu\'un très bon joueur n\'ajoute que peu, donc être extrêmement bon dans un domaine ne vaut jamais plus qu\'être bon en tout. Les barres des pages de joueur vont de 0 à 100 et restent pleines au-delà.',
		levelOwn:
			'Votre niveau dépend uniquement de ce que vous avez fait, jamais des autres joueurs : personne ne perd de points parce qu\'un autre a joué davantage. En dehors de la Survie, il ne baisse jamais, car les statistiques du jeu ne font qu\'augmenter. En cas d\'égalité, celui qui a eu besoin de moins de temps de jeu passe devant ; ensuite, les joueurs sont triés par ordre alphabétique de leur nom.',
		partsHeading: 'Étendue et profondeur',
		parts:
			'Chaque compétence se compose de deux sortes d\'éléments. L\'étendue compte combien de choses différentes vous avez faites : types de créatures, biomes, minerais, types de blocs. Elle est pleine à un nombre fixe et ne va jamais au-delà, et aucune ferme ne peut la fausser. La profondeur compte combien vous en avez fait, selon une courbe : la première centaine compte beaucoup, la suivante un peu moins. Elle atteint 100 à ce qu\'un joueur expérimenté a après quelques centaines d\'heures.',
		caps:
			'Tout ce qu\'une ferme peut faire sans fin a un plafond souple : les éliminations comptent pleinement jusqu\'à 250 par type de créature, les minerais jusqu\'à 1 500 par sorte, les échanges jusqu\'à 2 000 et les poissons jusqu\'à 1 000. Au-delà, seule la racine carrée compte, donc les 120 000 piglins zombifiés d\'une ferme à or comptent pour environ 600 éliminations. Une ferme ajoute toujours un peu ; elle ne peut simplement pas gagner une compétence à elle seule.',
		skillsHeading: 'Les sept compétences',
		skillWeight: '{weight} du niveau',
		dyingHeading: 'Mourir',
		dying:
			'Une mort ne fait baisser que la Survie, un dixième du niveau. Plus vous avez joué, moins une mort compte : un joueur expérimenté perd bien moins d\'un dixième de niveau, ce qui ne se voit généralement pas du tout. Seules les premières morts d\'un nouveau joueur coûtent environ un niveau et demi, et il les récupère une fois qu\'il a joué à peu près aussi longtemps sans mourir qu\'avant cette mort.',
		rankingGrinders:
			'Grinders : ce qu\'un joueur accomplit par heure en ligne. Blocs minés et posés, distance (un kilomètre compte 200, les wagonnets rien du tout) et monstres tués (10 chacun, plafonnés comme ci-dessus), divisés par les heures de jeu, à partir de 2 heures de temps de jeu. Le temps AFK ajoute des heures mais rien d\'autre.',
	},

	// The licenses page.
	licenses: {
		heading: 'Licences',
		lead: 'Qui a créé les éléments qui composent ce site, et sous quelles licences ils sont utilisés.',
		minecraftHeading: 'Minecraft',
		minecraft:
			'Minecraft ainsi que les noms, textures et textes du jeu appartiennent à Mojang et Microsoft. Ce site les affiche conformément aux {guidelines}.',
		guidelines: 'Conditions d\'utilisation de Minecraft',
		guidelinesUrl: 'https://www.minecraft.net/fr-fr/usage-guidelines',
		download:
			'Le mod ne contient aucun fichier du jeu. Chaque serveur télécharge les textures et les textes de Minecraft auprès de Mojang, comme le fait le launcher, et en tire les icônes.',
		skins: 'Les skins appartiennent à leurs joueurs. Ils sont chargés depuis Mojang, et depuis GeyserMC pour les joueurs Bedrock.',
		projectHeading: 'Stats WebUI Plus',
		project:
			'{plus} est un logiciel libre sous licence MIT.',
		showText: 'Afficher le texte de la licence',
		fontHeading: 'Polices',
		font:
			'Les textes utilisent {font} par macimas : une recréation libre de la police de Minecraft, et non un fichier du jeu, sous licence SIL Open Font License 1.1.',
		// The fonts the site heading and page titles use: only those this server uses are named.
		fontBoth:
			'Le titre du site et les titres des pages utilisent {font} par {author}, également sous licence SIL Open Font License 1.1 ; son texte de licence figure dans la liste ci-dessous.',
		fontSite:
			'Le titre du site utilise {font} par {author}, également sous licence SIL Open Font License 1.1 ; son texte de licence figure dans la liste ci-dessous.',
		fontTitles:
			'Les titres des pages utilisent {font} par {author}, également sous licence SIL Open Font License 1.1 ; son texte de licence figure dans la liste ci-dessous.',
		// The server owner's own font for the site heading; their text about it follows.
		fontCustom: 'Le titre du site utilise {name}, une police choisie par ce serveur :',
		packagesHeading: 'Logiciels open source de ce site',
		packages: 'Le site contient du code issu de ces paquets. Les mêmes mentions dans un seul fichier : {file}.',
		loading: 'Chargement…',
		error: 'Impossible de charger la liste des licences.',
	},

	// The server owner's own pages.
	pages: {
		notFoundTitle: 'Page introuvable',
		notFound: 'Cette page n\'existe pas.',
	},
};
