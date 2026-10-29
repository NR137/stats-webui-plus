"""Singleplayer test: the mod on the integrated server of a real Minecraft client, on Fabric or NeoForge.

Starts the loader's dev client (./gradlew :<loader>:runSmokeClient) on a virtual screen (Xvfb, software OpenGL), which
opens a copy of the smoke test's world with the nine sample players directly (--quickPlaySingleplayer). Checks that
the integrated server's web UI answers, lists the world's players and has the client's own player online, then closes
the client and looks through its log for errors from the mod or its mixins.

Usage: python scripts/smoke_test/client_test.py fabric|neoforge
Needs: the smoke test for that loader first (its world), and xvfb-run (both in the dev image, docker/Dockerfile).
"""

import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from smoke_test import CACHE, MC_VERSION, MOD_ID, ROOT, check, encoded, failures, get, is_json  # noqa: E402

PORT = 25583
START_TIMEOUT_S = 900  # The first start downloads the client's assets.


def set_up(loader: str) -> tuple[Path, list[str]]:
	server = CACHE / f'{loader}-{MC_VERSION}'
	world = server / 'world'
	if not (world / 'level.dat').is_file():
		sys.exit(f'{world} is missing: run the smoke test for {loader} first (docker/tasks.sh smoke {loader})')
	folder = ROOT / loader / 'build' / 'run' / 'smokeClient'
	for name in ('saves', 'config', 'logs', 'crash-reports', 'usercache.json', 'options.txt'):
		path = folder / name
		if path.is_dir():
			shutil.rmtree(path)
		elif path.exists():
			path.unlink()
	(folder / 'saves').mkdir(parents=True)
	shutil.copytree(world, folder / 'saves' / 'smoke', ignore=shutil.ignore_patterns('session.lock'))
	if (server / 'usercache.json').is_file():
		shutil.copy(server / 'usercache.json', folder / 'usercache.json')
	(folder / 'config').mkdir()
	(folder / 'config' / f'{MOD_ID}.json').write_text(json.dumps({'port': PORT, 'bind': '127.0.0.1'}))
	# No first-start screens, and no pause when the window has no focus (a paused game stops the integrated server).
	(folder / 'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:2\n')
	sample = sorted(path.stem for path in (world / 'players' / 'stats').glob('*.json'))
	return folder, sample


def processes_in(folder: Path) -> list[int]:
	"""The client is started by Gradle; it is found by its working folder, which is the run folder."""
	pids = []
	for entry in Path('/proc').iterdir():
		if entry.name.isdigit():
			try:
				if Path(os.readlink(entry / 'cwd')).resolve() == folder.resolve():
					pids.append(int(entry.name))
			except OSError:
				pass
	return pids


def main() -> None:
	if len(sys.argv) != 2 or sys.argv[1] not in ('fabric', 'neoforge'):
		sys.exit(__doc__)
	loader = sys.argv[1]
	folder, sample = set_up(loader)
	base = f'http://127.0.0.1:{PORT}'
	log_path = folder / 'client-test.log'
	print(f'Starting the {loader} client in {folder} (web UI on {base})', flush=True)
	# OpenGL through EGL: on Xvfb, SDL (Minecraft's window library) finds no GLX visual it accepts.
	env = dict(os.environ, SDL_VIDEO_FORCE_EGL='1')
	with log_path.open('w', encoding='utf-8') as log:
		client = subprocess.Popen(
			['xvfb-run', '-a', '-s', '-screen 0 1280x720x24', './gradlew', f':{loader}:runSmokeClient', '--console=plain'],
			cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True,
		)
	started = time.time()
	ready = False
	try:
		while time.time() - started < START_TIMEOUT_S and client.poll() is None:
			try:
				ready = get(base, '/api/health').status == 200
			except OSError:
				ready = False
			if ready:
				break
			# Without any graphics backend the client waits on an error screen instead of exiting.
			text = log_path.read_text(encoding='utf-8', errors='replace')
			if 'Failed to create backend OpenGL' in text and 'Failed to create backend Vulkan' in text:
				print('        the client found no graphics backend, see ' + str(log_path))
				break
			time.sleep(2)
		if check(ready, f'singleplayer starts the web UI ({time.time() - started:.0f} s)'):
			print('Integrated server', flush=True)
			index = get(base, '/')
			check(index.status == 200 and '/_app/' in index.body.decode('utf-8', 'replace'), '/ is the web UI')
			status = {}
			deadline = time.time() + 60
			while time.time() < deadline:
				status = get(base, '/api/status').json()
				if status.get('onlinePlayers') == 1:
					break
				time.sleep(1)
			check(status.get('onlinePlayers') == 1, f'the client\'s player is online (got {status.get("onlinePlayers")})')
			players = []
			deadline = time.time() + 30
			while time.time() < deadline:
				players = get(base, '/api/players').json().get('players', [])
				if len(players) > len(sample):
					break
				time.sleep(1)
			uuids = {player['uuid'] for player in players}
			check(set(sample) <= uuids, f'the world\'s {len(sample)} sample players are listed')
			own = [player for player in players if player['uuid'] not in sample]
			check(len(own) == 1, f'the client\'s own player is listed too ({[player["name"] for player in own]})')
			if own:
				page = get(base, '/api/players/' + encoded(own[0]['name']))
				detail = page.json() if is_json(page) else {}
				check(page.status == 200 and detail.get('online') is True, f'{own[0]["name"]} is online on their page')
				check(detail.get('firstJoinEstimated') is False, 'their first join is a real join, not an estimate')
	finally:
		for pid in processes_in(folder):
			try:
				os.kill(pid, signal.SIGTERM)
			except ProcessLookupError:
				pass
		try:
			client.wait(timeout=60)
		except subprocess.TimeoutExpired:
			os.killpg(client.pid, signal.SIGKILL)
			client.wait()

	print('Client log', flush=True)
	log = log_path.read_text(encoding='utf-8', errors='replace')
	# Only log entries ("[time] [thread/LEVEL] ..."): stack traces of unrelated warnings (e.g. the narrator's missing
	# libflite) print the whole classpath, mixin library and mod folders included.
	problems = [
		line for line in log.splitlines()
		if line.startswith('[') and (re.search(r'mixin', line, re.I) and re.search(r'error|fail|exception', line, re.I)
		or re.search(rf'(ERROR|WARN).*({MOD_ID}|webui\.stats\.mc)', line)
		or re.search(r'webui\.stats\.mc\.\S+(Exception|Error)', line))
	]
	for line in problems[:20]:
		print('        ' + line[:300])
	check(not problems, f'no errors or warnings from the mod or mixins ({len(problems)} found)')

	print()
	if failures:
		print(f'{len(failures)} check(s) failed on {loader}; full log: {log_path}')
		sys.exit(1)
	print(f'All singleplayer checks passed on {loader}.')


if __name__ == '__main__':
	main()
