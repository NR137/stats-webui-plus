# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.0.1] - 2026-11-13

The first public release of Stats WebUI Plus, for Minecraft 26.3: a mod for Fabric and NeoForge servers that gives the
server a website and JSON API with its rankings, statistics and advancements. Written from scratch, inspired by
mc-stats-webui by borrageiros.

- **A ranking by level:** seven skills (Milestones, Combat, Exploration, Mining, Building, Farming, Survival), each
  counting how many different things a player did and how much, so farms and AFK time can't buy the top. Three
  rankings on a podium: **Legends** (level), **Veterans** (play time) and **Grinders** (actions per hour). "How we
  rank" explains every number, and each player's page shows what their skills are made of.
- **Advancements** on the game's frames, as tiles or as the game's own tree: who has the most, who unlocked a
  specific one, and a page per player and advancement with the parts done and still missing (every biome, every
  mob, a balanced diet, …). Hidden advancements stay hidden unless asked for.
- **Statistics and leaderboards:** the game's whole statistics catalog in its groups, each statistic with its board,
  plus a selection of boards that combine statistics (ores, distance, kills per death, …).
- **Player pages and compare:** play time, first join and last online, the level with its experience bar, a 3D avatar
  with the player's skin; two players compared row by row.
- **The game's look, drawn by the mod:** at the first start with a Minecraft version, the mod downloads the game's
  client and language files from Mojang, as the launcher does, and draws every icon from them: blocks, items, mob
  faces, biome scenes, animated where the game animates. The mod itself contains no files of the game
  (`downloadGameAssets` turns the download off).
- **The website:** the menu as the game's hotbar; the themes Birch (light) and Deepslate (dark); English, Spanish,
  French and German with the game's own names; a start page of your own (`startPage`) and pages of your own
  (imprint, rules, … in Markdown, HTML or text); a header image, the site heading in one of two fonts (or your
  own) with a slogan in one of five looks, page title fonts, menu links, your own favicon.
- **Link previews:** a link to the website shared on Discord, WhatsApp and the like shows a card with the heading,
  slogan and header image; a player's page shows the player and their level (`siteUrl` for reverse proxies).
- **Floodgate (Geyser):** Bedrock players' skins and names from GeyserMC's Global API; players with the same name are
  told apart.
- **A JSON API** for bots, status pages and scoreboards, documented in [docs/api.md](docs/api.md) and an OpenAPI 3.1
  file every server serves at `/api/openapi.yaml`.
- **Light on the server:** the stats are kept in memory and refreshed at most once per `refreshCooldownMs`; answers
  are cached by browsers and proxies until then, and compressed.
