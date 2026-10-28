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
		python scripts/textures/extract_mc_textures.py --version "$(minecraft_version)" --lang-only
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
		test) # The quick tests: test-java and test-web
			task test-java
			task test-web ;;
		gametest) # Mock player game tests on Fabric and NeoForge
			./gradlew :fabric:runGameTest :neoforge:runGameTestServer ;;
		smoke) # Built JARs on the official servers, with browser checks; needs build first
			if [ -n "${2:-}" ]; then
				python scripts/smoke_test/smoke_test.py "$2" --browser
			else
				python scripts/smoke_test/smoke_test.py fabric --browser
				python scripts/smoke_test/smoke_test.py neoforge --browser
			fi ;;
		all) # Everything CI runs
			task test
			task build
			task gametest
			task smoke ;;
		*)
			echo "Usage: docker/tasks.sh <task> [loader]"
			grep -E '^\s+[a-z-]+\) #' "$0" | sed -E 's/^\s+([a-z-]+)\) # /  \1\t/'
			exit 1 ;;
	esac
}

task "${1:-help}" "${2:-}"
