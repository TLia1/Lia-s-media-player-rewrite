# Lia's Media Player — v3

A rewrite of Lia's Media Player. The design, the reasons behind it and the milestones are
in [REWRITE-DESIGN.md](REWRITE-DESIGN.md). This repository is at **milestone M6**: on top of
the headless engine of M1, the windows of M2, the chat links of M3, the library and settings
of M4 and the addons of M5 (API 4), the mod builds for **Minecraft 1.21.1, 1.21.11 and 26.3,
on both NeoForge and Fabric**.

- **Playlists** (the "Playlists" chip over the chat, or its key): create, rename, search,
  add links (a YouTube playlist or SoundCloud set is expanded into its tracks), drag entries
  into order, remove them, play in order or shuffled, looping or not, copy out as m3u, make
  one from the links on the clipboard, delete (twice, to be sure).
- **History and favourites**: everything played, most recent first; the heart keeps an
  entry for good, "Clear" keeps the hearts. Play again, or add to the playlist you came from.
- **Settings** (the pause menu's button, Mod Menu or NeoForge's mod list, or a key):
  generated from every setting's spec, with groups, search, descriptions, warnings and a
  reset per row; changes apply at once. Picture height, the chat link filter, the picture
  memory, yt-dlp updates, the theme (dark, high contrast, Minecraft, light), where new
  windows open, the window caps, the hover preview — and the tools' state with "Update the
  tools".
- **Windows remember**: the next window of a kind opens where the last was left, with its
  loop, shuffle and queue. Each player has a queue panel (double click to play, drag to
  reorder), previous and next, a heart, copy-link (Shift: at the current time), theatre
  mode, and hide; hidden players keep playing and a banner says what they start.
- **Keys**: v2's twelve key mappings (unbound by default), and over the chat v2's shortcuts —
  Space, ←/→ (Shift: further) while nothing is typed, Ctrl+M/L/S/N/P/F/↑/↓ always.
- **Upgrading from v2**: the first start imports v2's `config.json`, `playlists.json`,
  `history.json` and `windows.json`, and keeps them as `*.v2.bak`.
- **Addons** (API 4, Minecraft-free): one `MediaPlayerAddon` per addon, found through the
  `liasmediaplayer` entrypoint on Fabric and `LiasMediaPlayerApi.registerAddon` on NeoForge. They
  add link sources, resolvers, themes, buttons on the windows, Ctrl+key shortcuts, key bindings,
  settings and interceptors, listen to playback events, and play links through handles. The
  [API guide](docs/API.md), the [porting guide from 3.x](docs/PORTING.md), and an
  [example addon](examples/example-addon) that is one jar for both loaders.

Not yet at parity: v2's ±10 s buttons on the video window (the arrow keys seek), thumbnails
in the playlist and history lists (titles are there; pictures come with the disk cache of
M7), and the "sound only when hidden" setting (§20, decision 6; hidden players keep
decoding, as in v2). Of v2's API, surfaces, sound with no window, positional sound, the
watch-together hooks, picture decoders, screen tabs and the counters are not back yet; each has a
`Capability` that says so (see the [API guide](docs/API.md#versions-and-capabilities)).

## Trying it in game

```bash
./gradlew :platform:1.21.1-fabric:runClient
```

Open a world, then type a command (the link is the rest of the line and needs no quotes; a
trailing `true` opens a new window instead of queueing into the front one):

```
/show video https://www.youtube.com/watch?v=aqz-KE-bpKQ
/show audio https://soundcloud.com/… true
/show image https://tenor.com/view/…
```

Or post a link in chat and click the label it becomes.

Open the chat (`T`) to use the window's controls and the "Playlists" chip; the settings are
behind the pause menu's "Media Player Settings". "Open the media controls" in the game's
controls screen (unbound by default) shows the windows live without the chat. The first
start downloads yt-dlp and ffmpeg into the game directory's `liasmediaplayer/bin/` unless they are found on
the machine.

## Layout

| Module | What | Minecraft? |
|---|---|---|
| `api/` | Public API, published on its own for addon developers | No |
| `core/` | The engine: links, resolution, tools, playback, library, settings | No |
| `ui/` | The mod's own UI toolkit (widgets, layout, input, themes), the windows, and the screens, drawn through a `Canvas` | No |
| `platform/` | Adapters to the game (`mc/`) and the two loader entrypoints (`fabric/`, `neoforge/`) | Yes |
| `examples/example-addon/` | An addon built against the API alone, one jar for both loaders | No |
| `docs/` | The API guide and the porting guide | — |
| `build-logic/` | Gradle convention plugins | — |

`api`, `core` and `ui` are compiled once for Java 21 and merged into every mod jar.
`platform` is built by [Stonecutter](https://stonecutter.kikugie.dev) once per Minecraft
version and loader, as `platform/versions/<mc>-<loader>`.

Rules the build enforces:

- `api`, `core` and `ui` never import Minecraft, a loader or LWJGL (ArchUnit tests).
- Stonecutter `//?` guards appear only under `platform/` (`checkVersionGuards`).
- `platform/mc` never imports a loader; each loader package never imports the other.
- The plain modules compile with `-Werror`, Error Prone and NullAway (packages are
  `@NullMarked`), are formatted with palantir-java-format, and need 70 % line coverage.

## Building

```bash
./gradlew verifyModules
```

Runs the tests, coverage, formatting check, layering tests and guard check of the plain
modules, and builds and tests the example addon. Needs no Minecraft and takes seconds.

The integration tests (tagged `integration`) run real ffmpeg on media they generate, served
by a local HTTP server; they need `ffmpeg` and `ffprobe` on `PATH` (or
`FFMPEG_PATH`/`FFPROBE_PATH`) and skip themselves otherwise. One of them plays a clip from
`/show` into a video window drawn on a recording canvas, the whole path short of the game.
The UI's tests also check that the language files hold exactly the keys the code uses. No
test touches the network.

```bash
./gradlew spotlessApply
```

Formats the plain modules.

```bash
./gradlew :core:playUrl --args="--script 'wait 10; p; wait 3; p; s 120; wait 8; q' https://www.youtube.com/watch?v=aqz-KE-bpKQ"
```

The CLI harness: plays a link through the real engine, sound to the real sound card,
and prints the player's state every second. Leave out `--script` to type commands
(`p` pause/resume, `s <seconds>` seek, `f`/`b` ±10 s, `v <0..1>` volume, `n` next,
`shot [file.png]` save the current frame, `q` quit); add `--audio` for sound only or
`--height 1080`. Tools are found on the machine or downloaded into
`run/harness/liasmediaplayer/bin/`.

```bash
./gradlew :platform:buildAll
```

Builds every target; jars land in `platform/versions/<target>/build/libs/`.

| Minecraft | Java | NeoForge | Fabric API | Mod Menu |
|---|---|---|---|---|
| 1.21.1 | 21 | 21.1.230 | 0.116.15 | 11.0.4 |
| 1.21.11 | 21 | 21.11.48 | 0.141.6 | 17.0.1 |
| 26.3 | 25 | 26.3.0.99 | 0.165.0 | 21.0.0 |

The 26.3 jar loads on any 26.3.x. The 26.x targets build with a Java 25 toolchain, which
Gradle downloads if none is installed.

```bash
./gradlew :platform:1.21.1-fabric:runClient
```

Starts a development client (use `1.21.1-neoforge:runClient` for NeoForge). Add
`-Plmp.exampleAddon` to load the example addon in it as well.

The matrix is declared once, in `settings.gradle.kts`. Per-version dependencies live in
`platform/stonecutter.properties.toml`; tool and library versions in
`gradle/libs.versions.toml`.

To work on another target, switch `platform/src` with
`./gradlew "Set active project to <mc>-<loader>"`, and run
`./gradlew "Reset active project"` before committing.

## License

The mod is licensed under the [GNU General Public License v3.0](LICENSE).

The public API in `api/` is licensed under the [MIT License](api/LICENSE), so addons under
any license can depend on it. Its notice ships in the API jar, and as `LICENSE-API` in
every mod jar.
