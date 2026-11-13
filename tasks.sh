#!/usr/bin/env bash
# The project's build and test commands, in one place for you, compose.yaml, the CI workflows and the pre-commit hook.
# Usually run in the dev image (docker/Dockerfile): docker compose run --rm dev ./tasks.sh test. Inside the VS Code dev
# container, or with the tools of .tool-versions installed locally, run it directly: ./tasks.sh test
set -euo pipefail
cd "$(dirname "$0")"

minecraft_version() {
	sed -n 's/^minecraft_version=//p' gradle.properties
}

# What a change touches, by its files: "jar" when the mod JAR can change (a release), "tools" when only tests, scripts,
# the dev image or CI change (all tests, no release), "docs" when only texts and screenshots change (no tests needed).
# Prints the group of the biggest kind among the given files (one per line on stdin).
change_kind() {
	local kind=docs file
	while IFS= read -r file; do
		case "$file" in
			'') ;;
			*/src/test/* | */src/gametest/* | */src/loadtest/* | *.test.ts) kind=tools ;;
			common/* | fabric/* | neoforge/* | web/* | build-logic/* | *.gradle | gradle.properties | gradle/* | gradlew* | \
				LICENSE | .tool-versions)
				echo jar
				return ;;
			*.md | screenshots/* | docs/*) ;;
			*) kind=tools ;;
		esac
	done
	echo "$kind"
}

# The files changed since the merge base with the given ref (the pull request's target branch).
changed_files() {
	git diff --name-only "$(git merge-base "$1" HEAD)" HEAD
}

# main only has released versions, so CHANGELOG.md's top section must be a release: mod_version with a date, and no
# Unreleased section. With a base ref (the pull request's target branch): a change that touches the mod JAR is a new
# release, so mod_version must differ from the base's.
check_changelog() {
	local version heading date previous base_version
	version=$(sed -n 's/^mod_version=//p' gradle.properties)
	if [ -n "${1:-}" ] && [ "$(changed_files "$1" | change_kind)" = jar ]; then
		base_version=$(git show "$(git merge-base "$1" HEAD):gradle.properties" | sed -n 's/^mod_version=//p')
		if [ "$version" = "$base_version" ]; then
			echo "This change touches the mod JAR, so it is a new release: raise mod_version (still $version) and add its CHANGELOG.md section" >&2
			return 1
		fi
	fi
	if grep -Eiq '^## \[unreleased\]' CHANGELOG.md; then
		echo "CHANGELOG.md has an Unreleased section; main only has released versions" >&2
		return 1
	fi
	heading=$(grep -m1 '^## \[' CHANGELOG.md || true)
	if [ "${heading%% - *}" != "## [$version]" ]; then
		echo "CHANGELOG.md's top section is '$heading', but mod_version in gradle.properties is $version" >&2
		return 1
	fi
	if ! [[ "$heading" =~ \ -\ ([0-9]{4}-[0-9]{2}-[0-9]{2})$ ]] || ! date -d "${BASH_REMATCH[1]}" > /dev/null 2>&1; then
		echo "Give the release a date in CHANGELOG.md: '$heading' should be '## [$version] - YYYY-MM-DD'" >&2
		return 1
	fi
	# Catches typos in the date (ISO dates compare as strings).
	date=${BASH_REMATCH[1]}
	if [[ "$date" > "$(date -u +%F)" ]]; then
		echo "CHANGELOG.md: $version's date $date is in the future (today is $(date -u +%F) UTC)" >&2
		return 1
	fi
	previous=$(grep '^## \[' CHANGELOG.md | sed -n '2s/.* - \([0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}\)$/\1/p')
	if [ -n "$previous" ] && [[ "$date" < "$previous" ]]; then
		echo "CHANGELOG.md: $version's date $date is before the previous release's ($previous)" >&2
		return 1
	fi
	echo "CHANGELOG.md: $heading"
}

task() {
	case "$1" in
		build) # Both JARs, with the web UI and textures
			./gradlew build ;;
		test-java) # JUnit tests of common/
			./gradlew :common:test ;;
		test-web) # Type check and Vitest tests of web/
			(cd web && corepack yarn install --immutable && corepack yarn check && corepack yarn test) ;;
		changelog) # CHANGELOG.md's top section = mod_version, dated; with a base ref, a JAR change raises mod_version
			# Base ref: ./tasks.sh changelog origin/main
			check_changelog "${2:-}" ;;
		changes) # What the changes since a base ref touch: jar, tools or docs (./tasks.sh changes origin/main)
			changed_files "${2:?changes needs a base ref, e.g. origin/main}" | change_kind ;;
		versions) # Every version the project uses against the newest one (needs a network)
			python3 scripts/versions/check_versions.py ;;
		test) # The quick tests: test-java and test-web
			task test-java
			task test-web ;;
		gametest) # Mock player game tests on Fabric and NeoForge
			./gradlew :fabric:runGameTest :neoforge:runGameTestServer ;;
		smoke) # Built JARs on the official servers, with browser checks; needs build first
			# The sample players' items come from the game's client JAR (downloaded once).
			./gradlew -q :common:downloadClientJar
			if [ -n "${2:-}" ]; then
				python3 scripts/smoke_test/smoke_test.py "$2" --browser
			else
				python3 scripts/smoke_test/smoke_test.py fabric --browser
				python3 scripts/smoke_test/smoke_test.py neoforge --browser
			fi ;;
		client) # Singleplayer on each loader's dev client, on a virtual screen; needs smoke first (its world)
			if [ -n "${2:-}" ]; then
				python3 scripts/smoke_test/client_test.py "$2"
			else
				python3 scripts/smoke_test/client_test.py fabric
				python3 scripts/smoke_test/client_test.py neoforge
			fi ;;
		all) # Everything CI runs
			task test
			task build
			task gametest
			task smoke
			task client ;;
		*)
			echo "Usage: ./tasks.sh <task> [loader or base ref]"
			grep -E '^\s+[a-z-]+\) #' "$0" | sed -E 's/^\s+([a-z-]+)\) # /  \1\t/'
			exit 1 ;;
	esac
}

task "${1:-help}" "${2:-}"
