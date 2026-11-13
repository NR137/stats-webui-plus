import type { Messages } from './en';

export const es: Messages = {
	// The whole site.
	site: {
		// Any page or part of one whose data could not be loaded.
		loadError: 'No se ha podido cargar. Inténtalo de nuevo en un momento.',
		loading: 'Cargando…',
		// The slogan in quotes (config siteHeadingSloganStyle "quote"), with this language's quotation marks.
		sloganQuote: '«{text}»',
	},

	// The menu and the links back.
	menu: {
		ranking: 'Ranking',
		players: 'Jugadores',
		leaderboards: 'Tablas',
		advancements: 'Progresos',
		compare: 'Comparar',
		stats: 'Estadísticas',
		about: 'Cómo puntuamos',
		licenses: 'Licencias',
		back: 'Volver',
		open: 'Menú',
		// The hotbar's last slot, with the pages that don't fit in one row.
		more: 'Más',
		links: 'Enlaces',
		top: 'Volver arriba',
	},

	// The language and theme switches.
	settings: {
		language: {
			label: 'Idioma',
			es: 'Español',
			en: 'English',
			fr: 'Français',
			de: 'Deutsch',
		},
		theme: {
			label: 'Tema',
			system: 'Sistema',
			birch: 'Abedul',
			deepslate: 'Pizarra profunda',
		},
	},

	// The server's status in the header.
	server: {
		online: 'En línea',
		offline: 'Desconectado',
		players: '{count} / {max} jugadores en línea',
		copy: 'Haz clic para copiar la dirección',
		copied: '¡Copiada!',
	},

	// The footer.
	footer: {
		credit: '{plus} por NR137, hecho con IA · Inspirado en {original} de borrageiros',
		disclaimer: 'No es un producto oficial de Minecraft. No está aprobado por Mojang ni Microsoft ni asociado con ellos.',
		assets: 'Nombres, texturas y textos de Minecraft © Mojang · Fuente: {font} por macimas (OFL)',
	},

	// The start page: the ranking and its podium.
	ranking: {
		heading: 'Ranking',
		lead: 'Los mejores del servidor.',
		nobody: 'Nadie todavía',
		rankingEmpty: 'Todavía no hay jugadores con estadísticas.',
		search: 'Buscar jugadores',
		podium: 'Podio',
	},

	// The player list.
	players: {
		heading: 'Jugadores',
		lead: 'Todos los que han jugado en el servidor, por tiempo de juego.',
		empty: 'Todavía no hay jugadores con estadísticas.',
		count: '{count} jugadores',
		countOne: '1 jugador',
		columns: {
			player: 'Jugador',
			hours: 'Horas',
			rate: 'Acciones/h',
			score: 'Nivel',
			advancements: 'Progresos',
		},
	},

	// A player's page.
	player: {
		tiersLink: '¿Qué son los rangos?',
		noneAchievedInTab: 'Todavía no hay nada conseguido en esta pestaña.',
		notFound: 'No hay ningún jugador con este nombre en este servidor.',
		rankOf: 'Puesto {rank} de {total}',
		playTime: 'Tiempo de juego',
		actionsPerHour: 'Acciones por hora',
		firstJoin: 'Primera conexión',
		lastSeen: 'Última conexión',
		onlineNow: 'En línea ahora',
		unknown: 'desconocido',
		firstJoinEstimate: 'Estimado a partir del primer progreso o receta desbloqueados.',
		lastSeenEstimate: 'Estimado a partir de la última vez que el servidor guardó a este jugador.',
		tiers: {
			stone: 'Piedra',
			copper: 'Cobre',
			iron: 'Hierro',
			gold: 'Oro',
			diamond: 'Diamante',
			netherite: 'Netherita',
			dragon_egg: 'Huevo de dragón',
		},
		tier: 'Rango {tier}',
		nextTier: 'Faltan {count} para {tier}',
		allAdvancements: 'Todos los progresos conseguidos',
		skills: 'Habilidades',
		search: 'Buscar estadísticas',
		hideZeros: 'Ocultar ceros',
		noMatches: 'Nada coincide con esta búsqueda.',
		shown: '{shown} de {total}',
		stats: 'Estadísticas',
		sameName: 'Otro jugador tiene el mismo nombre:',
		advancements: 'Progresos',
		achievedOnly: 'Solo conseguidos',
	},

	// The seven skills the level is made of, and the parts each counts.
	skills: {
		combat: {
			name: 'Combate',
		},
		milestones: {
			name: 'Hitos',
		},
		mining: {
			name: 'Minería',
		},
		exploration: {
			name: 'Exploración',
		},
		building: {
			name: 'Construcción',
		},
		farming: {
			name: 'Cultivo y cría',
		},
		survival: {
			name: 'Supervivencia',
		},
		parts: {
			milestones: 'Hitos',
			repeats: 'Repeticiones',
			mob_types: 'Tipos de criaturas',
			kills: 'Bajas',
			biomes: 'Biomas',
			distance: 'Distancia',
			ore_types: 'Tipos de mena',
			ores: 'Puntos de mena',
			block_types: 'Tipos de bloque',
			crafted_types: 'Objetos fabricados',
			placed: 'Bloques colocados',
			food_types: 'Alimentos',
			animal_types: 'Animales criados',
			farming: 'Trabajo',
			hours_per_death: 'Horas por muerte',
			kills_per_death: 'Bajas por muerte',
		},
	},

	// The leaderboards page, a board, and every board by its ID: name, hint (one line), about (how it counts).
	boards: {
		page: {
			heading: 'Tablas',
			lead: 'Elige una clasificación.',
			allStats: 'Todas las estadísticas →',
		},
		board: {
			fallbackTitle: 'Clasificación',
			notFound: 'Esta clasificación no existe.',
			empty: 'Nadie tiene todavía esta estadística. Sé el primero.',
		},
		categories: {
			rankings: 'Rankings',
			combat: 'Combate',
			mining: 'Minería',
			exploration: 'Exploración',
			life: 'Vida en el servidor',
			deaths: 'Muertes',
			advancements: 'Progresos',
			totals: 'Totales',
			skills: 'Habilidades',
		},
		legends: {
			name: 'Leyendas',
			hint: 'El nivel 100 es un jugador muy bueno en todas las habilidades.',
			about:
				'El nivel se compone de las siete habilidades: Hitos, Combate, Exploración, Minería, Construcción, Cultivo y cría y Supervivencia. 100 significa muy bueno en todas; por encima sigue subiendo, pero despacio. «Cómo puntuamos» explica cada parte.',
		},
		veterans: {
			name: 'Veteranos',
			hint: 'Horas reales en el servidor.',
		},
		grinders: {
			name: 'Grinders',
			hint: 'Bloques minados y colocados, distancia y bajas por hora jugada.',
			about:
				'Lo que un jugador consigue por hora en línea: bloques minados y colocados, distancia recorrida (un kilómetro cuenta 200, las vagonetas nada) y criaturas hostiles matadas (10 cada una, con el mismo límite que en Combate), dividido entre las horas jugadas. Los jugadores cuentan a partir de 2 horas de tiempo de juego. El tiempo AFK suma horas pero nada más, y baja el valor.',
		},
		advancements: {
			name: 'Progresos',
			hint: 'Progresos del juego base conseguidos. Las recetas no cuentan.',
		},
		'hostile-kills': {
			name: 'Bajas hostiles',
			hint: 'Criaturas hostiles matadas.',
			about: 'Todas las criaturas hostiles, lepismas incluidas.',
		},
		'hostile-no-silverfish': {
			name: 'Hostiles sin lepismas',
			hint: 'Criaturas hostiles matadas, sin contar las lepismas.',
			about: 'Todas las criaturas hostiles excepto las lepismas, que las granjas matan por miles.',
		},
		'other-mobs': {
			name: 'Otras criaturas',
			hint: 'Criaturas matadas que no son hostiles.',
			about:
				'Todas las criaturas que no son hostiles: animales, peces, calamares, murciélagos, aldeanos, gólems, … Los jugadores tienen su propia clasificación.',
		},
		bosses: {
			name: 'Jefes',
			hint: 'Enderdragón, Wither, Warden y guardián anciano.',
		},
		'player-kills': {
			name: 'Jugadores matados',
			hint: 'Jugadores matados.',
		},
		deaths: {
			name: 'Muertes totales',
			hint: 'Cuántas veces has muerto. Más arriba quien más tiene; al comparar, gana quien tiene menos.',
		},
		'kills-per-hour': {
			name: 'Bajas por hora',
			hint: 'Criaturas matadas por hora jugada.',
			about: 'Todas las criaturas matadas, granjas incluidas, divididas entre las horas jugadas.',
		},
		'blocks-mined': {
			name: 'Bloques minados',
			hint: 'Total de bloques minados.',
		},
		ores: {
			name: 'Menas',
			hint: 'Menas minadas.',
		},
		'diamonds-mined': {
			name: 'Diamantes minados',
			hint: 'Mena de diamante y mena de diamante de pizarra profunda minadas.',
		},
		'iron-mined': {
			name: 'Hierro minado',
			hint: 'Mena de hierro y mena de hierro de pizarra profunda minadas.',
		},
		'coal-mined': {
			name: 'Carbón minado',
			hint: 'Mena de carbón y mena de carbón de pizarra profunda minadas.',
		},
		'copper-mined': {
			name: 'Cobre minado',
			hint: 'Mena de cobre y mena de cobre de pizarra profunda minadas.',
		},
		'gold-mined': {
			name: 'Oro minado',
			hint: 'Mena de oro, mena de oro de pizarra profunda y mena de oro del Nether minadas.',
		},
		'redstone-mined': {
			name: 'Redstone minada',
			hint: 'Mena de redstone y mena de redstone de pizarra profunda minadas.',
		},
		'lapis-mined': {
			name: 'Lapislázuli minado',
			hint: 'Mena de lapislázuli y mena de lapislázuli de pizarra profunda minadas.',
		},
		'emerald-mined': {
			name: 'Esmeraldas minadas',
			hint: 'Mena de esmeralda y mena de esmeralda de pizarra profunda minadas.',
		},
		'quartz-mined': {
			name: 'Cuarzo del Nether minado',
			hint: 'Mena de cuarzo del Nether minada.',
		},
		'deaths-per-hour': {
			name: 'Muertes por hora',
			hint: 'Muertes por hora jugada. Más arriba quien más tiene; al comparar, gana quien tiene menos.',
			about:
				'Las muertes divididas entre las horas jugadas. Arriba está quien más tiene; al comparar a dos jugadores, gana el que tiene menos muertes por hora.',
		},
		'ancient-debris': {
			name: 'Escombros ancestrales',
			hint: 'Escombros ancestrales minados.',
		},
		'logs-mined': {
			name: 'Troncos',
			hint: 'Troncos talados.',
		},
		'tools-broken': {
			name: 'Objetos rotos',
			hint: 'Total de objetos gastados: herramientas, armas, armaduras, … cuya durabilidad se agotó.',
			about:
				'Objetos cuya durabilidad se agotó en tu mano o en tu cuerpo: herramientas, armas, armaduras, élitros, escudos, cañas de pescar, …',
		},
		'distance-total': {
			name: 'Distancia recorrida',
			hint: 'Distancia recorrida en km, sin contar volar ni caer.',
			about:
				'Cuenta cualquier forma de desplazarse: andar, correr, nadar, barcas, élitros, caballos, ghasts felices, nautilos, vagonetas, lavagantes, trepar, agacharse y andar sobre el agua o bajo ella. Volar en modo creativo y caer no cuentan.',
		},
		'items-dropped': {
			name: 'Tirados',
			hint: 'Total de objetos tirados.',
		},
		'items-picked-up': {
			name: 'Recogidos',
			hint: 'Total de objetos recogidos.',
		},
		'items-used': {
			name: 'Objetos usados',
			hint: 'Total de objetos usados: colocados, comidos, blandidos, lanzados, …',
			about:
				'Todo lo que el juego cuenta como usar un objeto: colocar bloques, comer y beber, herramientas sobre bloques, armas contra criaturas, lanzar, cubos, … y meter objetos en una vasija decorada.',
		},
		'items-crafted': {
			name: 'Objetos fabricados',
			hint: 'Total de objetos fabricados: mesa de trabajo, horno, mesa de herrería, cortapiedras y comercio.',
			about:
				'Todo lo que el juego cuenta como fabricar: la cuadrícula de fabricación del inventario y la mesa de trabajo, hornos, la mesa de herrería, el cortapiedras, la mesa de cartografía y el comercio con aldeanos. Cuenta cada objeto, así que 4 tablones fabricados de una vez cuentan como 4.',
		},
		'blocks-placed': {
			name: 'Bloques colocados',
			hint: 'Bloques colocados (el juego cuenta colocar como usar).',
			about:
				'El juego no cuenta los bloques colocados por separado; cuenta colocar como usar el objeto. Esta clasificación suma cuántas veces se usaron todos los objetos de bloque, lo que es casi solo colocar (plantar semillas también cuenta: se colocan como planta).',
		},
		'mob-types': {
			name: 'Tipos de criaturas',
			hint: 'Cuántos tipos distintos de criaturas se han matado.',
			about:
				'Cada tipo de criatura cuenta una vez, en cuanto el jugador ha matado una: hostiles, neutrales y pasivas por igual.',
		},
		'kills-per-death': {
			name: 'Bajas por muerte',
			hint:
				'Criaturas hostiles matadas (sin contar lepismas) por muerte; si aún no hay muertes, las propias bajas.',
			about:
				'Criaturas hostiles matadas (sin contar lepismas) divididas entre las muertes. Quien nunca ha muerto recibe sus bajas como valor, como si hubiera muerto una vez.',
		},
		'distance-per-hour': {
			name: 'Distancia por hora',
			hint: 'Distancia recorrida por hora jugada.',
			about: 'La distancia recorrida dividida entre las horas jugadas: quién se mueve más mientras está en línea.',
		},
		'food-eaten': {
			name: 'Comida',
			hint: 'Comida ingerida, porciones de tarta incluidas.',
			about: 'Todo lo comido: cada alimento y las porciones de una tarta colocada.',
		},
		'current-life': {
			name: 'Vida actual',
			hint: 'Tiempo de juego desde la última muerte (el juego solo lo cuenta mientras estás en línea).',
			about:
				'Cuánto tiempo lleva vivo el jugador desde su última muerte, en tiempo de juego: el juego solo lo cuenta mientras está en línea y vuelve a cero con cada muerte. Quien nunca ha muerto cuenta desde su primera conexión.',
		},
		'distance-walk-sprint': {
			name: 'Andar y correr',
			hint: 'Distancia andada y corrida.',
		},
		'distance-elytra': {
			name: 'Élitros',
			hint: 'Distancia volada con élitros.',
		},
		'play-time': {
			name: 'Tiempo de juego',
			hint: 'Tiempo real en el servidor.',
		},
		'skill-milestones': {
			name: 'Hitos',
			hint: 'Jefes, invasiones, hallazgos raros y objetos; cada uno cuenta entero una vez.',
			about:
				'Los grandes momentos de un mundo: derrotar al Enderdragón, al Wither, al Warden y a un guardián anciano; ganar una invasión; encontrar unos élitros, un tridente, el huevo de dragón, un corazón del mar, una calavera de esqueleto del Wither y un huevo de sniffer; fabricar un faro, netherita, un canalizador y una maza; que te salve un tótem de inmortalidad; comerte una manzana dorada encantada. Cada uno cuenta entero la primera vez (tres cuartas partes de la habilidad; 100 de los 118 puntos la llenan). Volver a derrotar a un jefe o fabricar otro faro, lingote de netherita, canalizador o maza suma un poco más (una cuarta parte), y menos con cada repetición. Las invasiones, los tótems y los hallazgos cuentan solo una vez, así que una granja de invasiones o recoger otra vez los mismos élitros no suma nada.',
		},
		'skill-combat': {
			name: 'Combate',
			hint: 'Tipos de criaturas hostiles y bajas, con límite por tipo.',
			about:
				'Tipos de criaturas hostiles matadas, de 30 (la mitad de la habilidad), y bajas de criaturas hostiles (la otra mitad, 100 con 4000). Cada tipo cuenta entero hasta 250 bajas; a partir de ahí, solo la raíz cuadrada del resto, así que una granja suma poco: 120.000 piglins zombificados cuentan como unas 600 bajas. Los animales, los peces y los aldeanos no cuentan.',
		},
		'skill-exploration': {
			name: 'Exploración',
			hint: 'Biomas visitados y distancia recorrida.',
			about:
				'Biomas visitados, de 50 (la mitad de la habilidad): los biomas del Mundo superior del progreso «Hora de aventuras» y los del Nether de «Cálidos destinos turísticos». Además, la distancia recorrida (la otra mitad, 100 con 5000 km): andar, correr, nadar, barcas, élitros, caballos y otras monturas, trepar. Las vagonetas cuentan una quinta parte, porque un circuito de vagonetas sigue funcionando mientras no estás. Volar en creativo y caer no cuentan.',
		},
		'skill-mining': {
			name: 'Minería',
			hint: 'Tipos de mena y menas; cuanto más raras, más valen.',
			about:
				'Tipos de mena minados, de 10 (el 30 % de la habilidad), y las propias menas (el 70 %, 100 con 12.000 puntos); cuanto más raras, más valen: escombros ancestrales 15, diamante 8, esmeralda 6, oro 3, hierro 2, lapislázuli 1,5, cuarzo del Nether 1,2, cobre 1, redstone 0,8, carbón 0,25. Cada mena cuenta entera hasta 1500 bloques; a partir de ahí, solo la raíz cuadrada del resto. La piedra, la tierra y la pizarra profunda no cuentan.',
		},
		'skill-building': {
			name: 'Construcción',
			hint: 'Tipos de bloque colocados, objetos fabricados y bloques colocados.',
			about:
				'Tipos de bloque colocados, de 400 (el 35 % de la habilidad), tipos de objeto fabricados, de 250 (el 15 %), y bloques colocados (la mitad, 100 con 300.000). El juego cuenta colocar un bloque como usar su objeto, y se suman esos usos. Muchos bloques distintos cuentan más que montones de uno solo.',
		},
		'skill-farming': {
			name: 'Cultivo y cría',
			hint: 'Comida, cría, comercio, peces y cosecha.',
			about:
				'Alimentos comidos, de 25 tipos (una cuarta parte de la habilidad), tipos de animales criados, de 15 (otra cuarta parte, del progreso «De dos en dos, ¡ua!»), y el trabajo (la mitad, 100 con 5000): cada animal criado, los tratos con aldeanos (enteros hasta 2000), los peces pescados (enteros hasta 1000) y los cultivos cosechados (20 cuentan como 1).',
		},
		'skill-survival': {
			name: 'Supervivencia',
			hint: 'Horas jugadas y bajas por muerte.',
			about:
				'Seguir con vida: horas jugadas por muerte (la mitad de la habilidad, 100 con 8 horas) y criaturas hostiles matadas por muerte (la mitad, 100 con 100). Ambas se dividen entre las muertes más uno, así que quien nunca ha muerto cuenta como si hubiera muerto una vez. Esta habilidad se detiene en 100.',
		},
	},

	// Units of the boards: name and hint.
	units: {
		hours: {
			name: 'horas',
			hint: 'Tiempo real en el servidor.',
		},
		kills: {
			name: 'bajas',
			hint: 'Criaturas o jugadores matados.',
		},
		count: {
			name: 'veces',
			hint: 'Cuántas veces ha pasado.',
		},
		hearts: {
			name: 'corazones',
			hint: 'Daño en corazones.',
		},
		kills_per_hour: {
			name: 'bajas/h',
			hint: 'Criaturas matadas por hora jugada.',
		},
		deaths_per_hour: {
			name: 'muertes/h',
			hint: 'Muertes por hora jugada.',
		},
		blocks: {
			name: 'bloques',
			hint: 'Número de bloques.',
		},
		km: {
			name: 'km',
			hint: 'Distancia recorrida en kilómetros.',
		},
		km_per_hour: {
			name: 'km/h',
			hint: 'Kilómetros por hora jugada.',
		},
		mob_types: {
			name: 'tipos de criaturas',
			hint: 'Tipos distintos de criaturas.',
		},
		ratio: {
			name: 'bajas/muerte',
			hint: 'Criaturas matadas por muerte.',
		},
		level: {
			name: 'nivel',
			hint: 'El nivel del ranking: 100 es un jugador muy bueno en todas las habilidades.',
		},
		skill: {
			name: 'puntos',
			hint: '100 es un jugador muy bueno en esta habilidad; por encima, el valor sigue subiendo despacio.',
		},
		actions_per_hour: {
			name: 'acciones/h',
			hint: 'Bloques minados y colocados, distancia y bajas por hora jugada.',
		},
	},

	// The statistics page and the game's statistic groups.
	stats: {
		groupTabs: 'Grupos de estadísticas',
		groups: {
			custom: {
				short: 'General',
				name: 'Estadísticas generales',
				hint: '{name}.',
			},
			killed: {
				short: 'Matadas',
				name: 'Criaturas matadas',
				hint: 'Matado: {name}.',
			},
			killed_by: {
				short: 'Muertes',
				name: 'Muertes por criaturas',
				hint: 'Muerto por: {name}.',
			},
			mined: {
				short: 'Minados',
				name: 'Bloques minados',
				hint: 'Minado: {name}.',
			},
			crafted: {
				short: 'Fabricados',
				name: 'Fabricados',
				hint: 'Fabricado: {name}.',
			},
			used: {
				short: 'Usados',
				name: 'Usados',
				hint: 'Usado: {name}.',
			},
			broken: {
				short: 'Rotos',
				name: 'Rotos',
				hint: 'Roto: {name}.',
			},
			picked_up: {
				short: 'Recogidos',
				name: 'Recogidos',
				hint: 'Recogido: {name}.',
			},
			dropped: {
				short: 'Tirados',
				name: 'Tirados',
				hint: 'Tirado: {name}.',
			},
		},
		lead: 'Todo lo que cuenta Minecraft. Elige una para ver su clasificación.',
		heading: 'Estadísticas',
	},

	// The advancements pages, a player's advancement, criteria and variants.
	advancements: {
		heading: 'Progresos',
		lead: 'Quién ha conseguido qué.',
		most: 'Más progresos',
		mostLead: 'Cuántos progresos del juego base tiene cada jugador. Las recetas no cuentan.',
		search: 'Buscar progresos',
		empty: 'Nadie ha conseguido todavía este progreso.',
		variants: {
			cat: {
				tabby: 'Atigrado café',
				black: 'Tuxedo',
				red: 'Atigrado rojo',
				siamese: 'Siamés',
				british_shorthair: 'Británico de pelo corto',
				calico: 'Calicó',
				persian: 'Persa',
				ragdoll: 'Ragdoll',
				white: 'Blanco',
				jellie: 'Jellie',
				all_black: 'Negro',
			},
			wolf: {
				pale: 'Pálido',
				ashen: 'Cenizo',
				black: 'Negro',
				chestnut: 'Castaño',
				rusty: 'Oxidado',
				snowy: 'Nevado',
				spotted: 'Manchado',
				striped: 'Rayado',
				woods: 'Boscoso',
			},
			frog: {
				temperate: 'Temperada',
				cold: 'Helada',
				warm: 'Cálida',
			},
			site: {
				desert_pyramid: 'Cepillado en una pirámide del desierto',
				desert_well: 'Cepillado en una fuente del desierto',
				ocean_ruin_cold: 'Cepillado en ruinas oceánicas frías',
				ocean_ruin_warm: 'Cepillado en ruinas oceánicas cálidas',
				trail_ruins_common: 'Cepillado en un sendero en ruinas (botín común)',
				trail_ruins_rare: 'Cepillado en un sendero en ruinas (botín raro)',
				has_sherd: 'Fragmento de cerámica en el inventario',
			},
		},
		player: {
			doneOn: 'Conseguido: {when}',
			notYet: 'Aún no conseguido',
			progress: '{done} de {total} hechos',
			partsDone: 'Hecho',
			partsMissing: 'Falta',
			anyOf: 'Basta con uno de estos:',
			allPlayers: 'Todos los jugadores con este progreso',
		},
		criteria: {
			heart_transplanter: {
				place_creaking_heart_awake: 'Corazón de crepitante colocado (activo)',
				place_creaking_heart_dormant: 'Corazón de crepitante colocado (inactivo)',
				place_pale_oak_log: 'Tronco de roble pálido colocado para completarlo',
			},
			distract_piglin: {
				distract_piglin: 'Objeto de oro lanzado a un piglin',
				distract_piglin_directly: 'Lingote de oro dado a un piglin',
			},
			loot_bastion: {
				loot_bastion_bridge: 'Cofre de un puente',
				loot_bastion_hoglin_stable: 'Cofre de un establo de hoglins',
				loot_bastion_treasure: 'Cofre de una sala del tesoro',
				loot_bastion_other: 'Cofre genérico de bastión',
			},
			root: {
				killed_something: 'Matar algo',
				killed_by_something: 'Morir a manos de algo',
			},
			read_power_of_chiseled_bookshelf: {
				chiseled_bookshelf: 'Estantería colocada junto a un comparador de redstone',
				comparator: 'Comparador de redstone colocado junto a una estantería',
			},
			uh_oh: {
				give_tnt_directly: 'Dinamita dada a un cubo de azufre',
				pick_up_dropped_tnt: 'Un cubo de azufre recogió dinamita soltada',
			},
		},
		reward: 'Recompensa: {xp} puntos de experiencia',
		view: 'Vista',
		viewTiles: 'Casillas',
		viewTree: 'Árbol',
		tabs: 'Pestañas de progresos',
		notFound: 'Este progreso no existe.',
		nobody: 'Nadie tiene todavía ningún progreso.',
		holder: '1 jugador',
		holders: '{count} jugadores',
		hidden: 'Oculto',
		hideHidden: 'Ocultar progresos ocultos',
		hideHiddenHint:
			'Oculta los progresos que el juego mantiene en secreto, para no estropear la sorpresa.',
		doneOf: '{done} de {total}',
	},

	// The compare page.
	compare: {
		heading: 'Comparar jugadores',
		lead: 'Elige a dos jugadores y mira quién va por delante en cada habilidad y estadística.',
		verdict: '{name} gana {wins} de {total} habilidades.',
		verdictLevel: '{wins} habilidades cada uno; {name} va por delante en nivel.',
		verdictEven: 'Empate total: las mismas habilidades y el mismo nivel.',
		skillParts: 'De qué se compone',
		partTarget: '100 con {target}',
		vanilla: 'Estadísticas',
		vanillaEmpty: 'Ninguno de los dos tiene esto todavía.',
		playerA: 'Jugador A',
		playerB: 'Jugador B',
		choose: 'Elegir',
		waiting: 'Esperando estadísticas',
		unselected: 'Sin seleccionar',
		slotA: 'A',
		slotB: 'B',
		needTwo: 'Elige a dos jugadores para compararlos.',
		same: 'Elige a dos jugadores distintos.',
		advancementsDiff: 'Progresos exclusivos',
		onlyAdvancements: 'Solo {name}',
		onlyAdvancementsEmpty: 'Ningún progreso exclusivo',
		otherAdvancements: 'Otros progresos',
	},

	// The 3D skin viewer.
	skin: {
		head: 'Cabeza',
		body: 'Cuerpo',
		actions: 'Controles de la skin',
		poses: {
			idle: 'Quieto',
			walk: 'Andar',
			run: 'Correr',
			fly: 'Volar',
		},
		pause: 'Pausar',
		resume: 'Reanudar',
		autoRotate: 'Girar solo',
		scrollZoom: 'Zoom con la rueda del ratón',
		equipment: {
			cape: 'Capa',
			elytra: 'Élitros',
			none: 'Sin capa',
		},
	},

	// How we rank.
	about: {
		howLink: '¿Cómo se calcula?',
		heading: 'Cómo puntuamos',
		lead:
			'Minecraft no valora lo bien que juegas; solo cuenta lo que haces. Una sola estadística premiaría lo que no es: el tiempo de juego premia quedarse AFK, y las bajas sin más, una granja de criaturas. Por eso el ranking mira siete habilidades y las convierte en un único nivel.',
		farmsHeading: 'Granjas y AFK',
		farms:
			'No ocultamos las granjas: las bajas de granjas de lepismas y de oro aparecen en la clasificación de Bajas hostiles, y los peces, en Peces pescados. Simplemente no deciden el nivel. Quedarse AFK da tiempo de juego, que solo cuenta para Supervivencia, y baja las acciones por hora.',
		rankingsHeading: 'Tres rankings',
		rankingLegends: 'Leyendas: el nivel más alto.',
		rankingVeterans: 'Veteranos: los que más horas llevan en el servidor.',
		tiersHeading: 'Rangos de progresos',
		tiers:
			'Cada página de jugador muestra, con una barra y un rango, qué parte de los progresos del juego base ha conseguido ese jugador. Los rangos llevan nombres de materiales de Minecraft y son solo por diversión: no cuentan para el nivel (Hitos cuenta algunas de las mismas hazañas a partir de las estadísticas).',
		unitsHeading: 'Unidades con sentido',
		units: 'Distancias en km, tiempo en horas, daño en corazones. Nunca centímetros ni ticks.',
		levelHeading: 'El nivel',
		level:
			'Cada habilidad recibe una puntuación: 100 es un jugador muy bueno en esa habilidad, alguien que ha visto y hecho casi todo. El nivel combina las habilidades con pesos fijos, así que nivel 100 significa muy bueno en todo. Como el nivel de experiencia del juego, es un número entero, redondeado hacia abajo.',
		levelOpen:
			'No hay límite superior. Por encima de 100 una habilidad sigue subiendo, pero despacio: el doble que un jugador muy bueno suma solo un poco, así que ser buenísimo en una sola cosa nunca pesa más que ser bueno en todo. Las barras de las páginas de jugador van de 0 a 100 y se quedan llenas por encima.',
		levelOwn:
			'Tu nivel depende solo de lo que has hecho, nunca de los demás jugadores: nadie pierde puntos porque otro haya jugado más. Fuera de Supervivencia nunca baja, porque las estadísticas del juego solo crecen. En caso de empate, va primero quien haya necesitado menos tiempo de juego; después, los jugadores se ordenan alfabéticamente por nombre.',
		partsHeading: 'Variedad y cantidad',
		parts:
			'Cada habilidad se compone de dos tipos de partes. La variedad cuenta cuántas cosas distintas has hecho: tipos de criaturas, biomas, menas, tipos de bloque. Se llena con una cifra fija y nunca la supera, y ninguna granja puede falsearla. La cantidad cuenta cuánto has hecho, en una curva: las primeras cien cuentan mucho, las cien siguientes algo menos. Llega a 100 con lo que tiene un jugador experimentado tras unos cientos de horas.',
		caps:
			'Todo lo que una granja puede hacer sin fin tiene un límite suave: las bajas cuentan enteras hasta 250 por tipo de criatura, las menas hasta 1500 por tipo, los tratos hasta 2000 y los peces hasta 1000. A partir de ahí solo cuenta la raíz cuadrada, así que los 120.000 piglins zombificados de una granja de oro cuentan como unas 600 bajas. Una granja sigue sumando un poco; solo que no puede ganar una habilidad por sí sola.',
		skillsHeading: 'Las siete habilidades',
		skillWeight: '{weight} del nivel',
		dyingHeading: 'Morir',
		dying:
			'Una muerte solo baja Supervivencia, una décima parte del nivel. Cuanto más hayas jugado, menos cuenta una muerte: un jugador experimentado pierde mucho menos de una décima de nivel, algo que normalmente ni se nota. Solo las primeras muertes de un jugador nuevo cuestan cerca de nivel y medio, y las recupera cuando vuelve a jugar sin morir más o menos el mismo tiempo que había jugado antes de esa muerte.',
		rankingGrinders:
			'Grinders: lo que un jugador consigue por hora en línea. Bloques minados y colocados, distancia (un kilómetro cuenta 200, las vagonetas nada) y criaturas hostiles matadas (10 cada una, con el límite de arriba), divididos entre las horas jugadas, a partir de 2 horas de tiempo de juego. El tiempo AFK suma horas pero nada más.',
	},

	// The licenses page.
	licenses: {
		heading: 'Licencias',
		lead: 'Quién hizo lo que compone esta web y con qué licencias se usa.',
		minecraftHeading: 'Minecraft',
		minecraft:
			'Minecraft y los nombres, texturas y textos del juego pertenecen a Mojang y Microsoft. Esta web los muestra conforme a las {guidelines}.',
		guidelines: 'Directrices de uso de Minecraft',
		guidelinesUrl: 'https://www.minecraft.net/es-es/usage-guidelines',
		download:
			'El mod no contiene ningún archivo del juego. Cada servidor descarga las texturas y los textos de Minecraft desde Mojang, como hace el launcher, y dibuja los iconos a partir de ellos.',
		skins: 'Las skins pertenecen a sus jugadores. Se cargan desde Mojang y, para los jugadores de Bedrock, desde GeyserMC.',
		projectHeading: 'Stats WebUI Plus',
		project:
			'{plus} es software libre bajo la licencia MIT.',
		showText: 'Mostrar el texto de la licencia',
		fontHeading: 'Fuentes',
		font:
			'Los textos usan {font} de macimas: una recreación libre de la fuente de Minecraft, no un archivo del juego, bajo la SIL Open Font License 1.1.',
		// The fonts the site heading and page titles use: only those this server uses are named.
		fontBoth:
			'El título del sitio y los títulos de las páginas usan {font} de {author}, también bajo la SIL Open Font License 1.1; su texto de licencia está en la lista de abajo.',
		fontSite:
			'El título del sitio usa {font} de {author}, también bajo la SIL Open Font License 1.1; su texto de licencia está en la lista de abajo.',
		fontTitles:
			'Los títulos de las páginas usan {font} de {author}, también bajo la SIL Open Font License 1.1; su texto de licencia está en la lista de abajo.',
		// The server owner's own font for the site heading; their text about it follows.
		fontCustom: 'El título del sitio usa {name}, una fuente elegida por este servidor:',
		packagesHeading: 'Software de código abierto en esta web',
		packages: 'La web contiene código de estos paquetes. Los mismos avisos en un solo archivo: {file}.',
		loading: 'Cargando…',
		error: 'No se ha podido cargar la lista de licencias.',
	},

	// The server owner's own pages.
	pages: {
		notFoundTitle: 'Página no encontrada',
		notFound: 'Esta página no existe.',
	},
};
