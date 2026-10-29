# Lia's Media Player — Rewrite Design (v3)

This document is the blueprint for rewriting Lia's Media Player from scratch. It is
based on a read of the v2 codebase (`Lia-s-media-player`, mod 2.0.1 / API 3.4.0,
~34k lines of Java in 200 files), its docs (`README.md`, `FEATURES.md`,
`TECHNICAL-DETAILS.md`, `API-DOCUMENTATION.md`) and its build.

The rewrite has five goals, in this order:

1. **Isolate everything that depends on the Minecraft version or the mod loader** in
   one place, so most of the code compiles once, as plain Java, and can be tested
   without the game.
2. **Code quality**: explicit ownership, no global mutable state, small classes with
   one job, enforced layering, and tests that cover the logic rather than only the
   arithmetic helpers.
3. **Performance**: nothing blocking on the client thread, no per-frame allocation
   in steady state, fewer processes per player, and better picture quality for the
   same CPU.
4. **Extensibility**: one addon entry point, typed registries, and a public API that
   does not change between Minecraft versions.
5. **Modern Java**: records, sealed types, pattern matching, virtual threads, and
   `java.net.http`.

Exact feature parity is **not** a goal. Where v2 has a behaviour that exists only
because of an implementation constraint, the rewrite changes it, and this document
says so.

---

## Table of contents

1. [Feature inventory](#1-feature-inventory)
2. [What v2 taught us](#2-what-v2-taught-us)
3. [Key decisions](#3-key-decisions)
4. [Architecture](#4-architecture)
5. [The platform seam (version- and loader-specific code)](#5-the-platform-seam)
6. [Media pipeline](#6-media-pipeline)
7. [User interface](#7-user-interface)
8. [Chat integration](#8-chat-integration)
9. [Persistence and settings](#9-persistence-and-settings)
10. [Threading model](#10-threading-model)
11. [Public API v4 and extension points](#11-public-api-v4-and-extension-points)
12. [Performance budget](#12-performance-budget)
13. [Errors, diagnostics and logging](#13-errors-diagnostics-and-logging)
14. [Security and trust](#14-security-and-trust)
15. [Build, tooling and quality gates](#15-build-tooling-and-quality-gates)
16. [Testing strategy](#16-testing-strategy)
17. [Modern Java conventions](#17-modern-java-conventions)
18. [Migrating from v2](#18-migrating-from-v2)
19. [Milestones](#19-milestones)
20. [Decisions on the open questions](#20-decisions-on-the-open-questions)

---

## 1. Feature inventory

This is the v2 feature set, used as the specification. Each row says whether v3
keeps it, changes it or drops it.

### 1.1 Chat and links

| Feature | v3 | Notes |
|---|---|---|
| Rewrite media URLs in chat into labels (`[picture]`, `[gif]`, `[video]`, `[youtube]`, `[audio]`, …) | Keep | Applied at **display time** in one hook, not in two loader-specific receive paths (§8). |
| Hover an image label to preview it (GIFs animate) | Keep | |
| Click to pin an image / play a video / open the audio bar | Keep | Same modifiers: **Shift** = new window, **Alt** = audio only. |
| Domain allow/block lists and blocked senders | Keep | Moved into the policy layer, next to addon interceptors. |
| `/show <type> <url> [newPlayer]` command | Keep | |
| "Play the link on the clipboard" key | Keep | |

### 1.2 Supported media

| Source | Kind | v3 |
|---|---|---|
| Direct image files (png, jpg, gif, bmp) | Image | Keep. **New:** WebP, AVIF and APNG through the ffmpeg fallback decoder (§6.9). Discord serves many images as WebP. |
| Tenor, Giphy share pages | Image | Keep |
| Direct video files, HLS/DASH manifests | Video | Keep |
| YouTube, Twitch, Vimeo, Streamable, Reddit | Video | Keep. **Changed:** YouTube plays at the configured resolution (720p/1080p) instead of being limited to progressive formats (§6.4). |
| YouTube playlists | Collection | **Generalised**: any yt-dlp playlist (YouTube playlists, SoundCloud sets, Bandcamp albums) expands into a queue (§6.3). |
| Direct audio files, SoundCloud, Bandcamp | Audio | Keep |

### 1.3 Players and windows

| Feature | v3 | Notes |
|---|---|---|
| Video window: play/pause, ±10 s, seek bar, volume, next, loop (off/all/one), shuffle, queue panel | Keep | |
| Compact audio bar with the same transport and queue | Keep | Same engine as video (§6.5). |
| Pinned image windows | Keep | |
| Move, resize, Ctrl+wheel zoom, snapping (Shift disables), theatre mode | Keep | |
| Corner buttons: favourite, copy link (Shift = with timestamp), open in browser, hide, close | Keep | |
| Background playback when hidden, "now playing" banner, HUD drawing during gameplay | Keep | |
| Remembered window placement per kind | Keep | |
| Spinner while loading or seeking, error panel with cause, advice, Retry and "Update the tools" | Keep | |
| Media controls screen (windows without the chat) | Keep | |
| Shortcuts over chat (Space, arrows, Ctrl+letter) | Keep | |

### 1.4 Library, settings and tools

| Feature | v3 | Notes |
|---|---|---|
| Named playlists: create, rename, reorder (drag), search, play, shuffle, loop, clipboard import/export, m3u | Keep | |
| History with favourites (favourites never evicted) | Keep | |
| Settings screen with groups, search, reset-to-default, descriptions | Keep | Rebuilt on the mod's own toolkit (§7). |
| Themes (dark, high contrast, Minecraft, light) and addon themes | Keep | Immutable theme objects instead of static fields. |
| Automatic download of ffmpeg/ffprobe/yt-dlp, stale yt-dlp detection and update | Keep | |
| Keybinds (play/pause, next, previous, volume, mute, hide all, close all, controls screen, playlists, settings, clipboard) | Keep | |

### 1.5 Addon API

| Feature | v3 | Notes |
|---|---|---|
| Register sources, config options, themes, window buttons, keybinds, window shortcuts, image decoders, interceptors, screen tabs | Keep | All through one `AddonContext` (§11). |
| Handles, `MediaRequest`, queue editing, playback events | Keep | |
| Surfaces (render media into a texture), headless audio, mixer channels, positional audio | Keep | One ownership model for all of them (§6.6). |
| Watch-together hooks (`SyncAction`, drift correction) | Keep | |
| Stats and failure log | Keep | |
| The API 2.0 `long`-id methods (`playVideo`, `togglePause(long)`, …) | **Drop** | Superseded by handles. A fresh major version is the moment to remove them. |
| Minecraft types in API signatures (`Component`, `GuiGraphics`, `ResourceLocation`, `Vec3`, `Entity`) | **Drop** | The API becomes Minecraft-free and identical on every target (§11). |

---

## 2. What v2 taught us

### 2.1 Keep these ideas

- **A single seam for the loader.** `platform.ClientHooks` names the thirteen
  moments the mod needs from the game, and two thin bridges forward to it. v3 keeps
  this shape and extends it to the Minecraft version as well.
- **External ffmpeg/yt-dlp instead of bundled natives.** It keeps the jar small,
  avoids per-platform native builds, and lets yt-dlp be updated on its own schedule.
- **The audio clock as master, with no `-re`.** The decoder reads ahead, a buffer
  absorbs network jitter, and the picture follows the sound.
- **Deferred texture release.** From 26.1 a GUI frame is extracted into commands
  and executed later, so a texture must never be freed during a frame. v3 builds this
  rule into the texture service instead of relying on discipline.
- **Disjoint source kinds.** A link is claimed by one feature only.
- **The trust model for downloaded binaries** (TLS to named publishers, magic-byte
  check, zip-slip protection) and the honest reasoning around checksums.
- **The documentation habit.** The v2 docs explain *why*, which is what made this
  design possible. Keep it, but in shorter documents.

### 2.2 Change these

| Problem in v2 | Evidence | v3 answer |
|---|---|---|
| Version-specific code is spread across the tree | Stonecutter `//?` guards in **28 files** across `gui`, `chat`, `media`, `image`, `input`, `tools` and `api`; token replacements rewrite the whole `src/` when switching targets | All version-specific code lives in `platform/mc`. Guards elsewhere fail the build (§5, §15). |
| Almost everything imports Minecraft | **94 of 137** internal files and **26** API files import `net.minecraft` or `com.mojang` | `api`, `core` and `ui` have no Minecraft dependency at all, enforced by ArchUnit. |
| A god object as the composition root | `MediaPlayerContext` (904 lines) builds every service, **implements the whole public API**, and is reached through a static `getOrNull()` cast | A small composition root that only wires services; the API is implemented by dedicated facades (§4.3). |
| Global mutable state | `MediaWindowOverlay` is a static coordinator; `Theme` is ~40 mutable `static int` fields; static registries in `api` | Instances owned by the root and passed by constructor. Themes are immutable records (§7.5). |
| Two engines that duplicate each other | `VideoPlayer` (845 lines) and `AudioPlayer` (673 lines) each have their own control thread, seek/pause logic and ~15 `volatile` fields | One `Player` whose video track is optional, driven by a single-threaded actor (§6.5). |
| Three owners of "a player" | Window players (`QueuedMediaWindow`), headless audio (`HeadlessAudio` + `PlayerHandle`) and surfaces (`SurfaceRegistry`) each tick, post events and dispose separately | One `PlaybackRegistry` owns every `Playback`; windows, surfaces and the HUD are *views* attached to it (§6.6). |
| YouTube capped at progressive formats | Format string `best[height<=720][acodec!=none][vcodec!=none]/…` — YouTube now only serves 360p as a progressive stream | Separate video and audio inputs into one ffmpeg process (§6.4). |
| Headers lost during resolution | `yt-dlp -g` prints only URLs; sites that need a `Referer` or cookies fail in ffmpeg | `yt-dlp -J` and pass `http_headers` to ffmpeg (§6.3). |
| Video timestamps drift on variable frame rate | `ts = base + frameIndex * frameDurationMicros` from the probe; phone videos are often VFR | Force constant output rate with `-fps_mode cfr -r N` (§6.4). |
| An extra process and round trip before playback | ffprobe runs before every player starts, and titles come from a separate oEmbed request | Metadata comes from the yt-dlp JSON when there is one; probes are cached; frame geometry can come from the stream itself (§6.4). |
| Vanilla widget churn in every screen | `OptionsList`, `PlaylistScreen`, `HistoryScreen`, `ConfigScreen`, `Tooltips` carry 7–8 guards each | One retained-mode toolkit drawn on a `Canvas` interface; one vanilla `Screen` subclass hosts it (§7). |
| Two code paths for chat on Fabric | `ALLOW_CHAT` + re-inject + `ChatTrustLevel` re-evaluation for player chat, `MODIFY_GAME` for system chat | One display-time hook for every message on both loaders (§8). |
| Four separate HTTP code paths | `HttpURLConnection` in `ImagePreviewCache`, `VideoThumbnailCache`, `TenorResolver`, `MediaTitleCache`; `HttpClient` in the downloader | One shared `java.net.http.HttpClient` service (§6.10). |
| Ad-hoc thread creation | `new Thread(...)` in players, `ConfigScreen`, resolvers; a private pool in the thumbnail cache; vanilla's `Util.ioPool()` elsewhere | A small set of named executors owned by the root (§10). |
| Writes on every change | Stores save after each mutation | Debounced asynchronous writes, flushed on shutdown (§9). |
| Low testable surface | ~23 % line coverage; most logic sits next to rendering | Most logic in Minecraft-free modules; target ≥ 70 % on `core` and `ui` (§16). |

---

## 3. Key decisions

Short records of each decision. The sections after this one give the details.

| # | Decision | Why |
|---|---|---|
| D1 | Four layers: `api`, `core`, `ui` (all Minecraft-free) and `platform` (Minecraft + loaders) | Goal 1. Most code compiles and is tested once. |
| D2 | Stonecutter is used **only** inside `platform`; a build check rejects `//?` anywhere else | Keeps the preprocessor where version churn actually is. |
| D3 | `api`, `core` and `ui` compile with `--release 21`; `platform` uses the Java version of each Minecraft target (21 or 25) | 1.21.x runs on Java 21, 26.x on Java 25. A Java 21 library runs on both. |
| D4 | Mixins are allowed in `platform/mc` only, listed in §5.5, each with a reason | One display-time chat hook replaces two fragile workarounds. Loader events stay the default. |
| D5 | One playback engine; audio-only is a player without a video track | Removes the duplicated engine and its divergent pause/seek bugs. |
| D6 | One ffmpeg process per session, with separate video and audio inputs when the site serves them separately | One network fetch per stream, higher quality on YouTube, simpler lifecycle. |
| D7 | Resolve with `yt-dlp -J` and cache the result until its URLs expire | Title, thumbnail, duration, formats and HTTP headers in one call. |
| D8 | `Playback` (player + queue) is independent of how it is shown | Windows, surfaces, HUD and headless audio share one owner and one event path. |
| D9 | The mod draws its own UI through a `Canvas` interface; vanilla `Screen` is only a host | The UI stops depending on vanilla widget APIs that change every few versions. |
| D10 | No mutable statics. One composition root, constructor injection | Testable, no initialisation-order bugs, no hidden coupling. |
| D11 | A single-threaded actor per player; virtual threads for blocking IO; a platform thread for each audio pump | Removes most `volatile`/lock reasoning. Cheap blocking IO. |
| D12 | Versioned JSON files, debounced writes, migrations from v2 files | Data survives the upgrade; no IO on the client thread. |
| D13 | Public API v4: Minecraft-free, one addon entry point, typed events, namespaced ids | One API jar for every target; addons compile once. |
| D14 | Audio output behind an `AudioSink` interface; Java Sound by default | Keeps the proven path and leaves room for an OpenAL sink later (§6.7). |

---

## 4. Architecture

### 4.1 Modules

```
                 ┌────────────────────────────────────────────────────┐
                 │ platform  (per Minecraft version × loader)         │
                 │   mc/        adapters to vanilla (Stonecutter OK)  │
                 │   fabric/    entrypoint + event wiring             │
                 │   neoforge/  entrypoint + event wiring             │
                 └──────────────┬─────────────────────────────────────┘
                                │ implements Platform, calls ClientEvents
                 ┌──────────────▼──────────────┐
                 │ ui   (plain Java 21)         │  toolkit, windows, screens, themes
                 └──────────────┬──────────────┘
                 ┌──────────────▼──────────────┐
                 │ core (plain Java 21)         │  links, resolve, tools, ffmpeg,
                 │                              │  playback, audio, images, library,
                 │                              │  settings, storage, events, policy
                 └──────────────┬──────────────┘
                 ┌──────────────▼──────────────┐
                 │ api  (plain Java 21)         │  public, MIT, published separately
                 └─────────────────────────────┘
```

Dependency rules, checked by ArchUnit tests (§15):

- `api` depends on nothing but the JDK (plus `org.jspecify` annotations at compile
  time).
- `core` depends on `api`, the JDK, Gson and SLF4J. Gson and SLF4J are already on
  Minecraft's classpath at runtime, so they are `compileOnly`.
- `ui` depends on `core` and `api`.
- Nothing in `api`, `core` or `ui` imports `net.minecraft`, `com.mojang`,
  `net.fabricmc`, `net.neoforged` or `org.lwjgl`.
- Inside `platform`, only `platform.fabric` imports `net.fabricmc` and only
  `platform.neoforge` imports `net.neoforged`.
- `core` never depends on `ui`. `ui` reaches the engine through `core` services, not
  through `api` facades.

The final mod jar for one target contains the classes of `api`, `core`, `ui` and
that target's `platform`, merged into one jar. They live in the mod's own packages,
so no relocation is needed and there is no nested jar. `api` is also published on
its own to a Maven repository for addon developers.

### 4.2 Package layout

```
api/   com.lia.mediaplayer.api
  MediaPlayer            the facade addons call
  MediaPlayerAddon       the one interface an addon implements
  AddonContext           registries handed to addons
  media/                 MediaKind, MediaRequest, MediaHandle, PlaybackState, RepeatMode
  queue/                 QueueView, QueueEntry
  source/                LinkSource, Resolver, ResolvedMedia, MediaMetadata
  event/                 sealed MediaEvent hierarchy, EventListener
  audio/                 AudioOptions, AudioPlacement, AudioChannel, Mixer, Vec3d
  render/                SurfaceHandle, SurfaceOptions, Pixels
  ui/                    WindowAction, ActionIcon, ThemeSpec, ThemeRole, ShortcutSpec, ScreenEntry
  settings/              SettingSpec and its typed subclasses
  policy/                Interceptor, PlayOrigin
  sync/                  SyncAction, SyncAdapter, SyncControl
  library/               PlaylistView, HistoryView
  diag/                  Stats, FailureRecord
  text/                  Text (sealed: Literal, Translatable)

core/  com.lia.mediaplayer.core
  MediaPlayerCore        composition root, built by the platform
  lifecycle/             Lifecycle, Disposer, ClientThread (executor interface)
  link/                  LinkScanner, SourceRegistry, builtin/ (one class per site)
  resolve/               ResolverChain, YtDlpResolver, DirectResolver, TenorResolver,
                         GiphyResolver, ResolveCache, CollectionExpander
  tools/                 ToolManager, ToolLocator, ToolDownloader, ProcessRunner
  ffmpeg/                FfmpegCommand (builder), Probe, ProbeCache, Session, PamReader
  playback/              Player, PlayerState, PlayerSnapshot, Playback, PlaybackRegistry,
                         PlayQueue, Clock
  audio/                 AudioSink, JavaSoundSink, PcmRing, Mixer, Gain, Spatial
  video/                 FrameRing, FramePool, FrameSink (interface)
  image/                 ImageLoader, GifDecoder, FfmpegImageDecoder, DecodedImage
  cache/                 AsyncCache, Weigher, DiskCache
  library/               PlaylistStore, HistoryStore, M3u
  settings/              Setting<T>, SettingsRegistry, SettingsStore, builtin settings
  storage/               JsonStore, Migration, DataPaths
  net/                   Http
  event/                 EventBus
  policy/                LinkFilter, InterceptorChain
  sync/                  SyncService
  diag/                  StatsCollector, FailureLog, PlaybackError, jfr/
  text/                  Keys (translation keys as constants)

ui/    com.lia.mediaplayer.ui
  canvas/                Canvas, TextureRef, Rect, Insets, Argb
  input/                 InputEvent (sealed), Key, Mods, PointerTracker
  widget/                Widget, Container, Row, Column, Stack, Label, Button, IconButton,
                         Slider, SeekBar, TextField, ListView, ScrollPane, Tooltip
  anim/                  Animation, Easing, Clock
  theme/                 Theme, Role, ThemeRegistry, builtin themes
  icon/                  Glyphs
  window/                Window, WindowManager, Chrome, Snap, Placement, WindowStateStore
  windows/               VideoWindow, AudioBar, ImageWindow, QueuePanel, ErrorPanel
  screen/                UiScreen, PlaylistScreen, HistoryScreen, SettingsScreen,
                         MediaControlsScreen
  overlay/               ChatOverlay (hover preview, chat buttons), NowPlayingBanner, HudLayer
  shortcut/              ShortcutTable

platform/ com.lia.mediaplayer.platform
  mc/                    McPlatform, McCanvas, McTextures, McChat, McInput, HostScreen,
                         McKeyMappings, McCamera, McClipboard, McText, McGame, mixin/
  fabric/                FabricEntrypoint, FabricEvents, ModMenuIntegration
  neoforge/              NeoForgeEntrypoint, NeoForgeEvents, NeoForgeApiMod
```

### 4.3 Composition root and lifecycle

`MediaPlayerCore` builds every service once, in dependency order, and owns their
lifetimes. It holds no logic of its own.

```java
public final class MediaPlayerCore implements AutoCloseable {
    private final Disposer disposer = new Disposer();

    public MediaPlayerCore(Platform platform, DataPaths paths) {
        var executors = disposer.add(new Executors(platform.clientThread()));
        var events    = new EventBus(platform.clientThread());
        var settings  = new SettingsRegistry(new SettingsStore(paths, executors.io()));
        var http      = disposer.add(new Http(settings));
        var tools     = disposer.add(new ToolManager(paths, http, executors, settings, events));
        var resolver  = new ResolverChain(tools, http, new ResolveCache());
        var sources   = new SourceRegistry(BuiltinSources.all());
        var mixer     = new Mixer();
        var playbacks = disposer.add(new PlaybackRegistry(tools, resolver, mixer, events, executors));
        // … library, caches, policy, sync, diagnostics
    }

    /** Leaving a world: stop everything that belongs to that session. */
    public void onWorldLeft() { … }

    @Override public void close() { disposer.close(); }  // game shutdown
}
```

Rules:

- Services receive what they need through their constructor. No service looks up
  another through a static.
- Each platform entrypoint keeps a single reference to the root. That reference is
  the only static in the mod outside of constants.
- The public API is implemented by small facades (`PlaybackFacade`,
  `LibraryFacade`, …) that wrap core services. `MediaPlayerCore` does not implement
  the API itself.
- Two lifecycles exist and are explicit: **game session** (tools, settings, library,
  caches on disk) and **world session** (players, windows, surfaces, in-memory
  caches). `onWorldLeft()` disposes everything in the second.
- `Disposer` closes resources in reverse order of creation. Every service that owns
  a thread, process, texture or file handle implements `AutoCloseable`.

---

## 5. The platform seam

This is the only place where the Minecraft version or the loader matters.

### 5.1 What core and ui need from the game (`Platform`)

```java
public interface Platform {
    ClientThread clientThread();     // Executor that runs on the client thread + isClientThread()
    TextureService textures();       // create, upload, release (deferred) GPU textures
    TextMeasurer text();             // width and height of a Text with the game font
    Clipboard clipboard();
    void openInBrowser(String url);  // http(s) only
    ToastService toasts();
    KeyMappingService keys();        // register mappings, poll presses
    ListenerService listener();      // camera position and yaw for positional audio
    GameState game();                // paused?, master volume, gui scale, window size
    Path gameDirectory();
}
```

`Platform` lives in `core`, so everything it names does too: `TextureRef`,
`TextureService` and `TextMeasurer` are in `core.platform`, not `ui.canvas` (settled in
M2). Showing a `UiScreen` is the one need that names a `ui` type, so it is not on
`Platform` but on `ui.UiHost` (open, close, which mod screen is open), which the platform
implements beside it. M2 has no general `KeyMappingService` yet: the platform registers one
key mapping per `ui.Hotkey` and reports presses through `ClientEvents.hotkeyPressed`.

`TextureService` is where the deferred-release rule lives:

```java
public interface TextureService {
    TextureRef create(String debugName, int width, int height);
    void upload(TextureRef texture, ByteBuffer rgba);  // client thread only
    void release(TextureRef texture);                   // queued, freed between frames
}
```

`TextureRef` is a Minecraft-free record (an id plus a size). The platform keeps the
map from id to `DynamicTexture` and `ResourceLocation`/`Identifier`.

### 5.2 What the game tells core and ui (`ClientEvents`)

The reverse direction is a single interface implemented by `ui`, called by the
loader bridges:

```java
public interface ClientEvents {
    void tick();                                                 // end of client tick
    void renderScreen(ScreenContext screen, Canvas canvas,       // after a screen draws, with the
                      double mouseX, double mouseY, Mods mods);  // cursor and modifiers of this frame
    void renderHud(ScreenContext screen, Canvas canvas);         // in-world HUD, under `screen`
    boolean input(ScreenContext screen, InputEvent event);       // true = consumed
    void screenOpened(ScreenContext screen);                     // a screen was built or rebuilt
    void hotkeyPressed(Hotkey hotkey);                           // a mod key mapping
    void worldLeft();
    List<ChatLink> chatDisplayed(List<String> runs, @Nullable String sender);  // M3
    // M4: a ButtonSink for vanilla buttons on vanilla screens
}
```

Three simplifications compared with v2:

- **Only press, release, scroll, key and char events cross the seam.** The cursor
  position is read every frame in `renderScreen`. `ui.input.PointerTracker`
  synthesises moves, drags and double-clicks from that. This removes the Fabric
  drag reconstruction before 1.21.8 and the "no mouse move hook" problem, and makes
  drag behave the same on every target.
- **Chat crosses as plain strings.** `McChat` flattens a `Component` into the text of its
  styled runs; `core` answers with the spans to turn into labels (`ChatLink`: run, range,
  label, hint, colour), and `McChat` rebuilds the `Component` itself, so every style `core`
  does not need to change — fonts, a server's own hover — survives untouched (§8, settled
  in M3). `UiHost.chatLinkAt(x, y)` is the reverse question: the link under the cursor.
- **`ScreenContext` is an opaque record** (`kind`: NONE, CHAT, PAUSE, HOSTED, OTHER;
  plus "is the chat field empty"). `ui` never sees a `Screen`. The media controls are a
  hosted screen like any other; `ui` asks its `UiHost` which one is open.
- The modifiers held during a drag cross with the cursor position (`PointerDrag` carries
  them), because Shift pressed mid-drag frees a window from snapping.

### 5.3 `Canvas`: the drawing surface

```java
public interface Canvas {
    int width();
    int height();
    void fill(int x0, int y0, int x1, int y1, int argb);
    void texture(TextureRef texture, int x, int y, int w, int h);
    void text(Text text, int x, int y, int argb, boolean shadow);
    void pushClip(int x0, int y0, int x1, int y1);
    void popClip();
    /** Everything drawn after this sits above everything drawn before it, text included. */
    void nextLayer();
    void tooltip(List<Text> lines, int x, int y);
}
```

`nextLayer()` is the abstraction over the z-order churn: a pose translation plus a
flush before 1.21.6, `nextStratum()` from 1.21.6. Rounded corners, icons and
progress bars are built from `fill` calls in `ui`, so they never need a version
guard.

### 5.4 Known version churn

v2 already discovered these. Every one of them belongs in `platform/mc` and nowhere
else.

| Since | Change | Platform class |
|---|---|---|
| 1.21.2 | `blit` argument order and `RenderType` factory | `McCanvas` |
| 1.21.5 | `ClickEvent` / `HoverEvent` became records (`OpenUrl`, `ShowText`) | `McText`, `McChat` |
| 1.21.5 | `NativeImage` pixel pointer public (`getPointer`); reflection before | `McTextures` |
| 1.21.5 | `DynamicTexture` needs a label | `McTextures` |
| 1.21.6 | `nextStratum()` replaces z-translation; `RenderPipelines.GUI_TEXTURED` | `McCanvas` |
| 1.21.6 | Tooltip call renamed (`setTooltipForNextFrame`) | `McCanvas` |
| 1.21.8 | Fabric `allowMouseDrag` appears | not needed in v3 (§5.2) |
| 1.21.11 | `ResourceLocation` → `Identifier`, `net.minecraft.Util` → `net.minecraft.util.Util` | `platform/mc` only |
| 1.21.11 | Mouse and key events became records (`MouseButtonEvent`, `KeyEvent`) | `FabricEvents` |
| 1.21.11 | `Screen.hasShiftDown()` → `Minecraft.hasShiftDown()` | `McInput` |
| 1.21.11 | Chat hit test through `ActiveTextCollector.ClickableStyleFinder` | `McChat` |
| 1.21.11 | Key mapping category became a registered object | `McKeyMappings` |
| 1.21.11 | `Camera.getPosition()`/`getYRot()` → `position()`/`yRot()` | `McCamera` |
| 26.1 | Java 25 | build |
| 26.1 | `GuiGraphics` → `GuiGraphicsExtractor`, `drawString` → `text`; GUI is extracted, then executed (texture lifetime rule) | `McCanvas`, `McTextures` |
| 26.1 | Chat display mode tri-state; `addMessage` → `addPlayerMessage` | `McChat` |
| 26.1 | Fabric: `HudRenderCallback` → `HudElementRegistry`, `Screens.getButtons` → `getWidgets`, `KeyBindingHelper` → `KeyMappingHelper` | `FabricEvents` |
| 26.2 | `mc.screen` / `setScreen` → `mc.gui.screen()` / `setScreen`; `getMainCamera` → `mainCamera` | `McScreens`, `McCamera` |
| 26.3 | `InputConstants.Type.KEYSYM` → `KEYBOARD` | `McKeyMappings` |

Guidelines for `platform/mc`:

- One class per concern, and keep each method that needs a guard short, so a guard
  covers a few lines.
- Prefer a guard inside a small method over Stonecutter token replacement. v2's
  global replacements (`GuiGraphics` → `GuiGraphicsExtractor` across 14 files) were
  needed only because those types were everywhere. In v3 they appear in a handful of
  files.
- When a family of versions diverges too much (for example 1.21.x versus 26.x
  rendering), a second implementation of the same interface behind a guard on the
  class is clearer than many inline guards.

### 5.5 Mixins

v2 avoided mixins completely, at the cost of two workarounds. v3 allows them inside
`platform/mc` under these rules: each mixin has a comment saying which event it
replaces and why none exists, `defaultRequire = 1` so a broken target fails at
startup rather than silently, and a CI client smoke test per target (§16).

Mixins (settled in M3):

| Mixin | Replaces |
|---|---|
| `ChatComponent` display entry (`addMessage` and its 26.1 variant) — rewrite the displayed `Component` | Fabric's `ALLOW_CHAT` cancel + re-inject + `ChatTrustLevel` re-evaluation, and the separate system-chat path. Works the same on NeoForge, and it only changes what is shown, so message signatures are not affected. |
| `ChatListener.showMessageToPlayer`, head and return — read-only, records who sent the player message being shown | The chat is not told the sender, and the loaders' chat events fire when a message is *received*, which the chat-delay option separates from when it is *shown*. Needed for the blocked-senders filter. |

A mixin reaches the mod only through a static: `McChat` holds the one the running mod
installs, the only static state besides the entrypoints' reference. Loom remaps the
mixins' target strings into the classes of the Fabric jar, so no refmap ships.

Anything else stays on loader events. If a later need for a mixin comes up, it goes
through the same rules.

### 5.6 Loader entrypoints

Each loader has one entrypoint that builds the `McPlatform`, the
`MediaPlayerCore`, the `ui` layer and its `ClientEvents`, then wires events:

| Moment | NeoForge | Fabric |
|---|---|---|
| Start | `@Mod(dist = CLIENT)` constructor | `ClientModInitializer` |
| Client tick | `ClientTickEvent.Post` | `END_CLIENT_TICK` |
| Screen render | `ScreenEvent.Render.Post` | `ScreenEvents.afterRender` / `afterExtract` |
| HUD render | `RenderGuiEvent.Post` / layered GUI | `HudRenderCallback` / `HudElementRegistry` |
| Mouse press, release, scroll | `ScreenEvent.Mouse*.Pre` + cancel | `ScreenMouseEvents.allow*` |
| Key, char | `ScreenEvent.KeyPressed.Pre`, `CharacterTyped.Pre` | `ScreenKeyboardEvents.allowKeyPress`, char event |
| Screen init (add buttons) | `ScreenEvent.Init.Post` | `ScreenEvents.AFTER_INIT` |
| World left | `ClientPlayerNetworkEvent.LoggingOut` | `ClientPlayConnectionEvents.DISCONNECT` |
| Key mappings | `RegisterKeyMappingsEvent` | `KeyMappingHelper` |
| Client command | `RegisterClientCommandsEvent` | `ClientCommandRegistrationCallback` |
| Config screen button | `IConfigScreenFactory` | ModMenu entrypoint |
| Addon discovery | `LiasMediaPlayer.registerAddon(...)` from the addon's `@Mod` constructor | `"liasmediaplayer"` entrypoint + the same method |

The bridges contain no decisions. Translating "consumed" into `setCanceled(true)` or
`return false` is all they do.

---

## 6. Media pipeline

### 6.1 Model

```java
/** A link the mod recognised, before anything was fetched. */
public record MediaRef(String url, MediaKind kind, String sourceId, Text label) {}

public enum MediaKind { IMAGE, VIDEO, AUDIO, COLLECTION }

/** What a resolver produced: everything needed to start ffmpeg. */
public record ResolvedMedia(
        MediaRef ref,
        Optional<StreamUrl> video,
        Optional<StreamUrl> audio,          // empty when muxed into video, or silent
        Map<String, String> httpHeaders,
        Metadata metadata,                  // title, duration, thumbnail, isLive, width, height, fps
        Instant expiresAt) {}

public record StreamUrl(String url, String protocol) {}
```

`COLLECTION` is new: a playlist page is a first-class kind that expands into
`MediaRef`s instead of a special case.

### 6.2 Link recognition

```java
public interface LinkSource {
    String id();                                   // "liasmediaplayer:youtube"
    Optional<MediaRef> match(URI uri, String raw); // cheap, no IO, thread-safe
    default int priority() { return 0; }
}
```

- `LinkScanner` finds URL spans in a string with one precompiled pattern and a fast
  pre-check (`indexOf("http")`) so messages without links cost almost nothing.
- `SourceRegistry` asks sources in priority order; the first match wins. Built-in
  sources keep v2's matching rules and their tests. Each built-in is one small
  class.
- Engines never ask "is this YouTube?" through a static predicate. Anything a later
  stage needs (a video id, a timestamp format) travels inside `MediaRef` or comes
  from the `LinkSource` that produced it, through a capability interface such as
  `TimestampLinks` for "copy at the current time".

### 6.3 Resolution

```java
public interface Resolver {
    boolean handles(MediaRef ref);
    CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options);
}
```

Built-in chain, tried in order: addon resolvers → `DirectResolver` (files and
manifests, no IO) → `GiphyResolver` (string work) → `TenorResolver` (page fetch) →
`YtDlpResolver`.

`YtDlpResolver` runs:

```
yt-dlp --ignore-config --no-playlist --no-warnings -J
       -f "bv*[height<=H]+ba/b[height<=H]/b" -- <url>
```

and reads from the JSON: `requested_formats` (separate video and audio URLs) or
`url` (single), `http_headers`, `title`, `duration`, `thumbnail`, `is_live`,
`width`, `height`, `fps`. This replaces three things v2 did separately: `yt-dlp -g`,
the oEmbed title request, and the ffprobe call for these sources.

- `--ignore-config` matters: a user's own yt-dlp config file would otherwise change
  the output format the mod parses.
- **`ResolveCache`** keeps results until `expiresAt` (derived from the stream URL's
  `expire` parameter where present, otherwise a conservative 1 hour). Retry, seek
  after a long pause and the queue's thumbnail and title lookups reuse it. An
  expired URL that fails with HTTP 403 triggers one re-resolve before the player
  reports a failure.
- **`CollectionExpander`** runs `yt-dlp --ignore-config --flat-playlist -J` for any
  `COLLECTION` ref, capped at 500 entries, and returns `MediaRef`s plus the
  collection title. YouTube playlists, SoundCloud sets and Bandcamp albums all use
  it.
- Resolution never runs on the client thread. Concurrent requests for the same ref
  share one future.

### 6.4 The ffmpeg session

One process per playback session (a session ends at a seek or a track change):

```
ffmpeg -nostdin -hide_banner -loglevel error
       -protocol_whitelist http,https,tcp,tls,crypto,hls,dash,data
       [-headers "<from yt-dlp>"] [-ss <start>] -reconnect 1 -reconnect_streamed 1 -i <video or muxed URL>
       [-headers …] [-ss <start>] -i <audio URL, when separate>
       -map 0:v:0 -vf "scale=W:H:force_original_aspect_ratio=decrease" -fps_mode cfr -r <fps>
             -pix_fmt rgba -f rawvideo pipe:1
       -map <0 or 1>:a:0 -ac 2 -ar 48000 -f s16le tcp://127.0.0.1:<port>
```

- **Separate inputs** let YouTube play the format the user chose (up to 720p or
  1080p) instead of the 360p progressive stream. v2 already had the loopback socket
  for audio; v3 makes it the only path and drops the two-process fallback.
- **`-fps_mode cfr -r <fps>`** makes `frameIndex * (1 / fps)` exact for variable
  frame rate sources. The output rate is `min(source fps, setting)`, where the
  setting defaults to 30 for windows smaller than 720 px and 60 otherwise. This is
  also a performance lever on weak machines.
- **Output size** follows the window. Decoding happens at the smallest of three
  steps (360, 480, 720 or 1080 lines, bounded by the setting) that covers the
  window's on-screen size. A resize that crosses a step relaunches the session once
  the resize has been still for 500 ms.
- **Frame geometry from the stream (PAM, adopted in M1):** frames are written as
  `-f image2pipe -c:v pam`, so each carries a ~70-byte text header with its width
  and height. ffmpeg decides the output size and says what it decided, which handles
  adaptive streams that change resolution mid-way, non-square pixels and rotation
  metadata. The prototype measured no cost: 600 frames of 1080p scaled to 720p took
  the same time with PAM as with `rawvideo` (1.20 s against 1.22 s wall, within
  noise). It also found the case that decides it: a phone clip tagged with a 90°
  rotation probes as 640×360 but is written as 405×720, so `rawvideo` sized from the
  probe would have garbled every such video. Probes are still run for direct files,
  for their tracks, frame rate and duration, not for geometry.
- **Probes** run only when metadata is unknown (direct files and manifests) and are
  cached per URL for the game session.
- **stderr** is captured into a bounded ring (last 16 KB) and handed to
  `PlaybackError` when the session fails.
- A **shutdown hook** and the session's own `close()` both kill the process tree
  (`ProcessHandle.descendants()`), so ffmpeg never outlives the game.

### 6.5 The player

One class for audio and video. Its state is a sealed hierarchy:

```java
public sealed interface PlayerState {
    record Idle() implements PlayerState {}
    record Resolving() implements PlayerState {}
    record Buffering(Duration position) implements PlayerState {}
    record Playing(Duration position) implements PlayerState {}
    record Paused(Duration position) implements PlayerState {}
    record Seeking(Duration target) implements PlayerState {}
    record Ended() implements PlayerState {}
    record Failed(PlaybackError error) implements PlayerState {}
}
```

**Actor model.** Each player owns a single-threaded command executor (a virtual
thread draining a queue). Every state change happens there: `play`, `pause`,
`seek`, `setTrackEnabled`, and the internal messages from its session
(`SessionStarted`, `FirstFrame`, `EndOfStream`, `SessionFailed`). Nothing else
writes player state, so the fields need no `volatile` and no lock. The client
thread reads an immutable `PlayerSnapshot` record that the actor publishes through
an `AtomicReference` after each change:

```java
public record PlayerSnapshot(
        PlayerState state, Duration position, Optional<Duration> duration,
        boolean hasVideo, boolean hasAudio, int videoWidth, int videoHeight) {}
```

Public methods only enqueue a command and return. Events go to the `EventBus`,
which delivers them on the client thread.

**Seek** relaunches the session at the target (input seeking, `-ss` before `-i`).
While it runs, the snapshot reports `Seeking(target)` so the seek bar stays where
the user put it; the actor leaves that state at the new session's first frame, not
at launch. Frames carry their session generation and frames from older sessions
are dropped (v2's barrier, kept).

**Pause** stops the audio sink and the clock. The reader threads keep draining into
the rings until they are full, then ffmpeg blocks on its pipe. Resume continues in
place if the pause was shorter than 10 s; a longer pause relaunches at the paused
position, because remote servers drop idle connections. v2 relaunched on every
video resume because its in-place resume froze; that freeze came from the decode
thread and the pipe interacting. The ring design below avoids it, and a test pins it
(`PlayerIntegrationTest.aShortPauseResumesInPlaceAndKeepsPlaying`: pause, resume
with no new session, frames until the end). Settled in M1.

**Hidden players.** A hidden window's player keeps decoding by default, but frames
are recycled without being copied or uploaded. A setting "When hidden: keep video /
sound only" lets weak machines relaunch without video after 5 s hidden; showing the
window relaunches with video at the current position.

### 6.6 Playback and its views

```java
/** "What plays": a player, its queue and its gain. Knows nothing about windows. */
public final class Playback implements AutoCloseable {
    private final long id;
    private final PlayQueue queue;
    private final Gain gain;         // survives track changes
    private Player player;           // replaced when the queue advances
    …
}
```

- `PlaybackRegistry` owns every `Playback`, ticks them, enforces the caps (video,
  audio, headless) and disposes them on world leave.
- A `View` attaches to a `Playback`: a `VideoWindow`, an `AudioBar`, an addon
  surface, or nothing (headless audio). Closing the last view of a non-headless
  playback closes the playback.
- `PlayQueue` is pure: append, insert, move, remove, jump, `next()`/`previous()`
  under `RepeatMode` and a sticky shuffle flag, using an injected
  `RandomGenerator` so tests are deterministic. v2's shuffle and repeat rules carry
  over unchanged (each `ALL` round reshuffled, never starting with the track just
  heard).
- History recording, events, sync broadcasting and failure logging happen once, in
  `Playback`, for every kind of owner. v2 had three copies of this.

### 6.7 Audio output

```java
public interface AudioSink extends AutoCloseable {
    void open(AudioFormat format) throws AudioException;
    int write(byte[] pcm, int offset, int length);  // blocking; byte[] because Java Sound takes one
    void start();
    void stop();
    void flush();
    long playedFrames();                // the master clock
    void setGain(float linear);
    void setPan(float pan);             // -1..1, ignored if unsupported
}
```

- `JavaSoundSink` is the default and matches v2's behaviour: a `SourceDataLine`
  with a ~150 ms device buffer, dB gain with a linear fallback, pan with a
  balance fallback.
- **Jitter buffering moves out of the device line** into a `PcmRing` of 2 s (a
  power-of-two direct buffer with a single producer and a single consumer). The
  socket reader writes into it; the pump moves 20 ms chunks from it to the sink.
  The video `FrameRing` is sized from the same time budget, so neither track can
  starve the other through ffmpeg's interleaved writes.
- **Clock**: `playedFrames() / sampleRate + sessionStart`, with a wall clock for
  silent videos. `driftCorrect` from v2 (bounded 50 ms per step, 2 s total, then
  seek) stays for watch-together.
- **Gain chain** unchanged: `master × channel × handle × attenuation`, computed once
  per tick on the client thread and published as two `volatile float`s that the pump
  reads. No allocation and no lock on the audio thread.
- **Later, optional:** an `OpenAlSink` in `platform/mc` that plays through
  Minecraft's own OpenAL context. That would use the same output device as the
  game and could give true 3D placement. It needs a prototype to check source
  limits and how to read a precise playback offset, so it is not in the first
  release. Because placement is described by `AudioPlacement` and not by how it is
  rendered, the API does not change if it lands.

### 6.8 Video frames to the GPU

- `FramePool` holds frames of the current size, allocated once per size. `FrameRing`
  is a bounded ring of pooled `VideoFrame`s (generation, due time, RGBA pixels), one
  per session. Unlike `PcmRing` it takes a `ReentrantLock`: two threads consume it (the
  view takes the newest due frame, the player's actor drops superseded ones when no view
  is attached), and the reader is a virtual thread that must not pin its carrier while
  it waits for room. A lock held for a few array moves per frame costs nothing visible.
- The reader thread reads each PAM header, then the whole frame straight into a pooled
  frame's array (one copy, out of the process pipe), and blocks when the ring is full.
- On the client thread, during the render pass, `VideoView.prepare(clockMicros)`
  drops due-but-superseded frames back to the pool, then uploads the newest due
  frame through `TextureService.upload`. At most one upload per player per frame.
- `McTextures.upload` does one copy into a native staging buffer the texture owns and
  one `glTexSubImage2D` from it: the same one copy and one upload as v2's `NativeImage`
  path, without reflecting on `NativeImage`'s private pixel pointer before 1.21.5 (which
  could not work on Fabric's intermediary names anyway). Settled in M2. Possible later
  improvements, each behind the same interface: a
  ring of two textures to avoid stalling on a texture in use, or YUV upload with a
  conversion shader (a third of the bandwidth, but shaders are version-specific).
- A window off screen or covered by a full-screen menu skips the upload.

### 6.9 Images and GIFs

- `ImageLoader` downloads through `Http` with a size cap (8 MB by default), sniffs
  the format from magic bytes, and decodes on the IO executor:
  - PNG, JPEG, BMP through `ImageIO` (no Minecraft needed);
  - GIF through `GifDecoder` (v2's compositing, frame cap 256, pixel cap 24 M,
    delay normalisation, all kept with their tests);
  - addon `ImageDecoder`s before the built-ins;
  - **new:** anything else (WebP, animated WebP, AVIF, APNG) through
    `FfmpegImageDecoder`, which asks ffmpeg for RGBA frames. It runs only when the
    tools are available, and fails cleanly when they are not.
- Decoded frames are RGBA heap buffers, dropped once uploaded. The upload to textures
  happens on the client thread, 16 MB of frames per tick at most, so a large GIF never
  causes a hitch; a picture counts as ready only when every frame is up.
- A still longer than 2048 px on its long side is scaled down once when decoded: it is
  shown at most at screen size, and a 12-megapixel photo would hold 48 MB of texture.
- One `ImageCache` (an `AsyncCache` of textured pictures, 256 MB by default) serves the
  hover preview and the image windows: one download, decode and texture set per link.
  Tenor and Giphy pages resolve through the resolver chain like any link (settled in M3).

### 6.10 Caches and HTTP

One generic cache replaces `ImagePreviewCache`, `VideoThumbnailCache` and
`MediaTitleCache`:

```java
public final class AsyncCache<K, V> {
    // LRU by weight; concurrent loads of the same key share one future;
    // loads can be cancelled when the entry is evicted;
    // an optional "finish on client thread" step (texture upload);
    // an eviction hook (texture release, which is deferred by TextureService).
    public Entry<V> get(K key);             // never blocks
    public sealed interface Entry<V> {
        record Loading<V>() implements Entry<V> {}
        record Ready<V>(V value) implements Entry<V> {}
        record Failed<V>(Throwable cause) implements Entry<V> {}
    }
}
```

- Weight limits come from settings and apply live.
- **Disk cache** for thumbnails (small JPEGs) and titles (one JSON file) under
  `liasmediaplayer/cache/`, capped at 100 MB by default and pruned at start. A
  playlist of 200 tracks then opens with pictures and names on the second visit
  without 400 requests.
- Visible-rows-only loading for long lists (v2's behaviour) is kept and made part of
  `ListView`.

`Http` wraps one `java.net.http.HttpClient` (HTTP/2, connect timeout, redirect
policy `NORMAL`), sets a single User-Agent, enforces size caps while streaming the
body, and runs on virtual threads. Every network call in the mod goes through it.

### 6.11 Tools

`ToolManager` keeps v2's split and behaviour:

- `ToolLocator`: explicit override (JVM property or environment variable), the
  mod's `bin/` folder, `PATH`, common per-OS locations, then the bare command.
- `ToolDownloader`: yt-dlp single binary, ffmpeg archive per platform, pinned ffmpeg
  release branch, magic-byte check, zip-slip check, temp file + atomic move.
- Stale yt-dlp (> 30 days) is replaced at launch or reported, by setting.
- **Changed:** the state is a sealed type published through the event bus
  (`Locating`, `Downloading(tool, progress)`, `Ready(versions)`, `Missing(tool,
  checkedPaths)`), so the settings screen and error panels show progress instead of
  polling.
- `ProcessRunner` is the only class that starts processes. It sets `-nostdin` style
  options, drains stderr into a bounded ring, applies timeouts, and destroys the
  whole process tree on close.

---

## 7. User interface

### 7.1 Why a toolkit of our own

In v2 every screen used vanilla widgets (`EditBox`, `Button`, `AbstractSelectionList`
for options) and paid a guard each time those APIs moved. The windows were already
drawn by hand. v3 draws everything through `Canvas` with a small retained-mode
toolkit in `ui`. The cost is owning a text field and list widgets. The benefit is
that the whole UI is version-independent and testable with a recording canvas.

What is lost: vanilla's narration for those screens. `ui` keeps an accessible name
on every widget so that narration can be added later through one platform hook.

### 7.2 Widgets

```java
public abstract class Widget {
    public abstract Size measure(Constraints constraints);       // constraints carry the font
    public void layout(Rect bounds, Constraints constraints) { … } // a container measures its children here
    public abstract void render(Canvas canvas, RenderContext ctx);
    public boolean onInput(InputEvent event, InputContext ctx) { return false; }
    public Optional<List<Text>> tooltip(double x, double y) { return Optional.empty(); }
}
```

`layout` takes the constraints because a container measures its children while it lays
them out, and measuring text needs the font (settled in M2). A widget that takes a press
can capture the pointer through `InputContext`, so the drags and the release go to it
wherever the cursor goes.

- Containers: `Row`, `Column`, `Stack`, `ScrollPane`, with padding, gaps, fixed and
  flex sizes. Layout runs only when something is marked dirty (resize, content
  change), not every frame.
- Controls: `Label`, `Button`, `IconButton`, `Toggle`, `Slider`, `SeekBar`,
  `TextField` (cursor, selection, clipboard, Ctrl+A/C/X/V, word jumps), `ListView`
  (virtualised rows, drag-to-reorder with an insertion line and edge scrolling, as in
  v2's playlist screen).
- Input events are a sealed hierarchy, so dispatch is a `switch` with patterns:

```java
public sealed interface InputEvent {
    record PointerDown(double x, double y, int button, Mods mods, int clicks) implements InputEvent {}
    record PointerUp(double x, double y, int button, Mods mods) implements InputEvent {}
    record PointerMove(double x, double y) implements InputEvent {}
    record PointerDrag(double x, double y, int button, double dx, double dy) implements InputEvent {}
    record Scroll(double x, double y, double delta, Mods mods) implements InputEvent {}
    record KeyDown(Key key, Mods mods) implements InputEvent {}
    record CharTyped(int codePoint) implements InputEvent {}
}
```

- `Mods` reports `primary()` (Ctrl, or Cmd on macOS) so no caller thinks about the
  platform.
- Animations use wall-clock time (`System.nanoTime()`), never ticks: windows also
  draw on the HUD while the world is paused.

### 7.3 Windows

- `Window` is a widget with chrome (title strip, corner buttons, resize grip, focus
  edge, open/close animation). `WindowManager` keeps the z-ordered stack, routes
  input top-down, raises on click, and applies snapping (`Snap` from v2, kept with
  its tests) and placement (default corner, cascade per kind, restored state).
- Subclasses: `VideoWindow`, `AudioBar`, `ImageWindow`. The video and audio windows
  share a `TransportBar` widget and a `QueuePanel` widget instead of a shared
  abstract base class.
- Theatre mode, idle-hiding chrome, locked windows (sync), the click flash and the
  closing outline carry over from v2.
- Addon `WindowAction`s are rendered into the corner row (max three, left of the
  heart), with every call into addon code wrapped so an exception is logged, not
  propagated.
- The HUD layer draws visible windows as picture only. The chat and media-controls
  screens draw them with controls and route input to them.

### 7.4 Screens

- The platform has one vanilla `Screen` subclass, `HostScreen`, that hosts any
  `UiScreen`: it forwards input, provides a `Canvas` in `render`, and reports
  `isPauseScreen` from the `UiScreen`.
- `PlaylistScreen`, `HistoryScreen`, `SettingsScreen` and `MediaControlsScreen` are
  `UiScreen`s in `ui`. Addon screens can be `UiScreen`s too, or a vanilla screen the
  addon opens itself.
- The "Playlists" chat button and the "Media Player Settings" pause-menu button are
  vanilla buttons added through `ButtonSink` in `screenInitialised`, because they
  sit inside vanilla screens.

### 7.5 Themes

```java
public record Theme(String id, Text name, Map<Role, Integer> colors) {
    public int color(Role role) { return colors.get(role); }
}
```

- Built-in themes are declared as "dark plus overrides" exactly as in v2, and a
  test checks every role is set in every theme.
- `ThemeRegistry` holds built-in and addon themes. The active theme is read from a
  setting through a listener, not polled every tick.
- Widgets receive the theme through `RenderContext`. No colour is a static field.

### 7.6 Shortcuts

`ShortcutTable.actionFor(key, mods, chatFieldEmpty)` stays a pure function with
v2's table and rules (bare Space and arrows only while the chat field is empty,
everything else behind the primary modifier, Escape never taken). Addon shortcuts
are offered a key only after the built-in table declines, and registration refuses
an unmodified letter or digit.

### 7.7 Text and translation

- `ui` and `core` use `api.text.Text` (`Literal`, `Translatable(key, args)`, with an
  optional style: colour, bold, underline). `McText` converts to and from
  `Component`.
- Translation keys are constants in `core.text.Keys`. A test checks that every key
  used in code exists in every language file, and that no file has unused keys.
- `en_us` and `fr_fr` ship from the start, as in v2.

---

## 8. Chat integration

**One hook, at display time.** The mixin on the chat display entry (§5.5) calls
`ClientEvents.chatDisplayed(text, sender)` for every message on both loaders,
whatever its origin. That removes Fabric's re-injection path and its trust-tag
emulation, and the "return the same instance" identity rule v2 had to pin with a
test.

Flow:

1. `McChat` checks the cheap pre-condition (the plain string contains `http`). If
   not, it returns the original `Component` untouched.
2. Otherwise it flattens the component into styled runs, hands them to `core` as
   `Text`, and receives a list of replacements.
3. `core.link.ChatRewriter` scans each run with `LinkScanner`, asks the
   `SourceRegistry`, applies the user's filters (sender, domain list), then the
   addon interceptors (`allowChatLink`, `decorateLabel`), and returns label spans.
   It keeps the inherited style of each run.
4. `McChat` rebuilds the `Component` with each label carrying an `OPEN_URL` click
   event to the original URL and a hover hint. If the mod's click handling is ever
   bypassed, vanilla's own "open link?" prompt still works.

**Finding the link under the cursor** is `McChat.linkAt(x, y)` behind `UiHost.chatLinkAt`,
a version-specific call cached per cursor position until the next tick (v2's
`ChatHitTest`). The hover preview and click routing in `ui.overlay.ChatOverlay` only see
the URL string.

Settled in M3:

- **Only the mod's own labels are acted on.** `ChatRewriter` remembers the links it
  labelled (the last 1024); a hover or click on any other link — one the filter refused,
  or a server's own clickable text — is left to the game, so the filter cannot be bypassed
  by a link that happens to be clickable anyway.
- **Look by what a link plays as:** pictures gold with no hint (the preview is the hover),
  sounds green, videos and video lists aqua; the hint says what Alt and Shift do.
- **Lists are collections everywhere.** SoundCloud sets and Bandcamp albums join YouTube
  playlists as `COLLECTION`s; a `CollectionSource` says what its entries are, read through
  `SourceRegistry.playsAs`, so a set opens the audio bar without anyone asking "is this
  SoundCloud?".
- **The filter has no screen yet.** `LinkFilter` (mode, host lists, blocked senders) is
  applied to every message; it is set through `ChatRewriter.setFilter` and defaults to
  allowing everything until the settings of M4 feed it.

---

## 9. Persistence and settings

### 9.1 Files

```
<gamedir>/liasmediaplayer/
  settings.json      user settings (mod and addons, namespaced keys)
  library.json       playlists and history
  windows.json       remembered window state per kind
  cache/             thumbnails, titles (safe to delete)
  bin/               downloaded tools
  logs/              optional rotating diagnostic log (off by default)
```

### 9.2 `JsonStore<T>`

```java
public final class JsonStore<T> {
    JsonStore(Path file, Codec<T> codec, int currentSchema, List<Migration> migrations,
              Executor io, Duration debounce) { … }
    public T get();                       // loaded lazily, once
    public void update(UnaryOperator<T> change);  // schedules a debounced write
    public void flush();                  // synchronous; called on shutdown
}
```

- Every file has a top-level `"schema": n`. Migrations are `JsonObject → JsonObject`
  functions, tested with stored fixture files from every schema version.
- Writes go to a temp file then an atomic move, on the IO executor, debounced by
  500 ms. A failed read keeps defaults and renames the broken file to
  `*.broken-<timestamp>.json` instead of overwriting it, so the user's data can be
  recovered.
- Domain objects are records (`Playlist(String name, List<String> urls)`,
  `HistoryEntry(String url, MediaKind kind, Instant lastPlayed, boolean favorite)`).
  Gson reads and writes records from 2.10; check the version bundled with the oldest
  supported target, and register explicit type adapters if it is older.

### 9.3 Settings

```java
public sealed interface SettingSpec<T> permits BoolSetting, IntSetting, EnumSetting,
        ChoiceSetting, StringSetting, StringListSetting {
    String id();                 // "liasmediaplayer:video.max_resolution"
    T defaultValue();
    String group();
    Text label();
    Optional<Text> description();
    Optional<Text> warning();    // the "sensitive setting" red tooltip
}
```

- `SettingsRegistry` holds specs (built-in and addon), current values, validation,
  and change listeners (`onChange(spec, listener)`). Services react to changes;
  nothing polls.
- The settings screen is generated from the specs: groups on the left, a search
  box, one row per setting with a reset button.

---

## 10. Threading model

| Thread | Runs | Rules |
|---|---|---|
| Client thread | Ticks, rendering, input, texture creation and upload, all `ui` code, event delivery to listeners | Never blocks on IO or processes. Debug builds assert this in `ProcessRunner`, `Http` and `JsonStore`. |
| Player actor (one virtual thread per player) | Player commands and state changes | Only place that writes a player's state. |
| Session readers (virtual threads) | Read ffmpeg stdout into `FrameRing`, socket into `PcmRing`, stderr into its ring | Block on IO only. |
| Audio pump (one platform thread per active sink, `MAX_PRIORITY - 1`) | `PcmRing` → `AudioSink.write`, apply gain | No allocation, no locks, no logging in the loop. A platform thread because a blocking native write would pin a virtual thread's carrier. |
| IO executor (virtual threads, bounded by a semaphore of 8) | HTTP, image decoding, yt-dlp, ffprobe, file writes | Never touches textures or `ui` objects. |
| Decode executor (platform, `availableProcessors / 2`, min 1) | CPU-heavy decoding (GIF compositing, large images) | Keeps CPU work off the virtual-thread carriers. |

- Hand-off to the client thread is always `ClientThread.execute(...)`. No code
  outside the platform calls Minecraft's executor.
- No preview features. In particular, `StructuredTaskScope` is still in preview in
  Java 25 and is not used. A session uses a plain `ExecutorService` in
  try-with-resources (`ExecutorService` is `AutoCloseable` since Java 19).
- Every thread has a name (`lmp-player-12`, `lmp-session-12.3-video`, …) so stack
  dumps and profilers are readable.

---

## 11. Public API v4 and extension points

### 11.1 Principles

- **Minecraft-free.** No `Component`, `GuiGraphics`, `ResourceLocation`, `Vec3` or
  `Entity` in any signature. One `liasmediaplayer-api` jar works on every target.
  - Labels and names are `api.text.Text`.
  - Positions are `api.audio.Vec3d` records; following an entity is
    `AudioPlacement.follow(Supplier<Vec3d>)`, which the addon writes as
    `() -> new Vec3d(e.getX(), e.getY(), e.getZ())`.
  - Surfaces expose `textureId()` as a `"namespace:path"` string plus a size; the
    addon builds its own `ResourceLocation`/`Identifier` from it. A tiny helper class
    per Minecraft version can be published separately for convenience.
- **One entry point** for addons:

```java
public interface MediaPlayerAddon {
    void register(AddonContext context);
}

public interface AddonContext {
    String addonId();
    Registry<LinkSource> sources();
    Registry<Resolver> resolvers();
    Registry<ImageDecoder> imageDecoders();
    Registry<ThemeSpec> themes();
    Registry<WindowAction> windowActions();
    Registry<ShortcutSpec> shortcuts();
    Registry<KeyBindingSpec> keyBindings();
    Registry<SettingSpec<?>> settings();
    Registry<Interceptor> interceptors();
    Registry<ScreenEntry> screenEntries();
    EventSubscriptions events();
    void onReady(Consumer<MediaPlayer> callback);  // called once the mod is up
}
```

  Fabric addons declare a `"liasmediaplayer"` entrypoint. NeoForge addons call
  `LiasMediaPlayerApi.registerAddon(new MyAddon())` from their `@Mod` constructor.
  Both lead to the same `register` call at the same lifecycle moment, before key
  mappings are handed to the game (which fixes v2's "register from your entry point
  or your key never works" trap by making it the only way).
- **Namespaced ids** (`addonid:name`) for everything registered, so two addons
  cannot collide and settings are stored under their owner.
- **Typed events**:

```java
public sealed interface MediaEvent {
    MediaHandle handle();
    record Started(MediaHandle handle) implements MediaEvent {}
    record Paused(MediaHandle handle, Duration at) implements MediaEvent {}
    record Resumed(MediaHandle handle, Duration at) implements MediaEvent {}
    record Seeked(MediaHandle handle, Duration from, Duration to) implements MediaEvent {}
    record TrackChanged(MediaHandle handle, String url) implements MediaEvent {}
    record Ended(MediaHandle handle) implements MediaEvent {}
    record Failed(MediaHandle handle, FailureRecord failure) implements MediaEvent {}
    record Closed(MediaHandle handle) implements MediaEvent {}
}
```

  Listeners subscribe per type (`events.on(MediaEvent.Paused.class, listener)`) or to
  all. They run on the client thread; an exception is logged and the listener keeps
  its subscription.
- **Handles over ids.** `MediaPlayer.play(MediaRequest)` returns a `MediaHandle`
  immediately, in `Resolving` state; collections resolve asynchronously and the
  handle's queue fills when they do. v2 had to return `-1` there.
- **Thread rules in the type names**: read-only views (`id`, `url`, `kind`,
  `snapshot()`) are safe from any thread; anything that changes state must run on the
  client thread and throws `WrongThreadException` otherwise, instead of misbehaving.

### 11.2 Kept from API 3.x

`MediaRequest` (placement, sizing, chrome, origin, shuffle, repeat), queue editing,
surfaces, headless audio with `AudioOptions` and the mixer channels, interceptors
with `PlayOrigin`, sync (`SyncAction`, `SyncAdapter`, lock), diagnostics
(`Stats`, failure log), m3u import/export, history and favourites. Their semantics
carry over; only the signatures change where Minecraft types are removed.

### 11.3 Versioning

- API version 4.0.0. Semantic versioning. The API's own `@since` tags start at 4.0.
- `api` has a binary-compatibility check in CI (for example `japicmp` against the
  last release), so an accidental breaking change fails the build.
- `Capability` flags (v2) stay, for features that depend on the target (for example,
  positional audio through OpenAL once it exists).

---

## 12. Performance budget

| Area | Target | How |
|---|---|---|
| Chat message without a link | < 5 µs | `indexOf("http")` pre-check before any parsing. |
| Chat message with links | < 100 µs | One precompiled pattern; no allocation per run beyond the result. |
| Overlay per frame, 4 windows | < 0.3 ms client-thread time | Layout only when dirty; no allocation in `render`; one upload per player per frame at most. |
| Starting a direct video | first frame < 1 s on a fast link | Cached probe, PAM framing if adopted, no separate audio process. |
| Starting a YouTube video | first frame < 2.5 s | One `yt-dlp -J` call, cached; no oEmbed or probe. |
| Seek | picture < 1 s | Input seeking; resolve cache. |
| Memory per video player at 720p | < 120 MB | Ring sized by time (≈ 1 s of frames), pooled buffers, one texture. |
| Image cache | user setting (default 256 MB) | Weight-based LRU. |
| Client-thread IO | none | Asserted in debug builds. |

Tools to measure:

- **JFR events** in `core.diag.jfr` (`FrameUpload`, `SessionStart`, `Resolve`,
  `RingUnderrun`, `CacheLoad`). They cost nothing when recording is off and show up
  in any JFR recording or in Spark's profiler.
- A debug overlay (setting, off by default) showing per-player buffer fill, dropped
  frames, A/V drift and upload time.
- JMH benchmarks for `LinkScanner`, `ChatRewriter`, `GifDecoder` and the ring
  buffers, in a `benchmarks` source set that is not shipped.

---

## 13. Errors, diagnostics and logging

- `PlaybackError` keeps v2's classification of yt-dlp and ffmpeg output into causes
  (unavailable, private, age-restricted, geo-blocked, outdated extractor, expired
  URL, timeout, unsupported format, access refused, tool missing, …) with a
  translated line, advice and a stable cause id. The pattern table stays pure and
  unit-tested with real stderr samples.
- Errors inside the mod are values where failure is expected (a `Failed` state, an
  `Entry.Failed`, a `Result`), and exceptions where it is a bug.
- Every call into addon code goes through `AddonGuard.run(addonId, …)`, which logs
  the exception with the addon id once per call site, and after a configurable
  number of failures disables that registration with a warning.
- Logging through SLF4J (on Minecraft's classpath). One logger per class. No logging
  inside the audio pump or the render loop; counters instead, shown by the debug
  overlay.
- `Stats` and the failure log (last 32 failures, cleared on world leave) are exposed
  through the API, as in v2.

---

## 14. Security and trust

- Keep v2's trust model for downloaded tools, and its explanation, in the class
  javadoc of `ToolDownloader`.
- **Arguments are never options.** Every URL goes after `--` for yt-dlp and after
  `-i` for ffmpeg; `MediaRef` construction rejects anything that is not `http` or
  `https`, and a URL starting with `-` is rejected anyway.
- `yt-dlp --ignore-config` always, so a user config cannot add `--exec` or change the
  output.
- `ffmpeg -protocol_whitelist` without `file` for remote inputs, so a remote HLS
  playlist cannot point ffmpeg at local files. Local files (if ever supported) get a
  separate, explicit whitelist.
- Size caps on every download (images 8 MB, pages 512 KB, JSON 2 MB), checked while
  streaming.
- Chat links are only rewritten, never fetched, until the user hovers or clicks.
  Hover preview of images from unknown senders can be turned off in settings
  (default on, as in v2).
- m3u import and every API entry that takes URLs pass through the same `http(s)`
  gate.

---

## 15. Build, tooling and quality gates

### 15.1 Gradle layout

```
settings.gradle.kts
gradle/libs.versions.toml           versions of tools and libraries (not Minecraft)
build-logic/                         convention plugins (java-library, testing, quality)
api/   core/   ui/                   plain Java projects
platform/                            Stonecutter tree: <mc>-fabric, <mc>-neoforge
  stonecutter.properties.toml        the Minecraft × loader matrix and per-version deps
  src/main/java/…/platform/{mc,fabric,neoforge}
  src/main/resources/                fabric.mod.json, neoforge.mods.toml, mixins, lang
benchmarks/                          JMH, not shipped
docs/                                user and developer docs
```

- Stonecutter is applied to the `platform` project only, keeping v2's matrix and
  property table (`stonecutter.properties.toml`) and the `loom-back-compat` plugin
  for Fabric's obfuscated versus unobfuscated split. Check the exact syntax for a
  Stonecutter tree rooted at a subproject in the Stonecutter version used.
- Each `platform` subproject compiles `platform/mc` plus its loader's package,
  depends on `:api`, `:core` and `:ui`, and merges their classes into its jar.
- **Guard check:** a Gradle task fails if `//?` appears in any file outside
  `platform/`. This is what keeps the isolation from eroding.
- Convention plugins in `build-logic` replace the shared `common.gradle.kts` script,
  and versions live in a version catalog.

### 15.2 Quality gates (CI)

| Gate | Applies to |
|---|---|
| Compile with `-Xlint:all -Werror` | `api`, `core`, `ui` (once). `platform`: warnings only, so a deprecation in a new Minecraft version is not a matrix-wide failure (v2's reasoning, kept). |
| Error Prone + NullAway, with JSpecify `@NullMarked` packages | `api`, `core`, `ui` |
| Formatting with Spotless (palantir-java-format or google-java-format) | everything |
| ArchUnit layering tests (§4.1) | `core`, `ui`, `platform` |
| Unit tests + JaCoCo with a threshold (70 % lines) | `core`, `ui` |
| Translation key check (§7.7) | `ui`, `core`, resources |
| API binary compatibility (`japicmp`) | `api` |
| Build every target | `platform` matrix |
| Client smoke test per target (§16) | `platform` matrix, on release branches |

Checkstyle from v2 can be dropped in favour of the formatter plus Error Prone,
which catch more with less configuration. v2's `UnusedImports` problem disappears
because shared code no longer has guarded imports.

### 15.3 Developer experience

- `./gradlew :core:test :ui:test` runs in seconds and needs no Minecraft.
- A **preview harness** (`ui-preview`, not shipped): a `Canvas` implemented with
  Java2D in a desktop window, so windows and screens can be built and tweaked
  without launching the game, fed by the real `core` engine or by fakes.
- A **CLI harness** in `core` tests: `PlayUrl <url>` plays audio through the real
  engine from a terminal, which is the fastest way to debug resolution and sync.
- `./gradlew :platform:1.21.1-fabric:runClient` (and the NeoForge equivalent) as in
  v2.

---

## 16. Testing strategy

| Level | What | How |
|---|---|---|
| Unit | Link matching per source, `PlayQueue` (repeat/shuffle invariants), `ShortcutTable`, `Snap`, placement, `ShareLink` timestamps, `PlaybackError` patterns, `LinkFilter`, settings validation, migrations, `GifDecoder`, ring buffers | JUnit 5, AssertJ. Property-based tests (jqwik) for the queue and ring buffers. |
| Engine integration | Resolve, start, pause/resume (short and long), seek, end, failure, collection expansion | Real ffmpeg on media generated in the test (`ffmpeg -f lavfi -i testsrc2 … -f lavfi -i sine …`), served by the JDK's `HttpServer`; a fake `AudioSink` that records the clock; yt-dlp replaced by a fake executable that prints canned JSON. Tagged `integration`, run in CI where ffmpeg is installed. |
| UI | Layout, hit testing, drag-to-reorder, text field editing, theme coverage, window snapping and theatre mode | A `RecordingCanvas` that stores draw calls; assertions on what is drawn where, and a few golden snapshots. Input is simulated through `InputEvent`s. |
| Architecture | Layering and package rules | ArchUnit. |
| Platform | The mod loads, opens the chat, renders a window with a texture, plays a generated clip, closes it | Fabric's client game test API where the target supports it; otherwise a manual checklist per release. Check availability per Minecraft version. |

Tests never need network access. Flaky timing is avoided by injecting clocks
(`InstantSource`) and executors.

---

## 17. Modern Java conventions

Baseline Java 21 for `api`, `core` and `ui`.

- **Records** for every value type: events, snapshots, requests, settings values,
  persisted entities, geometry (`Rect`, `Size`, `Insets`). Compact constructors
  validate or canonicalise (v2's `AudioOptions` rule: clamp what can be clamped,
  throw on what cannot mean anything).
- **Sealed interfaces** for closed sets: `PlayerState`, `InputEvent`, `MediaEvent`,
  `Text`, `Cache.Entry`, `ToolState`, `SettingSpec`, `AudioPlacement`. Consumers use
  exhaustive `switch` with record patterns, so adding a case is a compile error at
  every place that must handle it:

```java
String label = switch (snapshot.state()) {
    case PlayerState.Playing(var pos) -> format(pos);
    case PlayerState.Paused(var pos)  -> format(pos) + " ⏸";
    case PlayerState.Seeking(var to)  -> format(to);
    case PlayerState.Failed(var err)  -> err.shortText();
    case PlayerState.Buffering(var pos) -> format(pos);
    case PlayerState.Idle()      -> "";
    case PlayerState.Resolving() -> "";
    case PlayerState.Ended()     -> "";
};
```

  (Java 21 does not allow several patterns in one `case` label; that arrives with
  unnamed patterns in Java 22.)

- **Pattern matching for `instanceof`** instead of casts.
- **Virtual threads** for blocking IO (§10), created through named factories
  (`Thread.ofVirtual().name("lmp-io-", 0).factory()`).
- **`java.net.http.HttpClient`** for all HTTP.
- **Sequenced collections** (`getFirst`, `getLast`, `reversed`) for queues and
  history.
- **`Optional`** as a return type and as a record component that is genuinely
  optional; never as a method parameter or a mutable field.
- **`var`** for local variables when the type is obvious from the right-hand side.
- **Text blocks** for embedded JSON fixtures and long messages in tests.
- **`java.time`** (`Duration`, `Instant`, `InstantSource`) instead of raw
  `long micros` in APIs. Hot loops (the audio pump, frame ring) keep primitives.
- **Immutability by default**: `List.copyOf`, `Map.copyOf` at boundaries; mutable
  state confined to one owner (an actor, the client thread).
- **Null**: JSpecify annotations with `@NullMarked` packages, checked by NullAway.
- **No preview features**, so the same bytecode runs on Java 21 and 25.

If support for 1.21.x is ever dropped, the baseline can move to Java 25 (scoped
values, flexible constructor bodies, module import declarations). Nothing in this
design depends on that.

---

## 18. Migrating from v2

- **Mod id stays `liasmediaplayer`** and the API mod id stays
  `liasmediaplayerapi` at version 4.0.0, so packs upgrade in place and addons see a
  major version change.
- **Data**: on first start, if `settings.json` does not exist but v2 files do,
  `V2Import` reads `config.json` (option ids mapped to the new namespaced ids),
  `playlists.json` and `history.json` (merged into `library.json`) and
  `windows.json`, then renames the originals to `*.v2.bak`. The `bin/` folder is
  reused as is.
- **Settings that change meaning**: `max_resolution` keeps its value but now also
  applies to YouTube; the frame-queue-capacity setting becomes a buffer length in
  seconds; window caps keep their values.
- **Addons**: a short porting guide in the API docs, mapping each 3.x entry point to
  its 4.0 equivalent (`registerSource` → `context.sources().register`,
  `PlaybackEvents.register` → `context.events().on`, `long` ids → handles, `Vec3` →
  `Vec3d`, `Component` → `Text`).

---

## 19. Milestones

Each milestone ends with something that runs.

| # | Content | Done when |
|---|---|---|
| M0 | Repository skeleton: modules, convention plugins, Stonecutter on `platform` with **one** target (1.21.1) on both loaders, ArchUnit and guard check, CI | An empty mod loads on Fabric and NeoForge; CI is green. |
| M1 | `core` headless engine: tools, `Http`, resolver chain with yt-dlp JSON, ffmpeg session, player actor, `JavaSoundSink`, `PlayQueue` | The CLI harness plays a YouTube link and a direct file with seek and pause; integration tests pass. Prototype decisions settled: PAM framing, in-place resume. |
| M2 | `ui` toolkit + `Canvas` + `McCanvas`/`McTextures` + `HostScreen`; `VideoWindow`, `AudioBar` | A video plays in a window in game, from the `/show` command. |
| M3 | Chat: display-time mixin, `ChatRewriter`, labels, hover preview, click routing, filters; images and GIFs; image windows | Every v2 link type works from chat. |
| M4 | Library and settings: playlists, history, settings screen, themes, window state, keybinds, shortcuts, media controls screen, v2 import | Feature parity with v2 for players. |
| M5 | API v4, addon entry points on both loaders, an example addon in the repository, API docs and porting guide | The example addon registers a source, a theme and a window button on both loaders. |
| M6 | Version matrix: add 1.21.11 and the latest 26.x on both loaders (§20, decision 1) | All targets build and pass the smoke test. |
| M7 | Performance pass with JFR and the debug overlay; disk cache; ffmpeg image fallback; polish | Budget in §12 met on a mid-range machine. |

---

## 20. Decisions on the open questions

Decided by the maintainer on 2026-10-29.

| # | Question | Decision |
|---|---|---|
| 1 | Minecraft versions at launch | **1.21.1, 1.21.11 and the latest 26.x**, on both loaders. Others are added back if players ask; adding a target is a `platform`-only change. |
| 2 | Own toolkit or vanilla widgets in screens | **Own toolkit** (§7.1). |
| 3 | Mixins | **The single chat-display mixin** of §5.5; loader events everywhere else. |
| 4 | PAM framing or probe + rawvideo | **PAM** (M1 prototype, §6.4): no measurable cost, and the only one of the two that gets rotated and adaptive streams right. |
| 5 | OpenAL sink | **Open** — not in the first release; prototype after M7. |
| 6 | Hidden-player default | **Keep decoding** (v2 behaviour), with a "sound only when hidden" setting for weak machines. |
| 7 | Disk cache default | **100 MB**, user-adjustable, with a "clear cache" button in settings. |
| 8 | License | **The mod is GPL-3.0** (`LICENSE`); **the API is MIT** (`api/LICENSE`), so addons under any license can build against it. The MIT notice ships in the API jar and, as `LICENSE-API`, in every mod jar beside the GPL text. |
