# The Stats WebUI Plus API

The mod's website reads everything it shows from a small JSON API, served by the same web server as the website
(`http://<server>:25580/api/...`, see `bind` and `port` in `config/stats_webui_plus.json`). You can use it too: for a
Discord bot, a status page, a scoreboard on your own site, or to keep your own statistics.

The API only reads. It answers `GET` and `HEAD`; anything else is a `405`. The full description, with every field,
type and unit, is the OpenAPI 3.1 file [`openapi.yaml`](../common/src/main/resources/assets/stats_webui_plus/openapi.yaml),
which every server also serves at `/api/openapi.yaml` (open it in any OpenAPI viewer, or generate a client from it).
This is version 2 of the API (`apiVersion` in `/api`).

## Endpoints

| Endpoint | What it answers | Stats refresh |
| --- | --- | --- |
| `/api` | The mod, its version, the Minecraft version, and every endpoint | no |
| `/api/health` | `{"ok": true, ...}` while the web server runs | no |
| `/api/openapi.yaml` | The OpenAPI description | no |
| `/api/status` | Players tracked and online, name clashes, the website's settings (branding, links, pages, languages), the state of the game's textures | no |
| `/api/pages/{id}?lang=de,en` | A configured page (imprint, rules, ...) in the first language that has a text | no |
| `/api/players` | Every player: play time, level, actions per hour, advancements done | yes |
| `/api/players/{player}` | One player: join and logout times, skills with their parts, every board with value and rank, the game's statistics they raised, advancements | yes |
| `/api/players/{player}/advancements/{advancement}` | One advancement for one player, with each criterion and when it was met | yes |
| `/api/rankings` | First place on the boards `legends`, `veterans` and `grinders` | yes |
| `/api/leaderboards` | Every board, and the categories of the leaderboards page | yes |
| `/api/leaderboards/{id}` | One board with its players, ranked | yes |
| `/api/stats` | Every one of the game's statistics, per group, with its leader | yes |
| `/api/stats/{group}/{statistic}` | The board of one of the game's statistics | yes |
| `/api/advancements` | Every advancement the server shows, with its tab and how many players have it | yes |
| `/api/advancements/most` | The board of the most advancements | yes |
| `/api/advancements/{advancement}` | One advancement and everyone who has it, earliest first | yes |

Anything else under `/api` is a `404` with `"error": "not_found"`. A trailing `/` is ignored.

## Players and IDs

- A **player** in a path is their name or their UUID. The exact spelling wins; otherwise the first player whose name
  differs only in case. Floodgate can give two Bedrock players the same name: `/api/status` lists such names in
  `nameClashes`, and `sameName` on a player's page names the others, so use the UUID for them.
- **IDs in paths** are written as on the website: the game's own things without `minecraft:` (`jump`, `story/root`),
  other namespaces with a plain colon (`somemod:thing`, `somemod:quests/first`). The full form (`minecraft:jump`)
  works as well. In the JSON, IDs are always full (`minecraft:jump`).
- Encode each part of a path once, as `encodeURIComponent` does (`+Steve` is `%2BSteve`); the server decodes it once.
- The game's statistics come in nine groups: `custom`, `killed`, `killed_by`, `mined`, `crafted`, `used`, `broken`,
  `picked_up`, `dropped`.

## Numbers, units and times

Values are plain numbers, rounded to 2 decimals (whole numbers come without decimals), never preformatted text: format
them for your readers yourself. Each value has a `unit` next to it (or a fixed one, documented in the OpenAPI file):

| Unit | Meaning |
| --- | --- |
| `level` | The ranking level (see "How we rank" on the website) |
| `skill` | A skill's value; 100 is very good, more is possible |
| `hours`, `km`, `km_per_hour`, `hearts` | Play time and other times, distances, speed, damage (one heart is two health points) |
| `count`, `kills`, `blocks`, `mob_types` | How many |
| `kills_per_hour`, `deaths_per_hour`, `actions_per_hour` | Per hour played |
| `ratio` | A ratio, such as kills per death |

The game's own statistics are counts, except in `custom`: distances (`*_one_cm`) are in km, damage in hearts, times
(`*_time`, `time_since_*`) in hours.

Times are epoch milliseconds (UTC); a time nobody knows is `null`. `firstJoinEstimated` and `lastLogoutEstimated` say
when a time was worked out from the game's files instead of being recorded by the mod.

Boards list the highest value first (also boards with `"lowerWins": true`: that only tells the compare page that fewer
wins there), on a tie whoever played less, then by name. Equal values get different ranks, as listed. Boards leave out
players at 0, except `legends`.

## Caching and compression

- Answers from the stats bring them up to date first, at most once per `refreshCooldownMs` (15 seconds by default),
  so the API costs the game the same however often it is asked. They say
  `Cache-Control: public, max-age=<seconds until the next refresh>`, which browsers and proxies (a Cloudflare cache
  rule for `/api/*`) may follow; with `refreshCooldownMs` 0 they are `no-store`.
- `/api`, `/api/health` and `/api/status` are never cached; pages and `/api/openapi.yaml` are `no-cache`.
- Every successful answer has an `ETag`. Send it back in `If-None-Match`, and an unchanged answer is an empty `304 Not Modified`.
- With `Accept-Encoding: gzip`, answers of 1 KB or more come gzipped (`Content-Encoding: gzip`). A player's page or
  `/api/stats` shrink to a fraction of their size; `curl --compressed` asks for it.

## Errors

Errors are JSON, never cached:

```json
{"error": "not_found", "message": "Player not found"}
```

| `error` | Status | When |
| --- | --- | --- |
| `not_found` | 404 | Unknown endpoint, player, board, statistic, advancement or page |
| `method_not_allowed` | 405 | Anything but `GET` and `HEAD` |
| `bad_request` | 400 | Reserved for requests the API can't read; this version sends none |
| `busy` | 503 | The game didn't hand over the stats within 5 seconds (a lag spike); try again after `Retry-After` (5) seconds |
| `unavailable` | 503 | The Minecraft server isn't running (yet) |
| `internal_error` | 500 | Something failed in the mod; the server log has the details |

Match on `error`; `message` is for people and may change.

## Examples

Who has played the most:

```sh
curl -s --compressed http://localhost:25580/api/rankings | jq '.veterans'
# {"name": "Steve", "uuid": "...", "value": 268, "unit": "hours"}
```

The top three diamond miners:

```sh
curl -s --compressed http://localhost:25580/api/leaderboards/diamonds-mined | jq '.entries[:3][] | "\(.rank). \(.name): \(.value)"'
```

How far a player has walked, and their rank (the statistic is left out while they have none):

```sh
curl -s --compressed http://localhost:25580/api/players/Steve | jq '.statistics.custom["minecraft:walk_one_cm"]'
# {"value": 1181.4, "unit": "km", "rank": 3}
```
