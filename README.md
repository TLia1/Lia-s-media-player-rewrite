# Lia's Media Player — v3

A rewrite of Lia's Media Player. The design, the reasons behind it and the milestones are
in [REWRITE-DESIGN.md](REWRITE-DESIGN.md). This repository is at **milestone M1**: the
headless engine in `core` — tool management, link recognition, resolution through
`yt-dlp -J`, ffmpeg sessions, the player and its queue — plays YouTube links and direct
files from a terminal. The mod loads on Fabric and NeoForge for Minecraft 1.21.1 and
installs its tools at startup, but shows nothing yet; windows come with M2.

## Layout

| Module | What | Minecraft? |
|---|---|---|
| `api/` | Public API, published on its own for addon developers | No |
| `core/` | The engine: links, resolution, tools, playback, library, settings | No |
| `ui/` | The mod's own UI toolkit, windows and screens, drawn through a `Canvas` | No |
| `platform/` | Adapters to the game (`mc/`) and the two loader entrypoints (`fabric/`, `neoforge/`) | Yes |
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
modules. Needs no Minecraft and takes seconds.

The engine's integration tests (tagged `integration`) run real ffmpeg on media they
generate, served by a local HTTP server; they need `ffmpeg` and `ffprobe` on `PATH` (or
`FFMPEG_PATH`/`FFPROBE_PATH`) and skip themselves otherwise. No test touches the network.

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

```bash
./gradlew :platform:1.21.1-fabric:runClient
```

Starts a development client (use `1.21.1-neoforge:runClient` for NeoForge).

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
