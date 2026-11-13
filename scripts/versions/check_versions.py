"""Every version the project sets, against the newest one there is: Minecraft and its loaders, Mixin, the Gradle
plugins, the web packages, the tools of the dev image and CI. Knows the project's rules: Fabric API, NeoForge and
NeoForm are compared within our Minecraft version, Java is the one Minecraft needs, Mixin the older of what NeoForge and
Fabric Loader ship, npm releases younger than Yarn's one-day hold wait, and an npm package is held back where another of
our packages doesn't allow it yet (peerDependencies, e.g. TypeScript for SvelteKit and svelte-check).

Usage: python3 scripts/versions/check_versions.py   (./tasks.sh versions)
Exits with 1 when anything can be updated (held and waiting versions don't count). Only the standard library, so it runs anywhere with Python 3 and a network.
With GITHUB_TOKEN set, GitHub's API allows more requests; with GITHUB_STEP_SUMMARY set, the table goes there too.
"""
import json
import os
import re
import sys
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# Yarn's npmMinimalAgeGate (its default): newer npm releases can't be installed yet.
NPM_HOLD = timedelta(days=1)


def fetch(url: str) -> str:
	headers = {'User-Agent': 'stats-webui-plus-version-check'}
	if url.startswith('https://api.github.com/') and os.environ.get('GITHUB_TOKEN'):
		headers['Authorization'] = 'Bearer ' + os.environ['GITHUB_TOKEN']
	with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
		return response.read().decode('utf-8')


def maven_versions(url: str) -> list[str]:
	"""The versions of a maven-metadata.xml, in the repository's order (oldest first)."""
	return re.findall(r'<version>([^<]+)</version>', fetch(url))


def numbers(version: str) -> tuple:
	"""A version's numbers for comparing: 0.166.2+26.3 -> (0, 166, 2); v7.0.1 -> (7, 0, 1)."""
	core = version.lstrip('v').split('+')[0]
	return tuple(int(part) for part in re.findall(r'\d+', core))


def stable(version: str) -> bool:
	return not re.search(r'(alpha|beta|rc|pre|snapshot|-SNAPSHOT)', version, re.IGNORECASE)


def newest(versions) -> str:
	return max(versions, key=numbers)


def is_major(used: str, latest: str) -> bool:
	"""A new major version: the first number, or the second while the first is 0."""
	a, b = numbers(used), numbers(latest)
	if not a or not b:
		return False
	return a[0] != b[0] or (a[0] == 0 and len(a) > 1 and len(b) > 1 and a[1] != b[1])


ROWS = []


def row(area: str, name: str, used: str, latest: str, note: str = '', status: str = '') -> None:
	if not status:
		if numbers(latest) <= numbers(used):
			status = 'current'
		else:
			status = 'major' if is_major(used, latest) else 'update'
	ROWS.append((area, name, used, latest, status, note))


def check(area: str, name: str, used: str, look) -> None:
	"""A row whose newest version comes from look(); a failed lookup is a row of its own."""
	try:
		result = look()
		latest, note, status = (result + ('', ''))[:3] if isinstance(result, tuple) else (result, '', '')
		row(area, name, used, latest, note, status)
	except Exception as error:
		ROWS.append((area, name, used, '?', 'error', f'lookup failed: {error}'))


def read(path: str) -> str:
	return (ROOT / path).read_text()


def prop(text: str, key: str) -> str:
	return re.search(rf'^{re.escape(key)}=(.+)$', text, re.MULTILINE).group(1).strip()


# --- Minecraft and its loaders ---------------------------------------------------------------------------------------

properties = read('gradle.properties')
minecraft = prop(properties, 'minecraft_version')
manifest = json.loads(fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
check('Minecraft', 'Minecraft', minecraft, lambda: (manifest['latest']['release'],
	'a new release needs a port of the mod' if manifest['latest']['release'] != minecraft else ''))

fabric_loader = prop(properties, 'fabric_loader_version')
check('Minecraft', 'Fabric Loader', fabric_loader, lambda: next(
	entry['version'] for entry in json.loads(fetch('https://meta.fabricmc.net/v2/versions/loader')) if entry['stable']))

fabric_api = prop(properties, 'fabric_api_version')
check('Minecraft', 'Fabric API', fabric_api, lambda: (newest(
	v for v in maven_versions('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml')
	if v.endswith('+' + minecraft)), f'for Minecraft {minecraft}'))

neoforge = prop(properties, 'neoforge_version')
check('Minecraft', 'NeoForge', neoforge, lambda: (newest(
	v for v in maven_versions('https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml')
	if v.startswith(minecraft + '.') and stable(v)), f'for Minecraft {minecraft}'))

neoform = prop(properties, 'neo_form_version')
check('Minecraft', 'NeoForm', neoform, lambda: (newest(
	v for v in maven_versions('https://maven.neoforged.net/releases/net/neoforged/neoform/maven-metadata.xml')
	if re.fullmatch(re.escape(minecraft) + r'-\d+', v)), f'for Minecraft {minecraft}'))


def java_for_minecraft():
	entry = next(v for v in manifest['versions'] if v['id'] == minecraft)
	needed = str(json.loads(fetch(entry['url']))['javaVersion']['majorVersion'])
	return needed, f'the Java Minecraft {minecraft} runs on'


tool_versions = dict(line.split()[:2] for line in read('.tool-versions').splitlines() if line.strip())
check('Tools', 'Java', tool_versions['java'], java_for_minecraft)


def mixin_both_ship():
	"""The older of the Mixin versions our NeoForge and Fabric Loader ship: the common code may use only what both have."""
	loader = json.loads(fetch(f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{fabric_loader}/'
		f'fabric-loader-{fabric_loader}.json'))
	fabric = next(lib['name'].split(':')[2] for lib in loader['libraries']['common']
		if lib['name'].startswith('net.fabricmc:sponge-mixin:'))
	pom = fetch(f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{neoforge}/neoforge-{neoforge}.pom')
	neo = re.search(r'<artifactId>sponge-mixin</artifactId>\s*<version>([^<]+)</version>', pom).group(1)
	older = min(fabric, neo, key=numbers)
	return older, f'NeoForge {neoforge} ships {neo}, Fabric Loader {fabric_loader} {fabric}'


mixin = re.search(r"sponge-mixin:([^')\"]+)", read('common/build.gradle')).group(1)
check('Minecraft', 'Mixin (compile only)', mixin, mixin_both_ship)

# --- Gradle -----------------------------------------------------------------------------------------------------------

build = read('build.gradle')
loom = re.search(r"id 'net\.fabricmc\.fabric-loom' version '([^']+)'", build).group(1)
check('Gradle', 'Fabric Loom', loom, lambda: newest(v for v in maven_versions(
	'https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml')
	if stable(v)))
moddev = re.search(r"id 'net\.neoforged\.moddev' version '([^']+)'", build).group(1)
check('Gradle', 'ModDevGradle', moddev, lambda: newest(v for v in maven_versions(
	'https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml')
	if stable(v)))
foojay = re.search(r"foojay-resolver-convention' version '([^']+)'", read('settings.gradle')).group(1)
check('Gradle', 'foojay resolver', foojay, lambda: newest(v for v in maven_versions(
	'https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/'
	'org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/maven-metadata.xml') if stable(v)))
gradle = re.search(r'gradle-([\d.]+)-bin\.zip', read('gradle/wrapper/gradle-wrapper.properties')).group(1)
check('Gradle', 'Gradle', gradle, lambda: json.loads(fetch('https://services.gradle.org/versions/current'))['version'])
junit = re.search(r"junit-bom:([^')\"]+)", read('common/build.gradle')).group(1)
check('Gradle', 'JUnit', junit, lambda: newest(v for v in maven_versions(
	'https://repo1.maven.org/maven2/org/junit/junit-bom/maven-metadata.xml') if stable(v)))

# --- Web --------------------------------------------------------------------------------------------------------------

package = json.loads(read('web/package.json'))
lock = read('web/yarn.lock')


def installed(name: str) -> str:
	"""The version yarn.lock resolved for a package (the range in package.json may allow older ones)."""
	match = re.search(rf'^"{re.escape(name)}@npm:[^"]*":\n(?:  .*\n)*?  version: ([^\n]+)', lock, re.MULTILINE)
	return match.group(1).strip() if match else package.get('dependencies', {}).get(name) or package['devDependencies'][name]


NPM = {}


def npm(name: str) -> dict:
	if name not in NPM:
		NPM[name] = json.loads(fetch('https://registry.npmjs.org/' + name.replace('/', '%2F')))
	return NPM[name]


def satisfies(version: str, spec: str) -> bool:
	"""Whether a version is in an npm range: alternatives with ||, each a list of ^x, ~x, >=x, >x, <=x, <x, x or x.x."""
	v = numbers(version)
	for alternative in spec.split('||'):
		ok = bool(alternative.strip())
		for part in alternative.split():
			op, bound = re.match(r'([\^~<>=]*)v?(.+)', part).groups()
			if bound in ('*', 'x'):
				continue
			b = numbers(bound)
			if op == '^':
				first = next((i for i, n in enumerate(b) if n), len(b) - 1)
				ok &= v >= b and v[:first + 1] == b[:first + 1]
			elif op == '~':
				ok &= v >= b and v[:2] == b[:2]
			elif op in ('', '='):
				ok &= v[:len(b)] == b
			else:
				ok &= {'>=': v >= b, '>': v > b, '<=': v <= b, '<': v < b}[op]
		if ok:
			return True
	return False


def peer_ranges(name: str) -> list:
	"""(package, range) for each of our packages that has name as a peer dependency, in their installed versions."""
	ranges = []
	for other in names:
		if other != name:
			peers = npm(other)['versions'].get(installed(other), {}).get('peerDependencies', {})
			if name in peers:
				ranges.append((f'{other} {installed(other)}', peers[name]))
	return ranges


def npm_newest(name: str):
	info = npm(name)
	latest = info['dist-tags']['latest']
	ranges = peer_ranges(name)
	allowed = [v for v in info['versions'] if stable(v) and numbers(v) <= numbers(latest)
		and all(satisfies(v, spec) for _, spec in ranges)]
	if allowed and newest(allowed) != latest:
		blockers = ', '.join(f'{who} wants {spec}' for who, spec in ranges if not satisfies(latest, spec))
		best = newest(allowed)
		if numbers(best) <= numbers(installed(name)):
			return latest, f'held: {blockers}', 'held'
		latest, note = best, f'{latest} is out, but {blockers}'
	else:
		note = ''
	released = datetime.fromisoformat(info['time'][latest].replace('Z', '+00:00'))
	if datetime.now(timezone.utc) - released < NPM_HOLD:
		return latest, f'released {released:%Y-%m-%d %H:%M} UTC, Yarn installs it a day later', 'wait'
	return latest, note


names = [name for section in ('dependencies', 'devDependencies') for name in package.get(section, {})]
for name in names:
	check('Web', name, installed(name), lambda name=name: npm_newest(name))
yarn = package['packageManager'].split('@')[1]
check('Web', 'Yarn', yarn, lambda: npm_newest('@yarnpkg/cli-dist'))

# --- Dev image and scripts --------------------------------------------------------------------------------------------


def endoflife(product: str, lts: bool = False) -> dict:
	"""The newest release cycle of a product on endoflife.date (with lts: the newest long-term support one)."""
	cycles = json.loads(fetch(f'https://endoflife.date/api/{product}.json'))
	return next(cycle for cycle in cycles if not lts or cycle.get('lts'))


check('Tools', 'Node', tool_versions['nodejs'], lambda: (str(endoflife('nodejs', lts=True)['cycle']), 'newest LTS'))
check('Tools', 'Python', tool_versions['python'], lambda: str(endoflife('python')['cycle']))
dockerfile = read('docker/Dockerfile')
ubuntu = re.search(r'^FROM ubuntu:([\d.]+)', dockerfile, re.MULTILINE).group(1)
newest_lts = lambda: (str(endoflife('ubuntu', lts=True)['cycle']), 'newest LTS')
check('Tools', 'Ubuntu (dev image)', ubuntu, newest_lts)
mise = re.search(r'MISE_VERSION=v?([\d.]+)', dockerfile).group(1)
check('Tools', 'mise', mise, lambda: json.loads(fetch('https://api.github.com/repos/jdx/mise/releases/latest'))['tag_name'].lstrip('v'))
corepack = re.search(r'COREPACK_VERSION=([\d.]+)', dockerfile).group(1)
check('Tools', 'Corepack', corepack, lambda: npm_newest('corepack'))
for line in read('scripts/requirements.txt').splitlines():
	if '==' in line:
		name, version = line.split('==')
		check('Tools', name, version.strip(), lambda name=name: json.loads(fetch(f'https://pypi.org/pypi/{name}/json'))['info']['version'])

# --- GitHub Actions ---------------------------------------------------------------------------------------------------

workflows = ''.join(path.read_text() for path in sorted((ROOT / '.github').rglob('*.yml')))
for action, major in sorted(set(re.findall(r'uses: ([\w-]+/[\w-]+)(?:/[\w/-]+)?@v(\d+)', workflows))):
	check('CI', action, major, lambda action=action: str(numbers(json.loads(fetch(
		f'https://api.github.com/repos/{action}/releases/latest'))['tag_name'])[0]))
for runner in sorted(set(re.findall(r'ubuntu-(\d+\.\d+)', workflows))):
	check('CI', 'GitHub runner ubuntu', runner, newest_lts)

# --- Output -----------------------------------------------------------------------------------------------------------

order = {'error': 0, 'major': 1, 'update': 2, 'wait': 3, 'held': 4, 'current': 5}
ROWS.sort(key=lambda r: (order[r[4]], ['Minecraft', 'Gradle', 'Web', 'Tools', 'CI'].index(r[0]), r[1].lower()))
header = ('Area', 'Name', 'Used', 'Newest', 'Status', 'Note')
widths = [max(len(str(r[i])) for r in ROWS + [header]) for i in range(5)]
for r in [header] + ROWS:
	print('  '.join(str(r[i]).ljust(widths[i]) for i in range(5)) + ('  ' + r[5] if r[5] else ''))
outdated = [r for r in ROWS if r[4] in ('major', 'update', 'error')]
print(f'\n{len(outdated)} to update (or failed to check), {sum(r[4] == "wait" for r in ROWS)} waiting, '
	f'{sum(r[4] == "held" for r in ROWS)} held, '
	f'{sum(r[4] == "current" for r in ROWS)} current.')

summary = os.environ.get('GITHUB_STEP_SUMMARY')
if summary:
	with open(summary, 'a') as out:
		out.write('### Versions\n\n| ' + ' | '.join(header) + ' |\n|' + '---|' * len(header) + '\n')
		for r in ROWS:
			out.write('| ' + ' | '.join(str(cell).replace('|', '\\|') for cell in r) + ' |\n')
sys.exit(1 if outdated else 0)
