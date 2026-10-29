#!/usr/bin/env bash
# Build and test commands, in one place for compose.yaml, the CI workflows and the pre-commit hook. Runs inside the
# dev image (docker/Dockerfile), e.g.: docker compose run --rm dev docker/tasks.sh test
set -euo pipefail
cd "$(dirname "$0")/.."

minecraft_version() {
	sed -n 's/^minecraft_version=//p' gradle.properties
}

# The web code imports advancementLang.json, which is generated and not in git; this makes only that file.
web_texts() {
	if [ ! -f web/src/lib/mc/advancementLang.json ]; then
		python3 scripts/textures/extract_mc_textures.py --version "$(minecraft_version)" --lang-only
	fi
}

task() {
	case "$1" in
		build) # Both JARs, with the web UI and textures
			./gradlew build ;;
		test-java) # JUnit tests of common/
			./gradlew :common:test ;;
		web-texts) # The generated texts the web code imports
			web_texts ;;
		test-web) # Type check and Vitest tests of web/
			web_texts
			(cd web && corepack yarn install --immutable && corepack yarn check && corepack yarn test) ;;
		test-scripts) # Python tests of scripts/ (texture script helpers)
			python3 -m unittest discover -s scripts/textures -p 'test_*.py' ;;
		test) # The quick tests: test-java, test-web and test-scripts
			task test-java
			task test-web
			task test-scripts ;;
		gametest) # Mock player game tests on Fabric and NeoForge
			./gradlew :fabric:runGameTest :neoforge:runGameTestServer ;;
		smoke) # Built JARs on the official servers, with browser checks; needs build first
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
			echo "Usage: docker/tasks.sh <task> [loader]"
			grep -E '^\s+[a-z-]+\) #' "$0" | sed -E 's/^\s+([a-z-]+)\) # /  \1\t/'
			exit 1 ;;
	esac
}

task "${1:-help}" "${2:-}"
