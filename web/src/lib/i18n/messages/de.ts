import type { Messages } from './en';

// Minecraft terms follow the game's own German translation (de_de).
export const de: Messages = {
	// The whole site.
	site: {
		// Any page or part of one whose data could not be loaded.
		loadError: 'Das konnte nicht geladen werden. Versuche es gleich noch einmal.',
		loading: 'Wird geladen …',
		// The slogan in quotes (config siteHeadingSloganStyle "quote"), with this language's quotation marks.
		sloganQuote: '„{text}“',
	},

	// The menu and the links back.
	menu: {
		ranking: 'Rangliste',
		players: 'Spieler',
		leaderboards: 'Bestenlisten',
		advancements: 'Fortschritte',
		compare: 'Vergleichen',
		stats: 'Statistiken',
		about: 'So bewerten wir',
		licenses: 'Lizenzen',
		back: 'Zurück',
		open: 'Menü',
		// The hotbar's last slot, with the pages that don't fit in one row.
		more: 'Mehr',
		links: 'Links',
		top: 'Nach oben',
	},

	// The language and theme switches.
	settings: {
		language: {
			label: 'Sprache',
			es: 'Español',
			en: 'English',
			fr: 'Français',
			de: 'Deutsch',
		},
		theme: {
			label: 'Darstellung',
			system: 'System',
			birch: 'Birke',
			deepslate: 'Tiefenschiefer',
		},
	},

	// The server's status in the header.
	server: {
		online: 'Online',
		offline: 'Offline',
		players: '{count} / {max} Spieler online',
		copy: 'Klicken, um die Adresse zu kopieren',
		copied: 'Kopiert!',
	},

	// The footer.
	footer: {
		credit: '{plus} von NR137, mit KI erstellt · Inspiriert von {original} von borrageiros',
		disclaimer: 'Kein offizielles Minecraft-Produkt. Nicht genehmigt von oder verbunden mit Mojang oder Microsoft.',
		assets: 'Minecraft-Namen, -Texturen und -Texte © Mojang · Schrift: {font} von macimas (OFL)',
	},

	// The start page: the ranking and its podium.
	ranking: {
		heading: 'Rangliste',
		lead: 'Die Besten auf dem Server.',
		nobody: 'Noch niemand',
		rankingEmpty: 'Noch keine Spieler mit Statistiken.',
		search: 'Spieler suchen',
		podium: 'Podium',
	},

	// The player list.
	players: {
		heading: 'Spieler',
		lead: 'Alle, die schon auf dem Server waren, nach Spielzeit.',
		empty: 'Noch keine Spieler mit Statistiken.',
		count: '{count} Spieler',
		countOne: '1 Spieler',
		columns: {
			player: 'Spieler',
			hours: 'Stunden',
			rate: 'Aktionen/h',
			score: 'Level',
			advancements: 'Fortschritte',
		},
	},

	// A player's page.
	player: {
		tiersLink: 'Was sind Stufen?',
		noneAchievedInTab: 'In diesem Tab noch nichts geschafft.',
		notFound: 'Auf diesem Server gibt es keinen Spieler mit diesem Namen.',
		rankOf: 'Platz {rank} von {total}',
		playTime: 'Spielzeit',
		actionsPerHour: 'Aktionen pro Stunde',
		firstJoin: 'Erster Login',
		lastSeen: 'Zuletzt online',
		onlineNow: 'Jetzt online',
		unknown: 'unbekannt',
		firstJoinEstimate: 'Geschätzt aus dem ersten freigeschalteten Fortschritt oder Rezept.',
		lastSeenEstimate: 'Geschätzt aus dem letzten Speichern dieses Spielers durch den Server.',
		tiers: {
			stone: 'Stein',
			copper: 'Kupfer',
			iron: 'Eisen',
			gold: 'Gold',
			diamond: 'Diamant',
			netherite: 'Netherit',
			dragon_egg: 'Drachenei',
		},
		tier: 'Stufe {tier}',
		nextTier: 'Noch {count} bis {tier}',
		allAdvancements: 'Alle Fortschritte erreicht',
		skills: 'Fertigkeiten',
		search: 'Statistiken suchen',
		hideZeros: 'Nullen ausblenden',
		noMatches: 'Nichts passt zu dieser Suche.',
		shown: '{shown} von {total}',
		stats: 'Statistiken',
		sameName: 'Ein anderer Spieler hat denselben Namen:',
		advancements: 'Fortschritte',
		achievedOnly: 'Nur erreichte',
	},

	// The seven skills the level is made of, and the parts each counts.
	skills: {
		combat: {
			name: 'Kampf',
		},
		milestones: {
			name: 'Meilensteine',
		},
		mining: {
			name: 'Bergbau',
		},
		exploration: {
			name: 'Erkundung',
		},
		building: {
			name: 'Bauen',
		},
		farming: {
			name: 'Ackerbau & Viehzucht',
		},
		survival: {
			name: 'Überleben',
		},
		parts: {
			milestones: 'Meilensteine',
			repeats: 'Wiederholungen',
			mob_types: 'Kreaturarten',
			kills: 'Kills',
			biomes: 'Biome',
			distance: 'Strecke',
			ore_types: 'Erzarten',
			ores: 'Erzpunkte',
			block_types: 'Blockarten',
			crafted_types: 'Hergestellte Items',
			placed: 'Gesetzte Blöcke',
			food_types: 'Nahrung',
			animal_types: 'Gezüchtete Tiere',
			farming: 'Arbeit',
			hours_per_death: 'Stunden pro Tod',
			kills_per_death: 'Kills pro Tod',
		},
	},

	// The leaderboards page, a board, and every board by its ID: name, hint (one line), about (how it counts).
	boards: {
		page: {
			heading: 'Bestenlisten',
			lead: 'Wähle eine Bestenliste.',
			allStats: 'Alle Statistiken →',
		},
		board: {
			fallbackTitle: 'Bestenliste',
			notFound: 'Diese Bestenliste gibt es nicht.',
			empty: 'Noch niemand hat diese Statistik. Sei die erste Person.',
		},
		categories: {
			rankings: 'Ranglisten',
			combat: 'Kampf',
			mining: 'Bergbau',
			exploration: 'Erkundung',
			life: 'Serverleben',
			deaths: 'Tode',
			advancements: 'Fortschritte',
			totals: 'Gesamt',
			skills: 'Fertigkeiten',
		},
		legends: {
			name: 'Legenden',
			hint: 'Level 100 ist ein sehr guter Spieler in jeder Fertigkeit.',
			about:
				'Das Level setzt sich aus den sieben Fertigkeiten zusammen: Meilensteine, Kampf, Erkundung, Bergbau, Bauen, Ackerbau & Viehzucht und Überleben. 100 heißt sehr gut in allen; darüber steigt es langsam weiter. „So werten wir“ erklärt jeden Teil.',
		},
		veterans: {
			name: 'Veteranen',
			hint: 'Echte Stunden auf dem Server.',
		},
		grinders: {
			name: 'Grinder',
			hint: 'Abgebaute und gesetzte Blöcke, Strecke und Kills pro gespielter Stunde.',
			about:
				'Was ein Spieler pro Stunde online schafft: abgebaute und gesetzte Blöcke, zurückgelegte Strecke (ein Kilometer zählt 200, Loren gar nicht) und erlegte Feinde (je 10, gedeckelt wie bei Kampf), geteilt durch die gespielten Stunden. Spieler zählen ab 2 Stunden Spielzeit. AFK-Zeit bringt Stunden, aber sonst nichts, und senkt den Wert.',
		},
		advancements: {
			name: 'Fortschritte',
			hint: 'Erreichte Vanilla-Fortschritte. Rezepte zählen nicht.',
		},
		'hostile-kills': {
			name: 'Erlegte Feinde',
			hint: 'Erlegte feindliche Kreaturen.',
			about: 'Jede feindliche Kreatur, Silberfischchen eingeschlossen.',
		},
		'hostile-no-silverfish': {
			name: 'Feinde ohne Silberfischchen',
			hint: 'Erlegte feindliche Kreaturen, ohne Silberfischchen.',
			about: 'Jede feindliche Kreatur außer Silberfischchen, die Farmen zu Tausenden töten.',
		},
		'other-mobs': {
			name: 'Andere Kreaturen',
			hint: 'Erlegte Kreaturen, die nicht feindlich sind.',
			about:
				'Jede Kreatur, die nicht feindlich ist: Tiere, Fische, Tintenfische, Fledermäuse, Dorfbewohner, Golems, … Spieler haben ihre eigene Bestenliste.',
		},
		bosses: {
			name: 'Bosse',
			hint: 'Enderdrache, Wither, Wärter und Großwächter.',
		},
		'player-kills': {
			name: 'Getötete Spieler',
			hint: 'Getötete Spieler.',
		},
		deaths: {
			name: 'Tode insgesamt',
			hint: 'Wie oft du gestorben bist. Die meisten zuerst; im Vergleich gewinnt, wer weniger hat.',
		},
		'kills-per-hour': {
			name: 'Erlegt pro Stunde',
			hint: 'Erlegte Kreaturen pro gespielter Stunde.',
			about: 'Alle erlegten Kreaturen, Farmen eingeschlossen, geteilt durch die gespielten Stunden.',
		},
		'blocks-mined': {
			name: 'Abgebaute Blöcke',
			hint: 'Abgebaute Blöcke insgesamt.',
		},
		ores: {
			name: 'Erze',
			hint: 'Abgebaute Erze.',
		},
		'diamonds-mined': {
			name: 'Abgebaute Diamanten',
			hint: 'Abgebautes Diamanterz und Tiefenschiefer-Diamanterz.',
		},
		'iron-mined': {
			name: 'Abgebautes Eisen',
			hint: 'Abgebautes Eisenerz und Tiefenschiefer-Eisenerz.',
		},
		'coal-mined': {
			name: 'Abgebaute Kohle',
			hint: 'Abgebaute Steinkohle und Tiefenschiefer-Steinkohle (die Erze).',
		},
		'copper-mined': {
			name: 'Abgebautes Kupfer',
			hint: 'Abgebautes Kupfererz und Tiefenschiefer-Kupfererz.',
		},
		'gold-mined': {
			name: 'Abgebautes Gold',
			hint: 'Abgebautes Golderz, Tiefenschiefer-Golderz und Nethergolderz.',
		},
		'redstone-mined': {
			name: 'Abgebautes Redstone',
			hint: 'Abgebautes Redstone-Erz und Tiefenschiefer-Redstone-Erz.',
		},
		'lapis-mined': {
			name: 'Abgebauter Lapislazuli',
			hint: 'Abgebautes Lapislazulierz und Tiefenschiefer-Lapislazulierz.',
		},
		'emerald-mined': {
			name: 'Abgebaute Smaragde',
			hint: 'Abgebautes Smaragderz und Tiefenschiefer-Smaragderz.',
		},
		'quartz-mined': {
			name: 'Abgebauter Netherquarz',
			hint: 'Abgebautes Netherquarzerz.',
		},
		'deaths-per-hour': {
			name: 'Tode pro Stunde',
			hint: 'Tode pro gespielter Stunde. Die meisten zuerst; im Vergleich gewinnt, wer weniger hat.',
			about:
				'Tode geteilt durch die gespielten Stunden. Die meisten stehen oben; im Vergleich zweier Spieler gewinnt, wer weniger Tode pro Stunde hat.',
		},
		'ancient-debris': {
			name: 'Antiker Schrott',
			hint: 'Abgebauter antiker Schrott.',
		},
		'logs-mined': {
			name: 'Stämme',
			hint: 'Abgebaute Stämme.',
		},
		'tools-broken': {
			name: 'Verbrauchte Gegenstände',
			hint: 'Insgesamt verbrauchte Gegenstände: Werkzeuge, Waffen, Rüstung, … mit aufgebrauchter Haltbarkeit.',
			about:
				'Gegenstände, deren Haltbarkeit in der Hand oder am Körper aufgebraucht war: Werkzeuge, Waffen, Rüstung, Elytren, Schilde, Angeln, …',
		},
		'distance-total': {
			name: 'Zurückgelegte Strecke',
			hint: 'Zurückgelegte Strecke in km, ohne Fliegen und Fallen.',
			about:
				'Jede Art der Fortbewegung zählt: Gehen, Sprinten, Schwimmen, Boote, Elytren, Pferde, glückliche Ghasts, Nautilusse, Loren, Schreiter, Klettern, Schleichen und Gehen auf oder unter Wasser. Fliegen im Kreativmodus und Fallen nicht.',
		},
		'items-dropped': {
			name: 'Fallen gelassen',
			hint: 'Insgesamt fallen gelassene Gegenstände.',
		},
		'items-picked-up': {
			name: 'Aufgesammelt',
			hint: 'Insgesamt aufgesammelte Gegenstände.',
		},
		'items-used': {
			name: 'Benutzte Gegenstände',
			hint: 'Insgesamt benutzte Gegenstände: platziert, gegessen, geschwungen, geworfen, …',
			about:
				'Alles, was das Spiel als Benutzen eines Gegenstands zählt: Blöcke platzieren, essen und trinken, Werkzeuge an Blöcken, Waffen an Kreaturen, werfen, Eimer, … und Gegenstände in einen verzierten Krug legen.',
		},
		'items-crafted': {
			name: 'Hergestellte Gegenstände',
			hint: 'Insgesamt hergestellte Gegenstände: Werkbank, Ofen, Schmiedetisch, Steinsäge und Handel.',
			about:
				'Alles, was das Spiel als Herstellen zählt: Handwerksfeld und Werkbank, Öfen, Schmiedetisch, Steinsäge, Kartentisch und Handel mit Dorfbewohnern. Jeder Gegenstand zählt, 4 auf einmal hergestellte Bretter also 4.',
		},
		'blocks-placed': {
			name: 'Platzierte Blöcke',
			hint: 'Platzierte Blöcke (das Spiel zählt das Platzieren als Benutzen).',
			about:
				'Das Spiel zählt platzierte Blöcke nicht eigens; das Platzieren zählt es als Benutzen des Gegenstands. Diese Bestenliste zählt zusammen, wie oft alle Block-Gegenstände benutzt wurden, was fast nur Platzieren ist (Pflanzen von Samen zählt auch: Sie werden als Pflanze platziert).',
		},
		'mob-types': {
			name: 'Kreaturenarten',
			hint: 'Wie viele verschiedene Kreaturenarten erlegt wurden.',
			about:
				'Jede Kreaturenart zählt einmal, sobald der Spieler eine erlegt hat: feindliche, neutrale und friedliche gleichermaßen.',
		},
		'kills-per-death': {
			name: 'Erlegt pro Tod',
			hint:
				'Erlegte feindliche Kreaturen (ohne Silberfischchen) pro Tod; ohne bisherigen Tod die erlegten selbst.',
			about:
				'Erlegte feindliche Kreaturen (ohne Silberfischchen) geteilt durch die Tode. Wer nie gestorben ist, bekommt seine Erlegten als Wert, als wäre er einmal gestorben.',
		},
		'distance-per-hour': {
			name: 'Strecke pro Stunde',
			hint: 'Zurückgelegte Strecke pro gespielter Stunde.',
			about: 'Die zurückgelegte Strecke geteilt durch die gespielten Stunden: Wer online am meisten herumkommt.',
		},
		'food-eaten': {
			name: 'Gegessene Nahrung',
			hint: 'Gegessene Nahrung, Kuchenstücke eingeschlossen.',
			about: 'Alles Gegessene: jedes Nahrungsmittel und die Stücke eines aufgestellten Kuchens.',
		},
		'current-life': {
			name: 'Aktuelle Lebenszeit',
			hint: 'Spielzeit seit dem letzten Tod (das Spiel zählt sie nur, solange man online ist).',
			about:
				'Wie lange der Spieler seit seinem letzten Tod lebt, in Spielzeit: Das Spiel zählt sie nur, solange er online ist, und beginnt mit jedem Tod wieder bei null. Wer nie gestorben ist, zählt ab seinem ersten Beitritt.',
		},
		'distance-walk-sprint': {
			name: 'Gehen und Sprinten',
			hint: 'Strecke gelaufen und gesprintet.',
		},
		'distance-elytra': {
			name: 'Elytren',
			hint: 'Strecke mit Elytren.',
		},
		'play-time': {
			name: 'Spielzeit',
			hint: 'Echte Zeit auf dem Server.',
		},
		'skill-milestones': {
			name: 'Meilensteine',
			hint: 'Bosse, Überfälle, seltene Funde und Items; jedes zählt einmal voll.',
			about:
				'Die großen Momente einer Welt: den Enderdrachen, den Wither, den Wärter und einen Großwächter besiegen; einen Überfall gewinnen; Elytren, einen Dreizack, das Drachenei, ein Herz des Meeres, einen Wither-Skelettschädel und ein Schnüffler-Ei finden; ein Leuchtfeuer, Netherit, einen Aquisator und einen Streitkolben herstellen; von einem Totem der Unsterblichkeit gerettet werden; einen verzauberten goldenen Apfel essen. Jedes zählt beim ersten Mal voll (drei Viertel der Fertigkeit; 100 der 118 Punkte füllen sie). Einen Boss erneut besiegen oder ein weiteres Leuchtfeuer, Netheritbarren, einen Aquisator oder Streitkolben herstellen bringt etwas mehr (ein Viertel), mit jeder Wiederholung weniger. Überfälle, Totems und Funde zählen nur einmal, eine Überfallfarm oder dieselben Elytren erneut aufheben bringt also nichts.',
		},
		'skill-combat': {
			name: 'Kampf',
			hint: 'Feindliche Kreaturarten und Kills, pro Art gedeckelt.',
			about:
				'Erlegte feindliche Kreaturarten, von 30 (die Hälfte der Fertigkeit), und Kills feindlicher Kreaturen (die Hälfte, 100 bei 4.000). Jede Art zählt bis 250 Kills voll, danach nur die Wurzel des Rests, eine Farm bringt also wenig: 120.000 Zombifizierte Piglins zählen wie etwa 600 Kills. Tiere, Fische und Dorfbewohner zählen nicht.',
		},
		'skill-exploration': {
			name: 'Erkundung',
			hint: 'Besuchte Biome und zurückgelegte Strecke.',
			about:
				'Besuchte Biome, von 50 (die Hälfte der Fertigkeit): die Oberwelt-Biome des Fortschritts „Abenteuerzeit“ und die Nether-Biome von „Heißbegehrte Reiseziele“. Dazu die zurückgelegte Strecke (die Hälfte, 100 bei 5.000 km): Gehen, Sprinten, Schwimmen, Boote, Elytren, Pferde und andere Reittiere, Klettern. Loren zählen ein Fünftel, weil eine Lorenschleife auch läuft, während man weg ist. Kreativflug und Fallen zählen nicht.',
		},
		'skill-mining': {
			name: 'Bergbau',
			hint: 'Erzarten und Erze, je seltener, desto mehr wert.',
			about:
				'Abgebaute Erzarten, von 10 (30 % der Fertigkeit), und die Erze selbst (70 %, 100 bei 12.000 Punkten), je seltener, desto mehr wert: Antiker Schrott 15, Diamant 8, Smaragd 6, Gold 3, Eisen 2, Lapislazuli 1,5, Netherquarz 1,2, Kupfer 1, Redstone 0,8, Kohle 0,25. Jedes Erz zählt bis 1.500 Blöcke voll, danach nur die Wurzel des Rests. Stein, Erde und Tiefenschiefer zählen nicht.',
		},
		'skill-building': {
			name: 'Bauen',
			hint: 'Gesetzte Blockarten, hergestellte Items und gesetzte Blöcke.',
			about:
				'Gesetzte Blockarten, von 400 (35 % der Fertigkeit), hergestellte Item-Arten, von 250 (15 %), und gesetzte Blöcke (die Hälfte, 100 bei 300.000). Das Spiel zählt das Setzen eines Blocks als Benutzen seines Items, und das wird zusammengezählt. Viele verschiedene Blöcke zählen mehr als sehr viel von einem.',
		},
		'skill-farming': {
			name: 'Ackerbau & Viehzucht',
			hint: 'Nahrung, Zucht, Handel, Fische und Ernte.',
			about:
				'Gegessene Nahrung, von 25 Sorten (ein Viertel der Fertigkeit), gezüchtete Tierarten, von 15 (ein Viertel, aus dem Fortschritt „Paarweise“), und die Arbeit (die Hälfte, 100 bei 5.000): jedes gezüchtete Tier, Handel mit Dorfbewohnern (bis 2.000 voll), gefangene Fische (bis 1.000 voll) und geerntete Pflanzen (20 zählen 1).',
		},
		'skill-survival': {
			name: 'Überleben',
			hint: 'Gespielte Stunden und Kills pro Tod.',
			about:
				'Am Leben bleiben: gespielte Stunden pro Tod (die Hälfte der Fertigkeit, 100 bei 8 Stunden) und erlegte Feinde pro Tod (die Hälfte, 100 bei 100). Beide teilen durch die Tode plus eins, wer nie gestorben ist, zählt also, als wäre er einmal gestorben. Diese Fertigkeit endet bei 100.',
		},
	},

	// Units of the boards: name and hint.
	units: {
		hours: {
			name: 'Stunden',
			hint: 'Echte Zeit auf dem Server.',
		},
		kills: {
			name: 'Erlegt',
			hint: 'Erlegte Kreaturen oder getötete Spieler.',
		},
		count: {
			name: 'Anzahl',
			hint: 'Wie oft es passiert ist.',
		},
		hearts: {
			name: 'Herzen',
			hint: 'Schaden in Herzen.',
		},
		kills_per_hour: {
			name: 'Erlegt/h',
			hint: 'Erlegte Kreaturen pro gespielter Stunde.',
		},
		deaths_per_hour: {
			name: 'Tode/h',
			hint: 'Tode pro gespielter Stunde.',
		},
		blocks: {
			name: 'Blöcke',
			hint: 'Anzahl der Blöcke.',
		},
		km: {
			name: 'km',
			hint: 'Zurückgelegte Strecke in Kilometern.',
		},
		km_per_hour: {
			name: 'km/h',
			hint: 'Kilometer pro gespielter Stunde.',
		},
		mob_types: {
			name: 'Kreaturenarten',
			hint: 'Verschiedene Kreaturenarten.',
		},
		ratio: {
			name: 'Erlegt/Tod',
			hint: 'Erlegte Kreaturen pro Tod.',
		},
		level: {
			name: 'Level',
			hint: 'Das Ranglisten-Level: 100 ist ein sehr guter Spieler in jeder Fertigkeit.',
		},
		skill: {
			name: 'Punkte',
			hint: '100 ist ein sehr guter Spieler in dieser Fertigkeit; darüber steigt der Wert langsam weiter.',
		},
		actions_per_hour: {
			name: 'Aktionen/h',
			hint: 'Abgebaute und gesetzte Blöcke, Strecke und Kills pro gespielter Stunde.',
		},
	},

	// The statistics page and the game's statistic groups.
	stats: {
		groupTabs: 'Statistikgruppen',
		groups: {
			custom: {
				short: 'Allgemein',
				name: 'Allgemeine Statistiken',
				hint: '{name}.',
			},
			killed: {
				short: 'Getötet',
				name: 'Erlegte Kreaturen',
				hint: 'Erlegt: {name}.',
			},
			killed_by: {
				short: 'Tode',
				name: 'Tode durch Kreaturen',
				hint: 'Getötet worden von: {name}.',
			},
			mined: {
				short: 'Abgebaut',
				name: 'Abgebaute Blöcke',
				hint: 'Abgebaut: {name}.',
			},
			crafted: {
				short: 'Hergestellt',
				name: 'Hergestellt',
				hint: 'Hergestellt: {name}.',
			},
			used: {
				short: 'Benutzt',
				name: 'Benutzt',
				hint: 'Benutzt: {name}.',
			},
			broken: {
				short: 'Verbraucht',
				name: 'Verbraucht',
				hint: 'Verbraucht: {name}.',
			},
			picked_up: {
				short: 'Aufgesammelt',
				name: 'Aufgesammelt',
				hint: 'Aufgesammelt: {name}.',
			},
			dropped: {
				short: 'Fallen gelassen',
				name: 'Fallen gelassen',
				hint: 'Fallen gelassen: {name}.',
			},
		},
		lead: 'Alles, was Minecraft zählt. Wähle eine für ihre Bestenliste.',
		heading: 'Statistiken',
	},

	// The advancements pages, a player's advancement, criteria and variants.
	advancements: {
		heading: 'Fortschritte',
		lead: 'Wer hat was erreicht.',
		most: 'Meiste Fortschritte',
		mostLead: 'Wie viele Vanilla-Fortschritte jeder Spieler hat. Rezepte zählen nicht.',
		search: 'Fortschritte suchen',
		empty: 'Diesen Fortschritt hat noch niemand erreicht.',
		variants: {
			cat: {
				tabby: 'Getigert',
				black: 'Schwarz',
				red: 'Rot',
				siamese: 'Siam',
				british_shorthair: 'Britisch-Kurzhaar',
				calico: 'Calico',
				persian: 'Perser',
				ragdoll: 'Ragdoll',
				white: 'Weiß',
				jellie: 'Jellie',
				all_black: 'Panther',
			},
			wolf: {
				pale: 'Blass',
				ashen: 'Aschgrau',
				black: 'Schwarz',
				chestnut: 'Kastanienbraun',
				rusty: 'Rostfarben',
				snowy: 'Schnee',
				spotted: 'Gefleckt',
				striped: 'Gestreift',
				woods: 'Wald',
			},
			frog: {
				temperate: 'Gemäßigt',
				cold: 'Kalt',
				warm: 'Warm',
			},
			site: {
				desert_pyramid: 'Im Wüstentempel gepinselt',
				desert_well: 'Am Wüstenbrunnen gepinselt',
				ocean_ruin_cold: 'In kalter Ozeanruine gepinselt',
				ocean_ruin_warm: 'In warmer Ozeanruine gepinselt',
				trail_ruins_common: 'In Pfadruine gepinselt (gewöhnliche Beute)',
				trail_ruins_rare: 'In Pfadruine gepinselt (seltene Beute)',
				has_sherd: 'Töpferscherbe im Inventar',
			},
		},
		player: {
			doneOn: 'Geschafft: {when}',
			notYet: 'Noch nicht geschafft',
			progress: '{done} von {total} geschafft',
			partsDone: 'Geschafft',
			partsMissing: 'Fehlt noch',
			anyOf: 'Eines davon reicht:',
			allPlayers: 'Alle Spieler mit diesem Fortschritt',
		},
		criteria: {
			heart_transplanter: {
				place_creaking_heart_awake: 'Knarzherz gesetzt (aktiv)',
				place_creaking_heart_dormant: 'Knarzherz gesetzt (ruhend)',
				place_pale_oak_log: 'Blasseichenstamm dazugesetzt',
			},
			distract_piglin: {
				distract_piglin: 'Goldgegenstand einem Piglin zugeworfen',
				distract_piglin_directly: 'Goldbarren einem Piglin gegeben',
			},
			loot_bastion: {
				loot_bastion_bridge: 'Truhe in einer Brücke',
				loot_bastion_hoglin_stable: 'Truhe in einem Hoglinstall',
				loot_bastion_treasure: 'Truhe in einer Schatzkammer',
				loot_bastion_other: 'Allgemeine Bastionstruhe',
			},
			root: {
				killed_something: 'Etwas getötet',
				killed_by_something: 'Von etwas getötet',
			},
			read_power_of_chiseled_bookshelf: {
				chiseled_bookshelf: 'Gearbeitetes Bücherregal neben einen Redstone-Komparator gesetzt',
				comparator: 'Redstone-Komparator neben ein gearbeitetes Bücherregal gesetzt',
			},
			uh_oh: {
				give_tnt_directly: 'TNT einem Schwefelwürfel gegeben',
				pick_up_dropped_tnt: 'Schwefelwürfel hat fallengelassenes TNT aufgesammelt',
			},
		},
		reward: 'Belohnung: {xp} Erfahrungspunkte',
		view: 'Ansicht',
		viewTiles: 'Kacheln',
		viewTree: 'Baum',
		tabs: 'Fortschritt-Tabs',
		notFound: 'Diesen Fortschritt gibt es nicht.',
		nobody: 'Noch niemand hat Fortschritte.',
		holder: '1 Spieler',
		holders: '{count} Spieler',
		hidden: 'Versteckt',
		hideHidden: 'Versteckte Fortschritte ausblenden',
		hideHiddenHint:
			'Blendet die Fortschritte aus, die das Spiel geheim hält, damit sie die Überraschung nicht verderben.',
		doneOf: '{done} von {total}',
	},

	// The compare page.
	compare: {
		heading: 'Spieler vergleichen',
		lead: 'Wähle zwei Spieler und sieh, wer in jeder Fertigkeit und Statistik vorne liegt.',
		verdict: '{name} gewinnt {wins} von {total} Fertigkeiten.',
		verdictLevel: 'Je {wins} Fertigkeiten; {name} liegt beim Level vorne.',
		verdictEven: 'Völlig gleichauf: dieselben Fertigkeiten und dasselbe Level.',
		skillParts: 'Woraus es sich zusammensetzt',
		partTarget: '100 bei {target}',
		vanilla: 'Statistiken',
		vanillaEmpty: 'Das hat noch keiner der beiden.',
		playerA: 'Spieler A',
		playerB: 'Spieler B',
		choose: 'Auswählen',
		waiting: 'Warte auf Statistiken',
		unselected: 'Nicht ausgewählt',
		slotA: 'A',
		slotB: 'B',
		needTwo: 'Wähle zwei Spieler aus, um sie zu vergleichen.',
		same: 'Wähle zwei verschiedene Spieler aus.',
		advancementsDiff: 'Einzigartige Fortschritte',
		onlyAdvancements: 'Nur {name}',
		onlyAdvancementsEmpty: 'Keine einzigartigen Fortschritte',
		otherAdvancements: 'Weitere Fortschritte',
	},

	// The 3D skin viewer.
	skin: {
		head: 'Kopf',
		body: 'Körper',
		actions: 'Skin-Steuerung',
		poses: {
			idle: 'Stehen',
			walk: 'Gehen',
			run: 'Rennen',
			fly: 'Fliegen',
		},
		pause: 'Pause',
		resume: 'Fortsetzen',
		autoRotate: 'Automatisch drehen',
		scrollZoom: 'Mit Mausrad zoomen',
		equipment: {
			cape: 'Umhang',
			elytra: 'Elytren',
			none: 'Kein Umhang',
		},
	},

	// How we rank.
	about: {
		howLink: 'Wie wird das berechnet?',
		heading: 'So werten wir',
		lead:
			'Minecraft hat keine Spielstärke-Wertung; es zählt nur, was man tut. Eine einzelne Statistik würde das Falsche belohnen: Spielzeit belohnt AFK-Stehen, reine Kills eine Mobfarm. Deshalb schaut die Rangliste auf sieben Fertigkeiten und macht daraus ein Level.',
		farmsHeading: 'Farmen und AFK',
		farms:
			'Wir verstecken Farmen nicht: Kills von Silberfischchen und Goldfarmen stehen in der Rangliste Erlegte Feinde, Fische bei Gefangene Fische. Sie entscheiden nur nicht über das Level. AFK-Stehen bringt Spielzeit, die nur bei Überleben zählt, und senkt die Aktionen pro Stunde.',
		rankingsHeading: 'Drei Ranglisten',
		rankingLegends: 'Legenden: das höchste Level.',
		rankingVeterans: 'Veteranen: die meisten Stunden auf dem Server.',
		tiersHeading: 'Fortschritts-Stufen',
		tiers:
			'Jede Spielerseite zeigt als Balken mit einer Stufe, welcher Anteil der Vanilla-Fortschritte erreicht wurde. Die Stufen sind nach Minecraft-Materialien benannt und nur zum Spaß: Für das Level zählen sie nicht (Meilensteine zählt einige derselben Taten aus den Statistiken).',
		unitsHeading: 'Verständliche Einheiten',
		units: 'Strecken in km, Zeit in Stunden, Schaden in Herzen. Niemals Zentimeter oder Ticks.',
		levelHeading: 'Das Level',
		level:
			'Jede Fertigkeit bekommt einen Wert: 100 ist ein sehr guter Spieler in dieser Fertigkeit, jemand, der das meiste davon gesehen und gemacht hat. Das Level mischt die Fertigkeiten mit festen Gewichten, Level 100 heißt also sehr gut in allem. Wie das Erfahrungslevel im Spiel ist es eine ganze Zahl, abgerundet.',
		levelOpen:
			'Es gibt keine Obergrenze. Über 100 steigt eine Fertigkeit weiter, aber langsam: doppelt so viel wie ein sehr guter Spieler bringt nur ein wenig, extrem gut in einer Sache wiegt also nie auf, in allem gut zu sein. Die Balken auf den Spielerseiten gehen von 0 bis 100 und bleiben darüber voll.',
		levelOwn:
			'Dein Level hängt nur davon ab, was du getan hast, nie von den anderen Spielern: Niemand verliert Punkte, weil jemand anderes mehr gespielt hat. Außerhalb von Überleben sinkt es nie, denn die Statistiken des Spiels wachsen nur. Bei Gleichstand kommt zuerst, wer weniger Spielzeit brauchte, dann entscheidet der Name.',
		partsHeading: 'Breite und Tiefe',
		parts:
			'Jede Fertigkeit besteht aus zwei Arten von Teilen. Breite zählt, wie viele verschiedene Dinge du getan hast: Kreaturarten, Biome, Erze, Blockarten. Sie ist bei einer festen Zahl voll und geht nie darüber, und keine Farm kann sie vortäuschen. Tiefe zählt, wie viel du getan hast, auf einer Kurve: die ersten hundert zählen viel, die nächsten hundert etwas weniger. 100 erreicht sie bei dem, was ein erfahrener Spieler nach einigen hundert Stunden hat.',
		caps:
			'Was eine Farm endlos tun kann, ist weich gedeckelt: Kills zählen bis 250 pro Kreaturart voll, Erze bis 1.500 pro Sorte, Handel bis 2.000 und Fische bis 1.000. Darüber zählt nur die Wurzel, die 120.000 Zombifizierten Piglins einer Goldfarm zählen also wie etwa 600 Kills. Eine Farm bringt noch ein wenig; sie gewinnt nur keine Fertigkeit allein.',
		skillsHeading: 'Die sieben Fertigkeiten',
		skillWeight: '{weight} des Levels',
		dyingHeading: 'Sterben',
		dying:
			'Ein Tod senkt nur Überleben, ein Zehntel des Levels. Je länger du gespielt hast, desto weniger zählt ein Tod: Ein erfahrener Spieler verliert weit unter ein Zehntel Level, was meist gar nicht sichtbar ist. Nur die ersten Tode eines neuen Spielers kosten etwa anderthalb Level, und die sind nach etwa genauso langer Zeit ohne Tod wieder da.',
		rankingGrinders:
			'Grinder: was ein Spieler pro Stunde online schafft. Abgebaute und gesetzte Blöcke, Strecke (ein Kilometer zählt 200, Loren gar nicht) und erlegte Feinde (je 10, gedeckelt wie oben), geteilt durch die gespielten Stunden, ab 2 Stunden Spielzeit. AFK-Zeit bringt Stunden, aber sonst nichts.',
	},

	// The licenses page.
	licenses: {
		heading: 'Lizenzen',
		lead: 'Wer gemacht hat, woraus diese Website besteht, und unter welchen Lizenzen es genutzt wird.',
		minecraftHeading: 'Minecraft',
		minecraft:
			'Minecraft und die Namen, Texturen und Texte des Spiels gehören Mojang und Microsoft. Diese Website zeigt sie nach den {guidelines}.',
		guidelines: 'Minecraft-Nutzungsrichtlinien',
		guidelinesUrl: 'https://www.minecraft.net/de-de/usage-guidelines',
		download:
			'Die Mod enthält keine Dateien des Spiels. Jeder Server lädt die Texturen und Texte von Minecraft bei Mojang herunter, wie es der Launcher tut, und zeichnet daraus die Symbole.',
		skins: 'Skins gehören ihren Spielern. Sie werden von Mojang geladen, für Bedrock-Spieler von GeyserMC.',
		projectHeading: 'Stats WebUI Plus',
		project:
			'{plus} ist freie Software unter der MIT-Lizenz.',
		showText: 'Lizenztext anzeigen',
		fontHeading: 'Schriften',
		font:
			'Die Texte nutzen {font} von macimas: eine freie Nachbildung der Minecraft-Schrift, keine Datei aus dem Spiel, unter der SIL Open Font License 1.1.',
		// The fonts the site heading and page titles use: only those this server uses are named.
		fontBoth:
			'Die Überschrift der Seite und die Seitentitel nutzen {font} von {author}, ebenfalls unter der SIL Open Font License 1.1; der Lizenztext steht in der Liste unten.',
		fontSite:
			'Die Überschrift der Seite nutzt {font} von {author}, ebenfalls unter der SIL Open Font License 1.1; der Lizenztext steht in der Liste unten.',
		fontTitles:
			'Die Seitentitel nutzen {font} von {author}, ebenfalls unter der SIL Open Font License 1.1; der Lizenztext steht in der Liste unten.',
		// The server owner's own font for the site heading; their text about it follows.
		fontCustom: 'Die Überschrift der Seite nutzt {name}, eine von diesem Server gewählte Schrift:',
		packagesHeading: 'Open-Source-Software in dieser Website',
		packages: 'Die Website enthält Code dieser Pakete. Dieselben Hinweise als eine Datei: {file}.',
		loading: 'Wird geladen …',
		error: 'Die Lizenzliste konnte nicht geladen werden.',
	},

	// The server owner's own pages.
	pages: {
		notFoundTitle: 'Seite nicht gefunden',
		notFound: 'Diese Seite gibt es nicht.',
	},
};
