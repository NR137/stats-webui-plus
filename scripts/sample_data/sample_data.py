"""Sample players for the screenshots: stats, advancements, session times and names in a dev server folder.

Writes nine made-up players with different play styles into <server>/world (stats, advancements, the mod's session
log) and <server>/usercache.json, and points the server's config at the screenshot setup (port 25590, example
address, Map and Discord buttons). Each player's UUID is chosen so Minecraft gives them a different default skin.
Advancements come from the client JAR, so progress is plausible: a child only after its parent, early steps early.

Usage: python3 scripts/sample_data/sample_data.py [server folder] [client JAR]
Defaults: fabric/runs/server and scripts/.cache/minecraft/<version>/minecraft-client.jar (written by the build).
"""
import json
import random
import re
import sys
import uuid
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERSION = re.search(r'^minecraft_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M).group(1).strip()
server = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'fabric' / 'runs' / 'server'
jar = zipfile.ZipFile(sys.argv[2] if len(sys.argv) > 2 else ROOT / 'scripts' / '.cache' / 'minecraft' / VERSION / 'minecraft-client.jar')
# A fixed seed: the same players, stats and progress every time; only the dates move along with today.
rng = random.Random(137)
DATA_VERSION = json.loads(jar.read('version.json'))['world_version']
NOW = datetime.now(timezone.utc).astimezone().replace(microsecond=0)
SKINS = ['alex', 'ari', 'efe', 'kai', 'makena', 'noor', 'steve', 'sunny', 'zuri']


def skin_index(u: uuid.UUID) -> int:
	"""Java's Math.floorMod(UUID.hashCode(), 18), as Minecraft's DefaultPlayerSkin picks the skin."""
	msb, lsb = u.int >> 64, u.int & (2**64 - 1)
	hilo = msb ^ lsb
	to32 = lambda v: ((v & 0xFFFFFFFF) ^ 0x80000000) - 0x80000000
	return to32(to32(hilo >> 32) ^ to32(hilo)) % 18


def uuid_for(skin: str, slim: bool) -> uuid.UUID:
	want = SKINS.index(skin) + (0 if slim else 9)
	while True:
		u = uuid.UUID(int=rng.getrandbits(128), version=4)
		if skin_index(u) == want:
			return u


# name, skin, slim, play hours, days since first join, hours since last online, advancement share, tab weights, style
PLAYERS = [
	('NetherNomadNix', 'zuri', False, 312, 118, 5, 0.86, {'nether': 3, 'end': 3, 'adventure': 1.5}, 'nether'),
	('DeepslateDan', 'steve', False, 268, 112, 20, 0.58, {'story': 3, 'nether': 1.2}, 'miner'),
	('BlockSmithBea', 'alex', True, 241, 116, 2, 0.46, {'story': 2, 'husbandry': 1}, 'builder'),
	('CompassCora', 'noor', True, 196, 97, 30, 0.72, {'adventure': 3, 'husbandry': 1.5}, 'explorer'),
	('CreeperBaneKat', 'kai', False, 174, 90, 9, 0.61, {'adventure': 3, 'nether': 1.5, 'end': 1.5}, 'fighter'),
	('PistonPia', 'ari', True, 151, 84, 50, 0.41, {'story': 2, 'nether': 1}, 'redstone'),
	('HarvestHollis', 'sunny', False, 122, 76, 70, 0.37, {'husbandry': 4}, 'farmer'),
	('TradingTilly', 'makena', True, 93, 61, 26, 0.31, {'adventure': 2, 'husbandry': 2}, 'trader'),
	('FreshSpawnFinn', 'efe', False, 4.5, 3, 15, 0.07, {'story': 5}, 'newcomer'),
]


def advancements():
	"""Vanilla advancements (no recipes) from the JAR: id -> (parent, criteria, requirements)."""
	found = {}
	prefix = 'data/minecraft/advancement/'
	for name in jar.namelist():
		if not name.startswith(prefix) or not name.endswith('.json') or name.startswith(prefix + 'recipes/'):
			continue
		data = json.loads(jar.read(name))
		criteria = list(data.get('criteria', {}))
		requirements = data.get('requirements') or [[c] for c in criteria]
		found['minecraft:' + name[len(prefix):-5]] = (data.get('parent'), criteria, requirements)
	return found


ADVANCEMENTS = advancements()


def ordered():
	done, order = set(), []
	def visit(adv):
		if adv in done:
			return
		parent = ADVANCEMENTS[adv][0]
		if parent in ADVANCEMENTS:
			visit(parent)
		done.add(adv)
		order.append(adv)
	for adv in sorted(ADVANCEMENTS):
		visit(adv)
	return order


ORDER = ordered()


def pick_advancements(share, weights):
	# Everyone works through the main story first; the play style only shifts the rest.
	weights = {'story': 4, **weights} if 'story' not in weights else {**weights, 'story': max(4, weights['story'])}
	target = max(1, round(share * len(ADVANCEMENTS)))
	chosen = set()
	while len(chosen) < target:
		open_ones = [a for a in ORDER if a not in chosen and (ADVANCEMENTS[a][0] is None or ADVANCEMENTS[a][0] in chosen)]
		if not open_ones:
			break
		tab = lambda a: a.split(':')[1].split('/')[0]
		roots = [a for a in open_ones if ADVANCEMENTS[a][0] is None]
		pool = roots or open_ones
		# A tab first, then something open in it: a tab with many open side branches doesn't crowd out a chain.
		tabs = sorted({tab(a) for a in pool})
		picked = rng.choices(tabs, weights=[weights.get(t, 0.6) for t in tabs])[0]
		chosen.add(rng.choice([a for a in pool if tab(a) == picked]))
	return [a for a in ORDER if a in chosen]


def stamp(moment: datetime) -> str:
	return moment.strftime('%Y-%m-%d %H:%M:%S %z')


def depth(adv):
	parent = ADVANCEMENTS[adv][0]
	return 0 if parent not in ADVANCEMENTS else 1 + depth(parent)


def advancement_file(chosen, first, last):
	# Early steps come early: order by depth in the tree (with some shuffle), then hand out the sorted moments.
	moments = sorted(first + (last - first) * rng.random() ** 1.6 for _ in chosen)
	progress = sorted(chosen, key=lambda adv: depth(adv) + rng.uniform(0, 2.5))
	# A parent always before its children.
	when = dict(zip(progress, moments))
	for adv in ORDER:
		parent = ADVANCEMENTS[adv][0]
		if adv in when and parent in when and when[adv] <= when[parent]:
			when[adv] = when[parent] + timedelta(minutes=rng.randint(5, 600))
	body = {}
	for adv in chosen:
		moment = min(when[adv], last)
		_, criteria, requirements = ADVANCEMENTS[adv]
		needed = [group[0] for group in requirements] if requirements else criteria[:1]
		times = {}
		for i, criterion in enumerate(needed):
			times[criterion] = stamp(moment - timedelta(minutes=(len(needed) - 1 - i) * rng.randint(20, 900)))
		body[adv] = {'criteria': times, 'done': True}
	# One advancement in progress, as in a real save.
	for adv in ORDER:
		_, criteria, requirements = ADVANCEMENTS[adv]
		if adv not in body and len(requirements) > 3 and (ADVANCEMENTS[adv][0] in body):
			body[adv] = {'criteria': {requirements[0][0]: stamp(last - timedelta(days=2))}, 'done': False}
			break
	body['DataVersion'] = DATA_VERSION
	return body


def n(base, spread=0.25):
	return max(0, round(base * (1 + rng.uniform(-spread, spread))))


def stats_file(style, hours):
	h = hours
	f = {k: 1.0 for k in ['walk', 'mine', 'fight', 'build', 'farm', 'trade', 'fly', 'boat', 'fish', 'nether', 'redstone']}
	f.update({
		'nether': {'nether': 4, 'fight': 1.6, 'fly': 3},
		'miner': {'mine': 4, 'walk': 0.8},
		'builder': {'build': 4, 'mine': 1.3, 'walk': 0.7},
		'explorer': {'walk': 2.5, 'boat': 4, 'fly': 3, 'fight': 1.2},
		'fighter': {'fight': 4, 'walk': 1.3},
		'redstone': {'redstone': 4, 'build': 1.5, 'walk': 0.6},
		'farmer': {'farm': 4, 'fish': 3, 'walk': 0.8},
		'trader': {'trade': 4, 'farm': 1.5, 'walk': 0.9},
		'newcomer': {'walk': 1.3, 'fight': 0.4, 'mine': 0.5, 'fly': 0, 'nether': 0},
	}[style])
	ticks = round(h * 72000)
	custom = {
		'play_time': ticks, 'total_world_time': ticks + n(h * 3000), 'time_since_rest': n(18000),
		'time_since_death': n(min(ticks, 400000)), 'leave_game': n(h * 0.9),
		'walk_one_cm': n(h * 260000 * f['walk']), 'sprint_one_cm': n(h * 190000 * f['walk']),
		'crouch_one_cm': n(h * 4000 * f['build']), 'swim_one_cm': n(h * 9000 * f['walk']),
		'walk_on_water_one_cm': n(h * 3000), 'walk_under_water_one_cm': n(h * 2500),
		'fall_one_cm': n(h * 30000), 'climb_one_cm': n(h * 2500 * f['mine']),
		'fly_one_cm': n(h * 12000 * f['fly']), 'aviate_one_cm': n(h * 150000 * f['fly']) if f['fly'] > 1 else 0,
		'boat_one_cm': n(h * 15000 * f['boat']), 'horse_one_cm': n(h * 6000 * f['walk']),
		'minecart_one_cm': n(h * 3000 * f['redstone']), 'jump': n(h * 650 * f['walk']),
		'deaths': n(h / 9 * (1.4 if style in ('fighter', 'nether') else 1)), 'mob_kills': n(h * 22 * f['fight']),
		'animals_bred': n(h * 4 * f['farm']), 'fish_caught': n(h * 2 * f['fish']),
		'traded_with_villager': n(h * 3 * f['trade']), 'talked_to_villager': n(h * 5 * f['trade']),
		'damage_dealt': n(h * 1800 * f['fight']), 'damage_taken': n(h * 900 * (1.2 if f['fight'] > 1 else 1)),
		'damage_blocked_by_shield': n(h * 120 * f['fight']), 'damage_resisted': n(h * 200),
		'sleep_in_bed': n(h * 0.5), 'open_chest': n(h * 25 * f['build']), 'open_barrel': n(h * 6),
		'enchant_item': n(h * 0.6), 'interact_with_crafting_table': n(h * 18 * f['build']),
		'interact_with_furnace': n(h * 6 * f['mine']), 'interact_with_anvil': n(h * 0.4),
		'eat_cake_slice': n(h * 0.1), 'drop': n(h * 12), 'use_cauldron': n(h * 0.3),
		'play_noteblock': n(h * 2 * f['redstone']), 'tune_noteblock': n(h * 1.5 * f['redstone']),
		'trigger_trapped_chest': n(h * 0.5 * f['redstone']), 'bell_ring': n(h * 0.2),
	}
	mined = {
		'stone': n(h * 260 * f['mine']), 'deepslate': n(h * 150 * f['mine']), 'dirt': n(h * 70),
		'grass_block': n(h * 25), 'gravel': n(h * 18 * f['mine']), 'sand': n(h * 30 * f['build']),
		'oak_log': n(h * 30 * f['build']), 'spruce_log': n(h * 18 * f['build']), 'birch_log': n(h * 10),
		'coal_ore': n(h * 10 * f['mine']), 'iron_ore': n(h * 6 * f['mine']), 'deepslate_iron_ore': n(h * 5 * f['mine']),
		'copper_ore': n(h * 5 * f['mine']), 'gold_ore': n(h * 1.2 * f['mine']), 'lapis_ore': n(h * 0.8 * f['mine']),
		'redstone_ore': n(h * 2 * f['mine'] * f['redstone']), 'diamond_ore': n(h * 0.25 * f['mine']),
		'deepslate_diamond_ore': n(h * 0.45 * f['mine']), 'emerald_ore': n(h * 0.05 * f['mine']),
		'netherrack': n(h * 40 * f['nether']), 'nether_quartz_ore': n(h * 4 * f['nether'] * f['redstone']),
		'ancient_debris': n(h * 0.12 * f['nether']), 'end_stone': n(h * 8 * f['nether']),
		'wheat': n(h * 14 * f['farm']), 'carrots': n(h * 8 * f['farm']), 'potatoes': n(h * 8 * f['farm']),
		'sugar_cane': n(h * 10 * f['farm']), 'pumpkin': n(h * 1.5 * f['farm']), 'melon': n(h * 2 * f['farm']),
		'short_grass': n(h * 40), 'torch': n(h * 3),
	}
	crafted = {
		'oak_planks': n(h * 30 * f['build']), 'stick': n(h * 20), 'torch': n(h * 18), 'crafting_table': n(h * 0.3),
		'stone_bricks': n(h * 40 * f['build']), 'glass_pane': n(h * 10 * f['build']), 'furnace': n(h * 0.4),
		'iron_pickaxe': n(h * 0.1), 'diamond_pickaxe': n(h * 0.03 * f['mine']), 'bread': n(h * 6 * f['farm']),
		'redstone_torch': n(h * 6 * f['redstone']), 'repeater': n(h * 4 * f['redstone']),
		'comparator': n(h * 1.5 * f['redstone']), 'piston': n(h * 2 * f['redstone']),
		'sticky_piston': n(h * 1 * f['redstone']), 'observer': n(h * 1.2 * f['redstone']),
		'hopper': n(h * 0.8 * f['redstone']), 'rail': n(h * 6 * f['redstone']), 'powered_rail': n(h * 1 * f['redstone']),
		'emerald_block': n(h * 0.3 * f['trade']), 'golden_carrot': n(h * 0.8 * f['farm']),
	}
	used = {
		'stone_bricks': n(h * 160 * f['build']), 'oak_planks': n(h * 120 * f['build']), 'glass': n(h * 40 * f['build']),
		'cobblestone': n(h * 80), 'torch': n(h * 14), 'bread': n(h * 3), 'cooked_beef': n(h * 2),
		'redstone': n(h * 30 * f['redstone']), 'repeater': n(h * 4 * f['redstone']), 'piston': n(h * 2 * f['redstone']),
		'wheat_seeds': n(h * 15 * f['farm']), 'bone_meal': n(h * 6 * f['farm']), 'firework_rocket': n(h * 6 * f['fly']) if f['fly'] > 1 else 0,
		'diamond_pickaxe': n(h * 400 * f['mine']), 'netherite_sword': n(h * 60 * f['fight']), 'bow': n(h * 6 * f['fight']),
		'fishing_rod': n(h * 3 * f['fish']), 'emerald': n(h * 6 * f['trade']), 'oak_boat': n(h * 0.5 * f['boat']),
	}
	broken = {'iron_pickaxe': n(h * 0.06), 'diamond_pickaxe': n(h * 0.02 * f['mine']), 'stone_axe': n(h * 0.05)}
	picked_up = {
		'cobblestone': n(h * 230 * f['mine']), 'cobbled_deepslate': n(h * 140 * f['mine']), 'dirt': n(h * 60),
		'raw_iron': n(h * 9 * f['mine']), 'coal': n(h * 10 * f['mine']), 'diamond': n(h * 0.8 * f['mine']),
		'wheat': n(h * 14 * f['farm']), 'emerald': n(h * 4 * f['trade']), 'netherrack': n(h * 35 * f['nether']),
		'rotten_flesh': n(h * 8 * f['fight']), 'bone': n(h * 6 * f['fight']), 'gunpowder': n(h * 3 * f['fight']),
	}
	dropped = {'dirt': n(h * 20), 'rotten_flesh': n(h * 5), 'cobblestone': n(h * 30)}
	killed = {
		'zombie': n(h * 6 * f['fight']), 'skeleton': n(h * 5 * f['fight']), 'creeper': n(h * 3 * f['fight']),
		'spider': n(h * 3 * f['fight']), 'enderman': n(h * 1.2 * f['fight'] * max(1, f['nether'] / 2)),
		'drowned': n(h * 1 * f['fight']), 'witch': n(h * 0.2 * f['fight']), 'cow': n(h * 2 * f['farm']),
		'pig': n(h * 1 * f['farm']), 'chicken': n(h * 1.2 * f['farm']), 'blaze': n(h * 1 * f['nether']) if f['nether'] else 0,
		'wither_skeleton': n(h * 0.4 * f['nether']) if f['nether'] else 0, 'piglin': n(h * 0.5 * f['nether']) if f['nether'] else 0,
		'ghast': n(h * 0.1 * f['nether']) if f['nether'] else 0, 'phantom': n(h * 0.3),
	}
	if style in ('nether', 'fighter'):
		killed['ender_dragon'] = 2 if style == 'nether' else 1
	if style == 'fighter':
		killed['wither'] = 1
	killed_by = {'zombie': n(h / 40), 'creeper': n(h / 50), 'skeleton': n(h / 45)}
	if f['nether']:
		killed_by['blaze'] = n(h / 120)
	group = lambda values: {f'minecraft:{k}': v for k, v in values.items() if v > 0}
	return {
		'stats': {
			'minecraft:custom': group(custom), 'minecraft:mined': group(mined), 'minecraft:crafted': group(crafted),
			'minecraft:used': group(used), 'minecraft:broken': group(broken), 'minecraft:picked_up': group(picked_up),
			'minecraft:dropped': group(dropped), 'minecraft:killed': group(killed), 'minecraft:killed_by': group(killed_by),
		},
		'DataVersion': DATA_VERSION,
	}


world = server / 'world'
stats_dir = world / 'players' / 'stats'
adv_dir = world / 'players' / 'advancements'
for folder in (stats_dir, adv_dir, world / 'stats_webui_plus'):
	folder.mkdir(parents=True, exist_ok=True)
# Start over: files of an earlier run would add players with other UUIDs.
for folder in (stats_dir, adv_dir):
	for old in folder.glob('*.json'):
		old.unlink()
cache, sessions = [], {}
for name, skin, slim, hours, days, since_online, share, weights, style in PLAYERS:
	player = uuid_for(skin, slim)
	first = NOW - timedelta(days=days, hours=rng.randint(0, 12))
	last = NOW - timedelta(hours=since_online, minutes=rng.randint(0, 59))
	(stats_dir / f'{player}.json').write_text(json.dumps(stats_file(style, hours)))
	(adv_dir / f'{player}.json').write_text(json.dumps(advancement_file(pick_advancements(share, weights), first, last), indent=2))
	sessions[str(player)] = {'firstJoin': int(first.timestamp() * 1000), 'lastLogout': int(last.timestamp() * 1000)}
	cache.append({'name': name, 'uuid': str(player), 'expiresOn': stamp(NOW + timedelta(days=30))})
	print(f'{name:16} {player}  {"slim" if slim else "wide"} {skin}')
(world / 'stats_webui_plus' / 'sessions.json').write_text(json.dumps(sessions, indent=2))
(server / 'usercache.json').write_text(json.dumps(cache, indent=2))

# The screenshot setup: a port of its own, an example address and the Map and Discord buttons with example links.
config_file = server / 'config' / 'stats_webui_plus.json'
if config_file.is_file():
	config = json.loads(config_file.read_text())
	config.update({'port': 25590, 'bind': '127.0.0.1', 'serverAddress': 'play.example.com'})
	links = {'Map': 'https://map.example.com', 'Discord': 'https://discord.example.com'}
	for link in config.get('links', []):
		label = link['label'] if isinstance(link['label'], str) else link['label'].get('en', '')
		if label in links:
			link['url'] = links[label]
	config_file.write_text(json.dumps(config, indent=2) + '\n')
	print('Set up', config_file)
else:
	print('No', config_file, 'yet: start the server once so it writes the default config, then run this again')
print(len(ADVANCEMENTS), 'advancements in the catalog')
