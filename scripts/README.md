# Scripts

| Folder | What it does | Needs |
|---|---|---|
| `textures/` | `extract_mc_textures.py` takes the Minecraft textures, icons and texts the web UI shows from the client JAR and minecraft.wiki. Runs in every build (`./gradlew extractMcTextures`); downloads are cached in `scripts/.cache/`. | Python 3 only |
| `art/` | `banner.py` draws the default header image the mod ships, `icon.py` the mod icon. Both write into `common/src/main/resources/assets/stats_webui_plus/`. | Pillow |
| `sample_data/` | `sample_data.py` writes nine sample players into the Fabric dev server, for screenshots. | Python 3 only |
| `screenshots/` | `take_screenshots.py` takes every image in [`screenshots/`](../screenshots) from the dev server. | Playwright |
| `smoke_test/` | `smoke_test.py` runs the built JAR on the official Fabric or NeoForge server with the sample players and checks it; see below. | Python 3 only; Playwright for `--browser` |

The build needs nothing but Python 3. For the other scripts, one shared environment holds Pillow and Playwright (from the repository root):

```
python3 -m venv scripts/.venv
scripts/.venv/bin/pip install -r scripts/requirements.txt
```

On Windows the interpreter is `scripts\.venv\Scripts\python`. Run the scripts with that interpreter, e.g. `scripts/.venv/bin/python scripts/art/banner.py`.

## Taking the screenshots again

1. Build once, so the web UI, the Minecraft textures and the client JAR in `scripts/.cache/` exist:
   ```
   ./gradlew build
   ```
2. Start the Fabric dev server, so it creates `fabric/runs/server`. The first start stops until you accept Mojang's EULA by setting `eula=true` in `fabric/runs/server/eula.txt`; the next start writes the default config. Type `stop` (or press Ctrl+C) to end it.
   ```
   ./gradlew :fabric:runServer
   ```
3. Write the sample players (stats, advancements and session times for nine play styles, each with a different default skin) and point the config at the screenshot setup: port 25590, the address `play.example.com`, Map and Discord buttons with example links.
   ```
   python3 scripts/sample_data/sample_data.py
   ```
4. Start the server again and leave it running; the web UI is at http://127.0.0.1:25590/.
   ```
   ./gradlew :fabric:runServer
   ```
5. In a second terminal, install Playwright's headless Chromium once (about 110 MB, in `~/.cache/ms-playwright`) and take the screenshots:
   ```
   scripts/.venv/bin/playwright install --only-shell chromium
   scripts/.venv/bin/python scripts/screenshots/take_screenshots.py
   ```
   `take_screenshots.py player player-de` takes just those. It waits for each page's data and skins; a full run takes about a minute.

## Smoke test

Checks the release JARs the way a server owner runs them, on the official Fabric server (with Fabric API) or NeoForge server for the versions in `gradle.properties`:

```
./gradlew build
python3 scripts/smoke_test/smoke_test.py fabric
python3 scripts/smoke_test/smoke_test.py neoforge
```

It writes the sample players (`sample_data/`), starts the server and checks over HTTP that the mod loaded (no mixin errors in the log), that the API answers for every player, board and advancement, that the web UI is served from the JAR, and that a changed config applies while the server runs. Then it stops the server. With `--browser` (run with the Playwright environment, e.g. `scripts/.venv/bin/python scripts/smoke_test/smoke_test.py fabric --browser`) it also opens the web UI in headless Chromium: navigation, direct links and reloads, "not found" pages, the four languages and the phone layout.

The servers and their downloads are kept in `scripts/.cache/smoke/`; the world, config and mods are written fresh every run. A run takes under a minute once the server is downloaded. The `tests` workflow runs both loaders with `--browser` on every pull request and before every release.

**NeoForge instead of Fabric:** the same steps work with the NeoForge dev server. Use `./gradlew :neoforge:runServer` in steps 2 and 4 (its folder is `neoforge/runs/server`; its dev console doesn't take commands, so stop it with Ctrl+C), and give `sample_data.py` that folder:

```
python3 scripts/sample_data/sample_data.py neoforge/runs/server
```

The players and their progress are the same every run (fixed random seed); only the dates move along with the day you run it. Running `sample_data.py` again replaces the earlier sample players.
