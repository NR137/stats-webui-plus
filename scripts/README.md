# Scripts

| Folder | What it does |
|---|---|
| `textures/` | `extract_mc_textures.py` takes the Minecraft textures, icons and texts the web UI shows from the client JAR and minecraft.wiki. Runs in every build (`./gradlew extractMcTextures`); downloads are cached in `scripts/.cache/`. |
| `art/` | `banner.py` draws the default header image the mod ships, `icon.py` the mod icon. Both write into `common/src/main/resources/assets/stats_webui_plus/`. |
| `sample_data/` | `sample_data.py` writes nine sample players into a dev server folder, for screenshots and the smoke test. |
| `screenshots/` | `take_screenshots.py` takes every image in [`screenshots/`](../screenshots) from the dev server. |
| `smoke_test/` | `smoke_test.py` runs the built JAR on the official Fabric or NeoForge server with the sample players and checks it; see below. |

All of them run in the dev image (`docker/Dockerfile`, see the main README), whose `python` has Pillow and Playwright with Chromium (`scripts/requirements.txt`). In the VS Code dev container run them directly, e.g. `python scripts/art/banner.py`; otherwise through compose: `docker compose run --rm dev python scripts/art/banner.py`.

## Taking the screenshots again

1. Build once, so the web UI, the Minecraft textures and the client JAR in `scripts/.cache/` exist:
   ```
   docker compose run --rm dev docker/tasks.sh build
   ```
2. Start the Fabric dev server, so it creates `fabric/runs/server`. The first start stops until you accept Mojang's EULA by setting `eula=true` in `fabric/runs/server/eula.txt`; the next start writes the default config. Type `stop` (or press Ctrl+C) to end it.
   ```
   docker compose up server
   ```
3. Write the sample players (stats, advancements and session times for nine play styles, each with a different default skin) and point the config at the screenshot setup: port 25590, the address `play.example.com`, Map and Discord buttons with example links.
   ```
   docker compose run --rm dev python scripts/sample_data/sample_data.py
   ```
4. Start the server again and leave it running; the web UI is at http://localhost:25590/.
   ```
   docker compose up server
   ```
5. In a second terminal, take the screenshots (the `screenshots` service shares the server's network):
   ```
   docker compose run --rm screenshots
   ```
   `docker compose run --rm screenshots python scripts/screenshots/take_screenshots.py player player-de` takes just those. It waits for each page's data and skins; a full run takes about a minute.

**NeoForge instead of Fabric:** the same steps work with the NeoForge dev server. Use `LOADER=neoforge docker compose up server` in steps 2 and 4 (its folder is `neoforge/runs/server`; its dev console doesn't take commands, so stop it with Ctrl+C), and give `sample_data.py` that folder:

```
docker compose run --rm dev python scripts/sample_data/sample_data.py neoforge/runs/server
```

The players and their progress are the same every run (fixed random seed); only the dates move along with the day you run it. Running `sample_data.py` again replaces the earlier sample players.

## Smoke test

Checks the release JARs the way a server owner runs them, on the official Fabric server (with Fabric API) or NeoForge server for the versions in `gradle.properties`:

```
docker compose run --rm dev docker/tasks.sh build
docker compose run --rm dev docker/tasks.sh smoke            # both loaders
docker compose run --rm dev docker/tasks.sh smoke neoforge   # one
```

It writes the sample players (`sample_data/`), starts the server and checks over HTTP that the mod loaded (no mixin errors in the log), that the API answers for every player, board and advancement, that the web UI is served from the JAR, and that a changed config applies while the server runs. Then it stops the server. With `--browser` (which `docker/tasks.sh smoke` passes) it also opens the web UI in headless Chromium: navigation, direct links and reloads, "not found" pages, the four languages and the phone layout. `python scripts/smoke_test/smoke_test.py fabric` alone skips the browser.

It also changes the config while the server runs (an address, then the port, which must move the web server) and restarts the server with `world` pointing at another folder with three of the players.

The servers and their downloads are kept in `scripts/.cache/smoke/`; the world, config and mods are written fresh every run. A run takes under a minute once the server is downloaded. The `tests` workflow runs both loaders with `--browser` on every pull request and before every release.

## Singleplayer test

`client_test.py` checks the mod on a client's integrated server. It starts each loader's dev client (`./gradlew :<loader>:runSmokeClient`, run folder `<loader>/build/run/smokeClient/`) on a virtual screen (Xvfb, OpenGL in software through EGL), which opens a copy of the smoke test's world directly (`--quickPlaySingleplayer`). The integrated server's web UI must answer, list the world's players and show the client's own player as online with a real first join; then the client is closed and its log checked for errors from the mod or its mixins.

```
docker compose run --rm dev docker/tasks.sh client            # both loaders; needs the smoke test first
docker compose run --rm dev docker/tasks.sh client neoforge   # one
```
