"""Load test: many players and many visitors at once, on the built JAR and the official server (like the smoke test).

README: within refreshCooldownMs, visitors cost the server thread nothing; a refresh takes a fraction of a
millisecond there; however many people browse, the game does that work at most once per window. This measures:

- requests per second and response times (median, 95th and 99th percentile) of a mix of pages and API calls,
- the game's milliseconds per tick (/tick query) idle and under load,
- in two phases: refreshCooldownMs at its default (15 s) and at 0 (a refresh on every request, the worst case),
- with online players whose stats change all the time (the load test add-on, common/src/loadtest: logged in like a
  real client, without one), so every refresh has changed players to copy on the server thread. "Idle" is them
  playing without visitors.

Usage: python3 scripts/load_test/load_test.py fabric|neoforge [--players 200] [--online 100] [--clients 50] [--seconds 30]
Run it with fixed resources to compare machines: docker compose run --rm load (see compose.yaml; LOAD_CPUS, LOAD_MEMORY).
Writes build/load-test/<loader>.json and, in GitHub Actions, a table to the job summary. Fails only if more than 1 %
of the requests fail; everything else is reported.
"""

import argparse
import json
import os
import platform
import random
import shutil
import statistics
import sys
import threading
import time
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'smoke_test'))
import smoke_test  # noqa: E402
from smoke_test import MOD_ID, ROOT, Server, encoded, free_port, get, set_config, set_up  # noqa: E402

RANDOM = random.Random(26)


def add_players(folder: Path, count: int) -> list[str]:
	"""Copies of the sample players under new UUIDs and names; usercache.json knows them, so Mojang is never asked."""
	players = folder / 'world' / 'players'
	samples = sorted((players / 'stats').glob('*.json'))
	cache_file = folder / 'usercache.json'
	cache = json.loads(cache_file.read_text()) if cache_file.is_file() else []
	names = [entry['name'] for entry in cache]
	for index in range(count):
		source = samples[index % len(samples)].stem
		new = str(uuid.UUID(int=RANDOM.getrandbits(128), version=4))
		name = f'Load{index:03d}'
		for kind in ('stats', 'advancements'):
			if (players / kind / f'{source}.json').is_file():
				shutil.copy(players / kind / f'{source}.json', players / kind / f'{new}.json')
		cache.append({'name': name, 'uuid': new, 'expiresOn': '2099-01-01 00:00:00 +0000'})
		names.append(name)
	cache_file.write_text(json.dumps(cache))
	return names


def add_online_players(loader: str, folder: Path, count: int) -> None:
	"""The load test add-on next to the mod, and how many players it logs in."""
	if count <= 0:
		return
	jar = ROOT / loader / 'build' / 'libs' / f'stats-webui-plus-loadtest-{loader}.jar'
	if not jar.is_file():
		sys.exit(f'{jar} is missing, run ./gradlew build first')
	shutil.copy(jar, folder / 'mods' / jar.name)
	(folder / 'config' / 'stats_webui_plus_loadtest.json').write_text(json.dumps({'players': count}))


def online(base: str) -> int:
	try:
		return get(base, '/api/status').json().get('onlinePlayers', -1)
	except OSError:
		return -1


def tracked(base: str) -> int:
	try:
		return len(get(base, '/api/players').json()['players'])
	except (OSError, KeyError, ValueError):
		return -1


def tick_times(server: Server) -> dict:
	"""Milliseconds per tick from /tick query: the average and the percentiles of the last ~100 ticks."""
	start = len(server.output)
	server.command('tick query')
	deadline = time.time() + 10
	result = {}
	while time.time() < deadline and 'p99' not in result:
		for line in server.output[start:]:
			if 'Average time per tick:' in line:
				result['average'] = float(line.split('Average time per tick:')[1].split('ms')[0])
			if 'Percentiles:' in line:
				part = line.split('Percentiles:')[1]
				for key in ('P50', 'P95', 'P99'):
					result[key.lower()] = float(part.split(f'{key}:')[1].split('ms')[0])
		time.sleep(0.2)
	return result


def load(base: str, paths: list[str], clients: int, seconds: float, server: Server) -> dict:
	latencies: list[float] = []
	statuses: dict[int, int] = {}
	lock = threading.Lock()
	stop = threading.Event()

	def client():
		mine = []
		codes = {}
		while not stop.is_set():
			path = RANDOM.choice(paths)
			started = time.perf_counter()
			try:
				status = get(base, path).status
			except OSError:
				status = 0
			mine.append((time.perf_counter() - started) * 1000)
			codes[status] = codes.get(status, 0) + 1
		with lock:
			latencies.extend(mine)
			for code, count in codes.items():
				statuses[code] = statuses.get(code, 0) + count

	threads = [threading.Thread(target=client, daemon=True) for _ in range(clients)]
	started = time.time()
	for thread in threads:
		thread.start()
	# The tick percentiles cover the last ~5 s, so ask while the load is still on.
	time.sleep(max(0.0, seconds - 6))
	ticks = tick_times(server)
	time.sleep(max(0.0, seconds - (time.time() - started)))
	stop.set()
	for thread in threads:
		thread.join(timeout=30)
	elapsed = time.time() - started
	latencies.sort()

	def percentile(share: float) -> float:
		return round(latencies[min(len(latencies) - 1, int(share * len(latencies)))], 1) if latencies else 0.0

	total = len(latencies)
	failed = sum(count for code, count in statuses.items() if code not in (200, 503))
	return {
		'requests': total,
		'per_second': round(total / elapsed, 1),
		'ms_median': round(statistics.median(latencies), 1) if latencies else 0.0,
		'ms_p95': percentile(0.95),
		'ms_p99': percentile(0.99),
		'busy_503': statuses.get(503, 0),
		'failed': failed,
		'statuses': {str(code): count for code, count in sorted(statuses.items())},
		'tick_ms': ticks,
	}


def machine() -> dict:
	info = {'platform': platform.platform(), 'cpus_visible': len(os.sched_getaffinity(0))}
	try:
		for line in Path('/proc/cpuinfo').read_text().splitlines():
			if line.startswith(('model name', 'Model')):
				info['cpu'] = line.split(':', 1)[1].strip()
				break
	except OSError:
		pass
	for key, path in (('cpu_limit', '/sys/fs/cgroup/cpu.max'), ('memory_limit', '/sys/fs/cgroup/memory.max')):
		try:
			info[key] = Path(path).read_text().strip()
		except OSError:
			pass
	return info


def main() -> None:
	parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
	parser.add_argument('loader', choices=['fabric', 'neoforge'])
	parser.add_argument('--players', type=int, default=200)
	parser.add_argument('--online', type=int, default=100, help='online test players (0 for none)')
	parser.add_argument('--clients', type=int, default=50)
	parser.add_argument('--seconds', type=float, default=30)
	parser.add_argument('--warmup', type=float, default=15, help='seconds of load before measuring')
	args = parser.parse_args()

	folder, command, _jar = set_up(args.loader, free_port(), web_port := free_port())
	names = add_players(folder, args.players)
	add_online_players(args.loader, folder, args.online)
	config = folder / 'config' / f'{MOD_ID}.json'
	base = f'http://127.0.0.1:{web_port}'
	print(f'Load test on {args.loader}: {len(names) + args.online} players, {args.online} of them online, '
		f'{args.clients} clients, {args.seconds:.0f} s per phase', flush=True)

	report = {'loader': args.loader, 'players': len(names) + args.online, 'online': args.online, 'clients': args.clients,
		'seconds': args.seconds,
		'machine': machine(), 'phases': {}}
	server = Server(folder, command)
	try:
		if not server.wait_ready(base):
			sys.exit(1)
		smoke_test.check(smoke_test.wait_for(lambda: online(base) == args.online, 60),
			f'{args.online} online test players are logged in (got {online(base)})')
		# A player who just joined is in the stats cache from the next refresh on: wait for all before measuring.
		smoke_test.check(smoke_test.wait_for(lambda: tracked(base) == len(names) + args.online, 60),
			f'all {len(names) + args.online} players are in the stats cache (got {tracked(base)})')
		boards = [board['id'] for board in get(base, '/api/leaderboards').json()['boards']]
		paths = (['/', '/api/players', '/api/crowns', '/api/leaderboards', '/api/status', '/api/advancements']
			+ [f'/api/players/{encoded(name)}' for name in names[:50]]
			+ [f'/api/players/Online{index:03d}' for index in range(min(args.online, 20))]
			+ [f'/api/leaderboards/{encoded(board)}' for board in boards[:20]])
		# Java compiles the busy code while it runs; until then the first measurement came out slower at random (with
		# 2 CPUs once 30x). Unmeasured load first, then a pause, so idle and every phase start warm.
		if args.warmup > 0:
			print(f'Warm-up ({args.warmup:.0f} s)', flush=True)
			load(base, paths, args.clients, args.warmup, server)
		# /tick query covers the last ~5 s: measured later, idle doesn't include cleaning up after the warm-up.
		time.sleep(15)
		report['idle_tick_ms'] = tick_times(server)
		for phase, cooldown in (('cooldown_15s', 15000), ('cooldown_0', 0)):
			set_config(config, refreshCooldownMs=cooldown)
			time.sleep(3)
			print(f'Phase {phase}', flush=True)
			report['phases'][phase] = load(base, paths, args.clients, args.seconds, server)
		# The add-on answers the server's keep-alive requests; otherwise they would be kicked after 15 s.
		smoke_test.check(online(base) == args.online, f'all {args.online} test players are still online (got {online(base)})')
	finally:
		server.stop()

	out = ROOT / 'build' / 'load-test'
	out.mkdir(parents=True, exist_ok=True)
	(out / f'{args.loader}.json').write_text(json.dumps(report, indent=2))
	lines = [
		f'### Load test: {args.loader}, {report["players"]} players ({args.online} online), {args.clients} clients',
		'',
		f'Machine: {report["machine"].get("cpu", "?")}, {report["machine"]["cpus_visible"]} CPUs visible, '
		f'CPU limit {report["machine"].get("cpu_limit", "none")}, memory limit {report["machine"].get("memory_limit", "none")}',
		'',
		f'Idle (players online, no visitors): {report["idle_tick_ms"].get("average", "?")} ms per tick (P99 {report["idle_tick_ms"].get("p99", "?")} ms)',
		'',
		'| Phase | Requests/s | Median ms | P95 ms | P99 ms | 503 | Failed | Tick ms (avg / P99) |',
		'| --- | --- | --- | --- | --- | --- | --- | --- |',
	]
	failed_total = requests_total = 0
	for phase, result in report['phases'].items():
		ticks = result['tick_ms']
		lines.append(f'| {phase} | {result["per_second"]} | {result["ms_median"]} | {result["ms_p95"]} | {result["ms_p99"]} | '
			f'{result["busy_503"]} | {result["failed"]} | {ticks.get("average", "?")} / {ticks.get("p99", "?")} |')
		failed_total += result['failed']
		requests_total += result['requests']
	summary = '\n'.join(lines)
	print('\n' + summary)
	if os.environ.get('GITHUB_STEP_SUMMARY'):
		Path(os.environ['GITHUB_STEP_SUMMARY']).parent.mkdir(parents=True, exist_ok=True)
		with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as file:
			file.write(summary + '\n\n')
	smoke_test.check(not smoke_test.failures, 'the server started and stopped cleanly')
	share = failed_total / max(1, requests_total)
	if smoke_test.failures or share > 0.01:
		print(f'\nFAILED: {failed_total} of {requests_total} requests failed ({share:.1%})')
		sys.exit(1)
	print(f'\nReport: {out / (args.loader + ".json")}')


if __name__ == '__main__':
	main()
