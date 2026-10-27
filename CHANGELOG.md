# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

<!-- Replace "unreleased" with the release date (YYYY-MM-DD) before running the release workflow. -->
## [1.3.0] - unreleased

First release of **Stats WebUI Plus**, a fork of [mc-stats-webui](https://github.com/borrageiros/mc-stats-webui) by
borrageiros. It now runs on Fabric and NeoForge, puts almost no load on the Minecraft server, and comes with a
reworked web UI. All changes below are relative to mc-stats-webui 1.2.1.

### Upgrading from mc-stats-webui

- **This is a new mod for the loader.** The mod ID changed to `stats_webui_plus`, so remove the old
  `mc-stats-webui` JAR instead of keeping both.
- **The config starts fresh.** The config file is now `config/stats_webui_plus.json`; `config/mc-stats-webui*.json`
  is not taken over. The old `title`, `slogan` and `tagline` options are replaced by `siteHeading`,
  `siteHeadingSlogan` and `siteHeadingTagline`.
- **Pick the right JAR:** `stats-webui-plus-fabric-<version>.jar` (needs Fabric API) or
  `stats-webui-plus-neoforge-<version>.jar`.

### Added

#### Loaders

- **NeoForge support** (NeoForge `26.3.0.97`) next to Fabric. Both are built from one codebase, and every release
  ships one JAR per loader.

#### Languages and names

- **German web UI.** Minecraft terms follow the game's own German translation.
- **Official Minecraft names** for blocks, items and mobs in the visitor's language (e.g. "Eichenholzbretter",
  "Tablones de roble") instead of names generated from the ID in English. They are generated from Mojang's language
  files at build time and loaded only for the visitor's language.
- `languages` option (default: all): which languages the web UI offers. The language switcher lists them
  alphabetically and hides itself when only one is enabled.
- `defaultLanguage` option (`en`, `es`, `fr` or `de`; default `en`): the language for visitors whose browser
  languages are all unsupported.

#### Site heading, header image and menu

- **Site heading** on every page (`siteHeading`), with a tagline laid out, sized and pulsing like Minecraft's splash
  text (`siteHeadingTagline`, pulsing can be turned off with `siteHeadingTaglinePulse`) and a slogan under it
  (`siteHeadingSlogan`). All three can have text per language; the default config lists them for English, German,
  Spanish and French, and a language left empty shows the English text. The site heading is the page's `<h1>`; page titles below it are `<h2>` with the same look.
- **Header image** at the top of every page: put it at `config/stats_webui_plus/header.png` or point `headerImage` to
  another file in the config folder (`""` turns it off). It is shown at most 120 px high and the content width,
  scaled down but never up. The mod ships a pixel-art banner and writes it there on first start, next to the new
  default config.
  `headerImagePosition` puts it above the site heading (`top`, default), between heading and slogan (`between`) or
  below the slogan (`bottom`).
- **Custom menu links** (`links`), e.g. to a web map, a Discord server or an admin dashboard, with labels per language.
  Three placeholders (Map, Discord, Dashboard) are preconfigured and only appear once a URL is set. A URL must start
  with `http://`, `https://` or a single `/` (a page on this site); `//host` and anything else is hidden with a
  warning in the server log.
- `windowTitle` can have text per language; the default is "{page} - My Server".

#### Status bar

- **Status bar** under the site heading (`statusBar`, on by default): online indicator, the server address
  (`serverAddress`, click to copy) and "3 / 20 players online". It refreshes every 30 seconds while the page is
  visible, pauses in background tabs, refreshes at once when the tab becomes visible again, and shows "Offline" when
  the server stops answering. The online count is always the server's current one.

#### Player pages

- **A URL per tab:** `/players/<name>` (stats) and `/players/<name>/advancements`. The tabs are links, so back and
  forward work and a tab can be opened in a new browser tab.
- **First joined and last online** (or "online now"), shown in the visitor's time zone. Minecraft records neither, so
  the mod keeps join and leave times in `<world>/stats_webui_plus/sessions.json`. For players from before that, both
  are estimated (oldest advancement or recipe, last save of the player's files) and marked with "≈".
- **Advancement progress bar with tiers** by share of completed advancements: Stone, Copper (10 %), Iron (25 %),
  Gold (40 %), Diamond (60 %), Netherite (80 %) and Dragon Egg (100 %). Its tooltip tells how many advancements are
  missing for the next tier. The How we rank page explains the tiers; they don't count towards the ranking.
- **Advancements tab:** in each tab, the finished advancements come first, then the open ones, both sorted by title.
  Every finished advancement shows when the player completed it, in green.
- **3D avatar:** walks, faces the viewer and slowly turns by default; `avatarPose`, `avatarAngle` (degrees) and
  `avatarRotate` set the starting pose, angle and turning. The avatar is zoomed to fill its box, also after resizing
  the window or switching the pose.
- **Default skins:** players without a skin of their own (offline-mode players, accounts that never picked one) get
  Minecraft's default skin for their UUID (Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny or Zuri, wide or slim),
  the same one the game shows, instead of "Skin could not be loaded".

#### Server load and crash safety

- `refreshCooldownMs` option (default `15000`, range `0`–`60000`): API requests within this many milliseconds of the
  last refresh reuse the cached stats. The server thread therefore refreshes them at most once per window, however
  many people browse the site, and stats on the page are at most that old. `0` refreshes on every request.
- `saveIntervalSeconds` option (default `0`, range `0`–`3600`): also saves online players' stats and advancements this
  often, so a server crash loses less. With `0`, Minecraft saves them every 5 minutes and on logout, like on any
  vanilla server. The web UI doesn't need these saves; it reads the stats from memory.

#### Config

- **Config changes apply without a restart:** the mod notices when `config/stats_webui_plus.json` is saved and reads
  it again, and `/reload` does the same (for network drives that don't report changes). A new `port` or `bind`
  rebinds the web server; only a new `world` waits for a restart. A file that can't be read is reported in the log,
  and the previous settings stay.

#### Credits and identity

- A line at the bottom of every page names this fork and the original: "Stats WebUI Plus by NR137, written with AI ·
  fork of mc-stats-webui by borrageiros", linking to both repositories.
- New mod icon: a podium on an inventory slot (the old one only showed the text "mc-stats-webui").

### Changed

#### Name, IDs and license

- **New name and mod ID:** the mod is called Stats WebUI Plus, with the mod ID `stats_webui_plus` on both loaders
  (NeoForge mod IDs can't contain hyphens). `/api/health` reports `"mod": "stats_webui_plus"`.
- **Config files are named after the mod ID** (`config/stats_webui_plus.json`, `config/stats_webui_plus-names.json`),
  so tools that match config files by mod ID, such as VoxelDash, find them.
- The mod metadata lists NR137 and borrageiros (original author) as authors, credits mc-stats-webui and links to this
  fork's repository. The license is MIT with both copyright holders instead of CC0-1.0; a `LICENSE` file is included
  in the repository and the JARs.

#### Almost no load on the server thread

- **Stats are read from memory instead of disk.** mc-stats-webui kept its cache current by having the server thread
  save every online player's stats and advancement files every 15 seconds, read them back, parse them and recompute
  the ranking once per player, even while nobody had the site open. Now nothing happens until the web UI asks for
  stats. Then the server thread only copies the changed players' stats out of memory (advancement progress only
  after an award, a revoke or `/reload`), which takes a fraction of a millisecond. Parsing, scoring and a single
  ranking run on the web server's threads, and concurrent requests share one refresh. Players who went offline are
  read from the files Minecraft saved on logout. The stats on the page are the same as before.
- `/api`, `/api/health` and `/api/status` never refresh the stats, so health checks and the status bar, which every
  open tab polls every 30 seconds, never wake the server thread.
- When the game is too busy to hand over the stats within 5 seconds (a lag spike, a huge command), the API answers
  `503` with `Retry-After` and logs a one-line warning.

#### Faster web UI, less data

- **API responses are cacheable:** `Cache-Control: max-age` is the time left until the stats would be refreshed
  anyway, so browsers and proxies such as Cloudflare never keep a copy older than the mod's own cache.
  `/api/health`, `/api/status`, errors and `refreshCooldownMs` `0` are never cached.
- **Icons, names and pages can be revalidated:** they carry an `ETag`, and a browser that already has a file gets an
  empty `304 Not Modified` instead of the whole file. Icons and names (`/mc/`) may be cached for a day, so repeat
  visits don't download every icon on the page again.
- **Player pages load about 4× less data** (e.g. 840 KB → 190 KB, gzipped 51 KB → 13 KB): vanilla stats the player
  never raised are sent as compact ID lists instead of one full row each, and the web UI rebuilds the identical rows.
  The leaderboards overview (`/api/leaderboards`) sends its stat ID lists the same way (e.g. 290 KB → 190 KB).
- Pages without a name in the URL (`/about`, `/players`, `/leaderboards`, `/compare`, `/advancements`) get the page
  SvelteKit prerendered for them instead of the general fallback page, so the browser loads that page's scripts and
  styles right away.
- **Player skins never hold up the stats.** The Mojang skin proxy asks Mojang on two threads of its own, so a page full
  of player heads can't make the API wait. Skin images are kept in memory (a skin's URL never changes its image),
  and so are player profiles: one older than 5 minutes is served at once and looked up again in the background, so
  a skin change shows up a visit later without anyone waiting. Concurrent requests for the same skin share one
  Mojang request, and at startup the skins of the 100 most played players are fetched in advance. Mojang errors and
  rate limits are never cached.
- Sorting the long stat lists on the player, compare and leaderboard pages is about 4× faster: each name is looked up
  once per sort instead of on every comparison.
- First-join estimates for players the session log never saw are kept until their advancements file changes, instead
  of being worked out from the file on every player page request.

#### Web UI

- **Language detection** checks the browser's whole preference list (Dutch first and English second now gives
  English), falls back to the server's `defaultLanguage` (English by default) instead of Spanish, and only remembers
  a language picked in the switcher.
- **Lists are sorted by name in the visitor's language:** stats with equal values, the stat lists on the leaderboards
  page and the advancements within each tab. Before, they were sorted by English IDs or titles, which looks random
  in other languages.
- **Home page:** the big home title is gone; the home page has a normal, localized "Ranking" page title like the
  other pages, and the menu entry is called Ranking instead of Home.
- Menu order: Ranking, Advancements, Players, Leaderboards, Compare, How we rank.
- **Player page layout:** name and score share the top line; play time, score per hour, first join and last online
  sit in two columns below, using the full width. The advancement tier reads "Stone tier" (tier name highlighted,
  with the tooltip) instead of only "Stone".
- The theme button says what it is about ("Theme: System") instead of only "System".
- **Advancements page:** in each tab, advancements at least one player finished come first, then the rest, both
  sorted by title. "0 players" is faint grey instead of green.
- The player page's advancement filter is called "Achieved only" instead of "Hide locked": the others are not
  locked, the player just hasn't finished them yet.

#### Icons

- **Every stat shows the closest real icon.** Items use the icon rendered from the game's own model, otherwise
  minecraft.wiki's inventory icon. Blocks without an item of their own (End Gateway, End Portal, Nether Portal, fire,
  water, lava, ...) show the block itself, partly animated, and entities that aren't mobs (boats, arrows, minecarts,
  experience orbs, ...) show the entity itself. Only where nothing exists does a related item stand in: wall
  variants show their floor version, potted plants their plant, candle cakes the cake, filled cauldrons the
  cauldron. Crops whose wiki icon is a sprout (carrots, potatoes, beetroots, cocoa, pitcher plant, torchflower) show
  the item they grow.
- **Mobs in kill and death stats are shown with their face** (minecraft.wiki entity sprites) instead of a spawn egg,
  including the player. Mobs sharing an ID with an item no longer show the item (chicken and rabbit showed raw meat,
  fish showed the fish item).

#### Project and build

- The project is split into `common/`, `fabric/` and `neoforge/` (based on MultiLoader-Template); loader-specific
  code sits behind a small `Platform` interface, and all mixins are shared.
- The texture script downloads the Minecraft client JAR into `scripts/.cache/minecraft/` instead of reading Fabric
  Loom's Gradle cache, and caches minecraft.wiki downloads per Minecraft version (`scripts/.cache/invicons/<version>/`,
  `scripts/.cache/mob-sprites/<version>/`), so a version update fetches current icons.
  `./gradlew build -PrefreshTextures` deletes the current version's cached downloads and fetches them again.
- Rate-limited wiki downloads (HTTP 429/503) pause all downloads as long as the wiki asks instead of giving up after a
  few seconds; missing files (404) aren't retried, and the wiki's 1×1 placeholder images count as missing.
- `web/src/lib/mc/icons.json` (which icon each ID uses, no artwork) is tracked in git, so changes to the icon choices
  show up in diffs. The icon images and `advancementLang.json` are Mojang's and stay generated at build time.
- Entity icons are chosen in one place (the texture script) instead of a second table in the web UI.
- The web dev server (`yarn dev`) sends `/mojang` to the running mod like `/api`, instead of straight to Mojang, so
  skins (and default skins) work there as on the real site.
- **Tests:** unit and HTTP tests for the shared Java code (`./gradlew :common:test`: config, ranking rules, session
  log, stats loading and API data, web server) and Vitest tests for the web UI (`corepack yarn test`: tiers,
  translations, API client, language detection). The new `tests` workflow runs them on every pull request and push to
  `main`, and the release workflow runs them first and stops if one fails. An optional pre-commit hook
  (`git config core.hooksPath .githooks`) runs the tests a commit touches.
- New screenshots of nine sample players on the Fabric dev server, taken by `scripts/sample_data/` and
  `scripts/screenshots/`, which take them again for later versions. `scripts/art/` draws the shipped header image
  and the mod icon. All scripts are Python; the build itself needs nothing beyond Python 3.

### Fixed

#### Stats

- **Advancement completion times** (advancement pages, "who got it first", player pages) are when the advancement was
  actually completed. Before, they were when its first criterion was met: "Adventuring Time" showed the first biome
  visited instead of the last one.
- Revoking an advancement (`/advancement revoke`) and progress reset by `/reload` show up on the page.
- After `/reload`, the list of advancements follows the new data packs (added or removed advancements) instead of
  staying as it was at startup.
- Every stat a player has a non-zero value for is listed, even if Minecraft's registries don't know its ID (e.g. items
  from a removed mod, renamed IDs). Before, such values were silently dropped.
- Kill and death stats no longer list vanilla entities that can never appear there (boats, item frames, arrows,
  markers, ...); Minecraft only records these stats for living entities. "Deaths by mob" no longer lists mobs that
  never damage a player (e.g. cows, villagers, horses, cats, armor stands, mannequins), and "Mobs killed" no longer
  lists armor stands, which are removed instead of dying. Entities added by other mods and non-zero values are
  always listed.
- A player's completed advancements come in a fixed order in the API instead of one that changed with every server
  start.

#### Server

- Looking up an unknown player name at Mojang no longer blocks the server thread (up to about 10 seconds per player,
  mostly at startup). Lookups run in the background, and the short UUID is shown until the name arrives.
  Offline-mode UUIDs are no longer sent to Mojang, and failed lookups are retried after an hour instead of on every
  reload.
- The name cache file is written atomically, so it can't be read half-written.
- A stats request that arrived while the server was stopping could reload the cache from the wrong folder and
  leave it empty.

#### Navigation and layout

- After opening a page directly (typed URL, bookmark, reload), the first link click reloaded the whole page, and on
  player pages every tab switch did. The Back button threw an error on the first navigation, which broke the web UI's
  own navigation.
- Long words (e.g. "Blasenkorallenwandfächer", "Nahkampfschaden") overflowed tiles and buttons on narrow screens; they
  now break with a hyphen following the rules of the visitor's language. Only long words are split.
- After switching the language, some icons (e.g. the category icons on the leaderboards page) disappeared until the
  page was reloaded.
- Pages with and without a scrollbar no longer shift the content sideways against each other: room for the scrollbar
  is always kept.
- Empty places on the home page podium no longer show a faded box overlapping the tabs below them.
- The page's language attribute is English instead of Spanish until the visitor's language is applied.
- Player pages of Bedrock players on Geyser/Floodgate servers (names like `.Steve`) and pages of IDs with a dot (e.g.
  from mods) gave a 404 when opened directly or reloaded: any path with a dot was taken for a missing file. Now only
  missing files under `/_app/` and `/mc/` and at the top level (e.g. `/favicon.ico`) are a 404; every other path opens
  the page, which asks the API whether that player or ID exists. Names with `..` in them work too.
- Names with `+` or `%` (possible with other Floodgate prefixes) weren't found: the API decoded them twice, turning
  `+` into a space.
- A player, leaderboard or advancement that doesn't exist says so ("There is no player with this name on this
  server.") instead of asking to try again later. For an unknown vanilla stat the leaderboard page no longer
  downloads every player's profile first.

#### Skins and icons

- Old 64×32 skins whose hat area has no transparent pixel (e.g. Notch's) showed an opaque box over the head. The web
  UI applies Minecraft's own rule for such skins and ignores that hat layer, like the game does.
- Leaves, grass, ferns, bushes, vines and lily pads showed gray icons; they are colored like in the game, using the
  tint colors from Minecraft's item definitions (fixed colors and the grass color map).
- Leather armor, potions, tipped arrows and firework stars showed a gray or incomplete icon; their layers are stacked
  and colored with the default colors from Minecraft's item definitions. Crossbow and light block show their default
  look (unloaded, level 15).
- Calibrated sculk sensor, sculk sensor, sculk shrieker, stonecutter and prismarine slab, stairs and wall have proper
  animated inventory icons. minecraft.wiki serves these as GIFs, which the texture script used to reject, so they
  showed a flat texture or a letter, and every build retried the downloads.
- Air showed an empty image; it shows the barrier, like cave air and void air.
- The texture script's PNG reader ignored transparency in grayscale and RGB images, which made some generated icons
  (e.g. spruce leaves) black where they should be see-through.

### Removed

- The 15-second save-and-reload cycle of the stats cache (see "Almost no load on the server thread"; for crash safety
  see `saveIntervalSeconds`).
- The config options `title`, `slogan` and `tagline` (replaced by the site heading options).
- The unused client source set (the Fabric template's example mixin and empty client entrypoint).

## Earlier versions

Versions up to 1.2.1 were Fabric-only releases of mc-stats-webui; see the git history.
