"""Smoke test: the built mod JAR on a real Fabric or NeoForge server, like a server owner runs it.

Sets up the official server for the versions in gradle.properties (with Fabric API on Fabric), puts the JAR from
<loader>/build/libs/ into mods/, writes the nine sample players (scripts/sample_data), starts the server and checks
the web UI and the API over HTTP. Then it changes the config while the server runs (an address, the port), runs
/reload with a new data pack and without it again, restarts the server on another world folder (`world`) and looks
through the log for errors from the mod or its mixins.

Usage: python3 scripts/smoke_test/smoke_test.py fabric|neoforge [--browser]
  --browser  also checks the web UI in headless Chromium (browser_checks.py; needs Playwright, see scripts/README.md)
Needs: ./gradlew build first (the JARs and scripts/.cache/minecraft/<version>/minecraft-client.jar), Java 25 as
`java` (or JAVA_HOME). Servers and downloads are kept in scripts/.cache/smoke/; the world, config and mods are
written fresh on every run. Exits with 1 if a check fails.
"""

import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROPERTIES = dict(
	line.split('=', 1)
	for line in (ROOT / 'gradle.properties').read_text().splitlines()
	if '=' in line and not line.lstrip().startswith('#')
)
MOD_ID = PROPERTIES['mod_id']
MOD_VERSION = PROPERTIES['mod_version']
MC_VERSION = PROPERTIES['minecraft_version']
FULL_VERSION = f'{MOD_VERSION}+{MC_VERSION}'
CACHE = ROOT / 'scripts' / '.cache' / 'smoke'
START_TIMEOUT_S = 900  # The first start downloads Minecraft and the loader's libraries.
WEB_ROOT = f'assets/{MOD_ID}/web/'

failures: list[str] = []


def check(ok: bool, what: str) -> bool:
	print(('  ok    ' if ok else '  FAIL  ') + what, flush=True)
	if not ok:
		failures.append(what)
	return ok


def download(url: str, target: Path) -> Path:
	if target.is_file():
		return target
	target.parent.mkdir(parents=True, exist_ok=True)
	print('Downloading', url, flush=True)
	request = urllib.request.Request(url, headers={'User-Agent': 'stats-webui-plus smoke test'})
	with urllib.request.urlopen(request, timeout=120) as response:
		data = response.read()
	partial = target.with_name(target.name + '.part')
	partial.write_bytes(data)
	partial.replace(target)
	return target


def free_port() -> int:
	with socket.socket() as probe:
		probe.bind(('127.0.0.1', 0))
		return probe.getsockname()[1]


# --- Server setup ---


def fabric_server(folder: Path) -> list[str]:
	installers = json.loads(urllib.request.urlopen('https://meta.fabricmc.net/v2/versions/installer', timeout=60).read())
	installer = next(entry['version'] for entry in installers if entry['stable'])
	loader = PROPERTIES['fabric_loader_version']
	launcher = download(
		f'https://meta.fabricmc.net/v2/versions/loader/{MC_VERSION}/{loader}/{installer}/server/jar',
		CACHE / 'downloads' / f'fabric-server-{MC_VERSION}-{loader}-{installer}.jar',
	)
	shutil.copy(launcher, folder / 'fabric-server-launch.jar')
	api = PROPERTIES['fabric_api_version']
	api_jar = download(
		'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/'
		f'{urllib.parse.quote(api)}/fabric-api-{urllib.parse.quote(api)}.jar',
		CACHE / 'downloads' / f'fabric-api-{api}.jar',
	)
	shutil.copy(api_jar, folder / 'mods' / api_jar.name)
	return ['-jar', 'fabric-server-launch.jar', 'nogui']


def neoforge_server(folder: Path) -> list[str]:
	version = PROPERTIES['neoforge_version']
	args_file = folder / 'libraries' / 'net' / 'neoforged' / 'neoforge' / version / (
		'win_args.txt' if os.name == 'nt' else 'unix_args.txt'
	)
	if not args_file.is_file():
		installer = download(
			f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar',
			CACHE / 'downloads' / f'neoforge-{version}-installer.jar',
		)
		print('Installing the NeoForge server', flush=True)
		subprocess.run([java(), '-jar', str(installer), '--install-server', str(folder)], check=True, cwd=folder,
			stdout=subprocess.DEVNULL)
	return ['@' + str(args_file.relative_to(folder)), 'nogui']


def java() -> str:
	home = os.environ.get('JAVA_HOME')
	if home:
		return str(Path(home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
	return 'java'


def set_up(loader: str, game_port: int, web_port: int) -> tuple[Path, list[str], Path]:
	folder = CACHE / f'{loader}-{MC_VERSION}'
	folder.mkdir(parents=True, exist_ok=True)
	# Fresh state every run; downloaded server files and libraries stay.
	for name in ('world', 'other_world', 'config', 'mods', 'logs', 'crash-reports', 'usercache.json', 'server.properties',
			'smoke-test.log'):
		path = folder / name
		if path.is_dir():
			shutil.rmtree(path)
		elif path.exists():
			path.unlink()
	(folder / 'mods').mkdir()

	jar = ROOT / loader / 'build' / 'libs' / f'stats-webui-plus-{loader}-{FULL_VERSION}.jar'
	if not jar.is_file():
		sys.exit(f'{jar} is missing, run ./gradlew build first')
	shutil.copy(jar, folder / 'mods' / jar.name)
	command = fabric_server(folder) if loader == 'fabric' else neoforge_server(folder)

	(folder / 'eula.txt').write_text('eula=true\n')
	(folder / 'server.properties').write_text('\n'.join([
		f'server-port={game_port}',
		'server-ip=127.0.0.1',
		'online-mode=false',
		'level-type=minecraft\\:flat',
		'generate-structures=false',
		'spawn-protection=0',
		'view-distance=2',
		'simulation-distance=2',
		'max-players=7',
		'motd=Smoke test',
	]) + '\n')

	# Sample players first: it only points an existing config at its own port, and there is none yet.
	client_jar = ROOT / 'scripts' / '.cache' / 'minecraft' / MC_VERSION / 'minecraft-client.jar'
	subprocess.run([sys.executable, str(ROOT / 'scripts' / 'sample_data' / 'sample_data.py'), str(folder), str(client_jar)],
		check=True, stdout=subprocess.DEVNULL)
	config = folder / 'config' / f'{MOD_ID}.json'
	config.parent.mkdir(parents=True, exist_ok=True)
	config.write_text(json.dumps({
		'port': web_port,
		'bind': '127.0.0.1',
		'refreshCooldownMs': 2000,
		'serverAddress': 'smoke.example.com',
		'links': [{'label': 'Map', 'url': 'https://map.example.com'}, {'label': 'Bad', 'url': '//evil.example.com'}],
	}, indent=2))
	# A config written before the first start gets no banner, so put the one from the JAR there (checks it is packed).
	with zipfile.ZipFile(jar) as archive:
		banner = folder / 'config' / MOD_ID / 'header.png'
		banner.parent.mkdir(parents=True, exist_ok=True)
		banner.write_bytes(archive.read(f'assets/{MOD_ID}/header.png'))
	return folder, command, jar


# --- HTTP ---


class Response:
	def __init__(self, status: int, headers, body: bytes):
		self.status = status
		self.headers = headers
		self.body = body

	def header(self, name: str) -> str:
		return self.headers.get(name) or ''

	def json(self):
		return json.loads(self.body)


def get(base: str, path: str, method: str = 'GET') -> Response:
	request = urllib.request.Request(base + path, method=method)
	try:
		with urllib.request.urlopen(request, timeout=30) as response:
			return Response(response.status, response.headers, response.read())
	except urllib.error.HTTPError as error:
		return Response(error.code, error.headers, error.read())


def is_json(response: Response) -> bool:
	return 'application/json' in response.header('Content-Type')


def encoded(path: str) -> str:
	return '/'.join(urllib.parse.quote(part, safe='') for part in path.split('/'))


# --- Checks ---


def check_api(base: str) -> list[str]:
	print('API', flush=True)
	health = get(base, '/api/health')
	check(health.status == 200 and health.json().get('ok') is True, '/api/health answers ok')
	check(MOD_VERSION in str(health.json().get('version')), f'/api/health reports version {MOD_VERSION}')

	status = get(base, '/api/status')
	body = status.json()
	check(status.status == 200, '/api/status answers')
	check(body.get('trackedPlayers') == 9, f'/api/status tracks the 9 sample players (got {body.get("trackedPlayers")})')
	check(body.get('onlinePlayers') == 0 and body.get('maxPlayers') == 7, '/api/status has the server\'s player counts')
	check('max-age' not in status.header('Cache-Control'), '/api/status is never cached')
	check([link.get('url') for link in body.get('links', [])] == ['https://map.example.com'],
		'only the valid menu link is sent (//evil.example.com is hidden)')
	check((body.get('branding') or {}).get('serverAddress') == 'smoke.example.com', 'branding comes from the config')

	players = get(base, '/api/players')
	names = [player['name'] for player in players.json().get('players', [])]
	check(players.status == 200 and len(names) == 9, f'/api/players lists 9 players (got {len(names)})')
	cache = players.header('Cache-Control')
	max_age = re.search(r'max-age=(\d+)', cache)
	check(bool(max_age) and int(max_age.group(1)) <= 2, f'/api/players may be cached until the next refresh ({cache})')

	for name in names:
		page = get(base, '/api/players/' + encoded(name))
		detail = page.json() if is_json(page) else {}
		check(page.status == 200 and detail.get('name') == name and detail.get('stats'), f'/api/players/{name}')
		check(detail.get('firstJoin') is not None, f'{name} has a first join time')
	for name in ('Nobody', '.Nobody', '+Nobody', 'Nobody.png'):
		missing = get(base, '/api/players/' + encoded(name))
		check(missing.status == 404 and is_json(missing) and missing.json().get('error') == 'not_found',
			f'/api/players/{name} is a JSON 404')

	crowns = get(base, '/api/crowns').json()
	check(all((crowns.get(crown) or {}).get('name') in names for crown in ('champion', 'dedicated', 'efficient')),
		'/api/crowns names three sample players')

	overview = get(base, '/api/leaderboards')
	boards = [board['id'] for board in overview.json().get('boards', [])] if is_json(overview) else []
	check(overview.status == 200 and len(boards) > 10, f'/api/leaderboards lists boards ({len(boards)})')
	broken = [board for board in boards if get(base, '/api/leaderboards/' + encoded(board)).status != 200]
	check(not broken, f'every listed leaderboard answers ({", ".join(broken) or "all fine"})')
	stone = get(base, '/api/leaderboards/' + encoded('mined/minecraft:stone'))
	check(stone.status == 200 and len(stone.json().get('entries', [])) > 0, 'vanilla board mined/minecraft:stone has entries')
	check(get(base, '/api/leaderboards/' + encoded('mined/minecraft:no_such_block')).status == 404,
		'an unknown vanilla stat is a 404')

	advancements = get(base, '/api/advancements')
	catalog = advancements.json() if is_json(advancements) else {}
	ids = [entry['id'] for entry in catalog.get('advancements', [])]
	check(advancements.status == 200 and catalog.get('total', 0) > 50, f'/api/advancements has the vanilla advancements ({catalog.get("total")})')
	check(any(entry.get('holders', 0) > 0 for entry in catalog.get('advancements', [])), 'sample players hold advancements')
	check(get(base, '/api/advancements/most').status == 200, '/api/advancements/most answers')
	for advancement in ids[:10]:
		check(get(base, '/api/advancements/' + encoded(advancement.replace(':', '/', 1))).status == 200,
			f'/api/advancements/{advancement}')
	check(get(base, '/api/advancements/minecraft/no/such_thing').status == 404, 'an unknown advancement is a 404')
	check(get(base, '/api/players', method='POST').status == 405, 'POST is refused')
	return names


def check_web(base: str, jar: Path, names: list[str]) -> None:
	print('Web UI from the JAR', flush=True)
	index = get(base, '/')
	html = index.body.decode('utf-8', 'replace')
	check(index.status == 200 and 'text/html' in index.header('Content-Type') and '/_app/' in html, '/ is the web UI')
	with zipfile.ZipFile(jar) as archive:
		files = {name[len(WEB_ROOT):]: name for name in archive.namelist() if name.startswith(WEB_ROOT) and not name.endswith('/')}
		check(len(files) > 100, f'the JAR holds the web UI ({len(files)} files)')
		for page in ('about', 'players', 'leaderboards', 'compare', 'advancements'):
			response = get(base, '/' + page)
			check(response.status == 200 and response.body == archive.read(files[page + '.html']), f'/{page} is {page}.html')
		fallback = archive.read(files['200.html'])
		for path in [f'/players/{encoded(names[0])}', '/players/.BedrockSteve', '/players/..Steve', '/players/Steve.png',
				'/advancements/minecraft/story/mine_stone', '/leaderboards/' + encoded('mined/minecraft:stone')]:
			response = get(base, path)
			check(response.status == 200 and response.body == fallback, f'{urllib.parse.unquote(path)} is the app page')
		scripts = re.findall(r'(?:href|src)="(/_app/immutable/[^"]+\.js)"', html)
		check(bool(scripts), 'index.html loads scripts from /_app/immutable/')
		for script in scripts[:3]:
			response = get(base, script)
			check(response.status == 200 and 'javascript' in response.header('Content-Type')
				and 'immutable' in response.header('Cache-Control'), f'{script} is served as immutable JavaScript')
		icons = sorted(name for name in files if name.startswith('mc/'))[:: max(1, len(files) // 25)][:20]
		for icon in icons:
			response = get(base, '/' + icon)
			check(response.status == 200 and response.body == archive.read(files[icon]), f'/{icon} matches the JAR')
	for path in ('/_app/immutable/missing.js', '/mc/item/missing.png', '/favicon-missing.ico'):
		check(get(base, path).status == 404, f'{path} is a 404')
	check(get(base, '/../../server.properties').status == 400, '/../ is refused')
	etag = index.header('ETag')
	request = urllib.request.Request(base + '/', headers={'If-None-Match': etag})
	try:
		urllib.request.urlopen(request, timeout=30)
		revalidated = 200
	except urllib.error.HTTPError as error:
		revalidated = error.code
	check(bool(etag) and revalidated == 304, 'pages can be revalidated (ETag, 304)')

	header = get(base, '/api/status').json().get('headerImage')
	image = get(base, header) if header else None
	check(bool(image) and image.status == 200 and image.header('Content-Type') == 'image/png', 'the header image is served')


def check_reload(base: str, config: Path) -> None:
	print('Config change while running', flush=True)
	data = json.loads(config.read_text())
	data['serverAddress'] = 'changed.example.com'
	config.write_text(json.dumps(data, indent=2))
	deadline = time.time() + 20
	address = None
	while time.time() < deadline:
		address = (get(base, '/api/status').json().get('branding') or {}).get('serverAddress')
		if address == 'changed.example.com':
			break
		time.sleep(0.5)
	check(address == 'changed.example.com', f'a saved config applies without restart (got {address})')


def check_log(log: str) -> None:
	print('Server log', flush=True)
	check(re.search(r'Cached \d+ frontend files', log) is not None, 'the mod started its web server')
	problems = [
		line for line in log.splitlines()
		if re.search(r'mixin', line, re.I) and re.search(r'error|fail|exception', line, re.I)
		or re.search(rf'(ERROR|WARN).*({MOD_ID}|webui\.stats\.mc)', line)
		or re.search(r'webui\.stats\.mc\.\S+(Exception|Error)', line)
	]
	# The one warning this run causes on purpose.
	problems = [line for line in problems if 'Hiding link //evil.example.com' not in line]
	for line in problems[:20]:
		print('        ' + line)
	check(not problems, f'no errors or warnings from the mod or mixins ({len(problems)} found)')


# --- Server process ---


class Server:
	"""The server process; its output is kept for the log checks and appended to smoke-test.log."""

	def __init__(self, folder: Path, command: list[str]):
		self.output: list[str] = []
		self.process = subprocess.Popen(
			[java(), '-Xmx2G', *command], cwd=folder, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
			stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace',
		)
		self.log_file = (folder / 'smoke-test.log').open('a', encoding='utf-8')
		self.reader = threading.Thread(target=self._pump, daemon=True)
		self.reader.start()

	def _pump(self):
		for line in self.process.stdout:
			self.output.append(line.rstrip('\n'))
			self.log_file.write(line)
			self.log_file.flush()

	def wait_ready(self, base: str) -> bool:
		started = time.time()
		done_at = None
		ready = False
		while time.time() - started < START_TIMEOUT_S and self.process.poll() is None:
			try:
				ready = get(base, '/api/health').status == 200
			except OSError:
				ready = False
			if done_at is None and any(re.search(r'Done \(', line) for line in self.output):
				done_at = time.time()
			if ready and done_at is not None:
				break
			# The server is up but the mod's web server isn't: the mod didn't load or crashed while starting.
			if done_at is not None and time.time() - done_at > 60:
				break
			time.sleep(1)
		check(ready, f'the server starts with the mod ({time.time() - started:.0f} s)')
		return ready

	def command(self, line: str) -> None:
		"""A console command, as typed into the server's console."""
		self.process.stdin.write(line + '\n')
		self.process.stdin.flush()

	def stop(self) -> None:
		if self.process.poll() is None:
			self.process.stdin.write('stop\n')
			self.process.stdin.flush()
			try:
				self.process.wait(timeout=120)
			except subprocess.TimeoutExpired:
				self.process.kill()
				self.process.wait()
		self.reader.join(timeout=10)
		self.log_file.close()
		check(self.process.returncode == 0, f'the server stops cleanly (exit code {self.process.returncode})')


def set_config(config: Path, **changes) -> None:
	data = json.loads(config.read_text())
	data.update(changes)
	config.write_text(json.dumps(data, indent=2))


def check_port_change(base: str, config: Path) -> str:
	"""README: config changes apply without a restart; a new port or address moves the web server."""
	print('New port while running', flush=True)
	new_port = free_port()
	new_base = f'http://127.0.0.1:{new_port}'
	set_config(config, port=new_port)
	deadline = time.time() + 20
	moved = False
	while time.time() < deadline and not moved:
		try:
			moved = get(new_base, '/api/health').status == 200
		except OSError:
			time.sleep(0.5)
	check(moved, f'the web server moves to the new port {new_port}')
	try:
		get(base, '/api/health')
		old_closed = False
	except OSError:
		old_closed = True
	check(old_closed, 'the old port is closed')
	return new_base


def wait_for(condition, seconds: float = 30) -> bool:
	deadline = time.time() + seconds
	while time.time() < deadline:
		if condition():
			return True
		time.sleep(0.5)
	return False


def check_datapack_reload(base: str, folder: Path, server: 'Server') -> None:
	"""CHANGELOG: after /reload the advancements follow the new data packs (added or removed advancements)."""
	print('/reload with a new data pack', flush=True)
	client_jar = ROOT / 'scripts' / '.cache' / 'minecraft' / MC_VERSION / 'minecraft-client.jar'
	with zipfile.ZipFile(client_jar) as jar:
		data_format = json.loads(jar.read('version.json'))['pack_version']['data_major']
	pack = folder / 'world' / 'datapacks' / 'smoke_test'
	advancement = pack / 'data' / 'smoke_test' / 'advancement' / 'hello.json'
	advancement.parent.mkdir(parents=True, exist_ok=True)
	(pack / 'pack.mcmeta').write_text(json.dumps({'pack': {
		'description': 'Stats WebUI Plus smoke test', 'min_format': data_format, 'max_format': data_format,
	}}))
	# A child of the Minecraft tab; a visible advancement without parent would be a tab and need a background.
	advancement.write_text(json.dumps({
		'parent': 'minecraft:story/root',
		'criteria': {'never': {'trigger': 'minecraft:impossible'}},
		'display': {'icon': {'id': 'minecraft:diamond'}, 'title': 'Smoke test', 'description': 'Added by /reload'},
	}))

	def listed() -> bool:
		catalog = get(base, '/api/advancements')
		return is_json(catalog) and any(entry['id'] == 'smoke_test:hello' for entry in catalog.json().get('advancements', []))

	check(not listed(), 'the data pack\'s advancement is not there before /reload')
	server.command('reload')
	check(wait_for(listed), 'after /reload the new advancement is listed')
	check(get(base, '/api/advancements/smoke_test/hello').status == 200, '... and has its own page')
	shutil.rmtree(pack)
	server.command('reload')
	check(wait_for(lambda: not listed()), 'after removing the data pack and /reload it is gone again')
	check(get(base, '/api/advancements/smoke_test/hello').status == 404, '... and its page is a 404')


def check_other_world(folder: Path, command: list[str], config: Path) -> list[str]:
	"""README: `world` reads stats from another world folder; only a new world waits for a restart."""
	print('Another world folder (after a restart)', flush=True)
	world = folder / 'world' / 'players'
	other = folder / 'other_world' / 'players'
	picked = sorted(path.stem for path in (world / 'stats').glob('*.json'))[:3]
	for kind in ('stats', 'advancements'):
		(other / kind).mkdir(parents=True, exist_ok=True)
		for uuid in picked:
			if (world / kind / f'{uuid}.json').is_file():
				shutil.copy(world / kind / f'{uuid}.json', other / kind / f'{uuid}.json')
	web_port = free_port()
	base = f'http://127.0.0.1:{web_port}'
	set_config(config, world='other_world', port=web_port)
	server = Server(folder, command)
	try:
		if server.wait_ready(base):
			status = get(base, '/api/status').json()
			check(status.get('trackedPlayers') == 3, f'the API reads other_world: 3 players (got {status.get("trackedPlayers")})')
			uuids = sorted(player['uuid'] for player in get(base, '/api/players').json().get('players', []))
			check(uuids == picked, 'exactly the players of other_world, not those of the running world')
	finally:
		server.stop()
	return server.output


# --- Main ---


def main() -> None:
	args = sys.argv[1:]
	browser = '--browser' in args
	if browser:
		args.remove('--browser')
	if len(args) != 1 or args[0] not in ('fabric', 'neoforge'):
		sys.exit(__doc__)
	loader = args[0]
	if browser:
		# Fails here, before the server starts, if Playwright is missing.
		sys.path.insert(0, str(Path(__file__).parent))
		import browser_checks
	game_port, web_port = free_port(), free_port()
	folder, command, jar = set_up(loader, game_port, web_port)
	config = folder / 'config' / f'{MOD_ID}.json'
	base = f'http://127.0.0.1:{web_port}'
	print(f'Starting the {loader} server in {folder} (web UI on {base})', flush=True)

	server = Server(folder, command)
	try:
		if server.wait_ready(base):
			names = check_api(base)
			check_web(base, jar, names)
			if browser:
				browser_checks.run(base, names, check)
			check_reload(base, config)
			base = check_port_change(base, config)
			check_datapack_reload(base, folder, server)
	finally:
		server.stop()
	output = server.output + check_other_world(folder, command, config)
	check_log('\n'.join(output))

	print()
	if failures:
		print(f'{len(failures)} check(s) failed on {loader}; full log: {folder / "smoke-test.log"}')
		sys.exit(1)
	print(f'All checks passed on {loader}.')


if __name__ == '__main__':
	main()
