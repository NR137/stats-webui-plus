# Font: mac's Minecraft

The web UI's font is **mac's Minecraft** ("Main" version) by macimas, <https://github.com/macimas-fonts/macsMinecraft>,
under the SIL Open Font License 1.1 ([OFL.txt](OFL.txt)). It is a recreation of the Minecraft font, not a file of the
game. The files are unchanged; `lib/theme/base.css` loads them, and the Licenses page (`/licenses`) shows the license.

## Where the files come from

- Release **1.03** (2024-04-07), the latest stable one when taken on 2026-11-08 (1.04-dev1 and 1.04-dev2 are
  prereleases): <https://github.com/macimas-fonts/macsMinecraft/releases/tag/1.03>, tag commit
  `2a3e4271f56b47b815f52231afcc78e5472fdb02`
- The four `.woff2` files: the release asset `macsMinecraft+woff2.zip`, unpacked
  (SHA-256 `95a02a3cfddf2d2d21b7307899c4d8be94cc42d3d2e968e8feaf1c2ed2dd2ed6`)
- `OFL.txt`: the repository's `LICENSE` at tag 1.03 (the zip has no license file)

| File | SHA-256 |
| --- | --- |
| `macsMinecraft-Regular.woff2` | `ced10c6e33cfa437c0ce09c5b125c6d9c34f2d8f12df0741d6a310b367c9377c` |
| `macsMinecraft-Bold.woff2` | `95131ff967b37ad0e653ca72cd0dbeaee42f49bf1c4dfbfd5c631ae3b89e17ab` |
| `macsMinecraft-Italic.woff2` | `bad2b2019af5941f9c985d09766937d730708f71677c826dfb838b96cd0c30c0` |
| `macsMinecraft-BoldItalic.woff2` | `bbfdbc5f218adbaeb1cc0945f04a7292938b0d8938776853633e821a56c5e709` |
| `OFL.txt` | `181e93a171a4eaa8ae5e8519be253f3d3bc9a78d10b4bc8819e709c814fc4a30` |

To check or fetch them again:

```bash
gh release download 1.03 -R macimas-fonts/macsMinecraft -p 'macsMinecraft+woff2.zip'
unzip 'macsMinecraft+woff2.zip' && sha256sum *.woff2
gh api 'repos/macimas-fonts/macsMinecraft/contents/LICENSE?ref=1.03' --jq .content | base64 -d > OFL.txt
```

When updating to a newer release, replace all files, update this table and check that the characters the UI uses are
still there (the triangles ▶ ▼ ▲, `’`, `„“`, `…`, `œ`, `–`, `≈`, `●`, `↑`).
