# Lia's Media Player API 4

Addons extend Lia's Media Player through a public API that names no Minecraft or loader type.
One API jar serves every Minecraft version and both loaders the mod is built for, and an addon
that uses nothing else compiles once: the [example addon](../examples/example-addon) is a single
jar that loads on Fabric and on NeoForge.

The API is MIT-licensed ([`api/LICENSE`](../api/LICENSE)), so an addon under any license can
build against it. Coming from API 3.x? See [the porting guide](PORTING.md).

- [Getting started](#getting-started)
- [Threads](#threads)
- [What an addon can register](#what-an-addon-can-register)
- [The running mod: `MediaPlayer`](#the-running-mod-mediaplayer)
- [Handles and events](#handles-and-events)
- [Errors in addon code](#errors-in-addon-code)
- [Versions and capabilities](#versions-and-capabilities)
- [Testing an addon](#testing-an-addon)

## Getting started

### 1. Depend on the API

```kotlin
dependencies {
    compileOnly("com.lia.mediaplayer:liasmediaplayer-api:4.0.0")
}
```

`compileOnly`: the mod ships the API classes, and an addon never bundles them. Until the API is
on a public repository, `./gradlew :api:publishAllPublicationsToLocalRepository` in this
repository writes it to `repo/`, which a build can use as a Maven repository.

The API is compiled for Java 21, which every supported Minecraft version runs.

### 2. Implement `MediaPlayerAddon`

```java
public final class MyAddon implements MediaPlayerAddon {
    @Override
    public void register(AddonContext context) {
        context.sources().register(new MySource());
        context.themes().register(ThemeSpec.of("myaddon:sunset", Text.translatable("theme.myaddon.sunset"))
                .with(ThemeRole.FILL, 0xFFFF8A50));
        context.onReady(mod -> this.mod = mod);
    }
}
```

`register` is called once, at startup, on the client thread, before the game's key mappings are
settled. The registries close when it returns.

### 3. Hand it to the mod

**Fabric**: a `"liasmediaplayer"` entrypoint. No initializer of your own is needed.

```json
{
  "entrypoints": {
    "liasmediaplayer": ["com.example.myaddon.MyAddon"]
  },
  "depends": {
    "liasmediaplayerapi": "*"
  }
}
```

The addon's id is its Fabric mod id. Fabric's `provides` carries the mod's version rather than
the API's, so check the API version at runtime instead (see
[Versions and capabilities](#versions-and-capabilities)).

**NeoForge**: from your `@Mod` constructor.

```java
@Mod(value = "myaddon", dist = Dist.CLIENT)
public final class MyAddonMod {
    public MyAddonMod() {
        LiasMediaPlayerApi.registerAddon("myaddon", new MyAddon());
    }
}
```

```toml
[[dependencies.myaddon]]
modId = "liasmediaplayerapi"
type = "required"
versionRange = "[4.0.0,5)"
ordering = "AFTER"
side = "CLIENT"
```

The mod takes every addon handed over once all mods are constructed, which is before NeoForge asks
for key mappings, so the order mods are constructed in does not matter. `registerAddon` throws
if it is called later than that.

The `@Mod` class is the only one that names NeoForge. Fabric never loads it, so one jar with both
`fabric.mod.json` and `META-INF/neoforge.mods.toml` serves both loaders.

## Threads

- `MediaPlayerAddon.register`, `onReady` callbacks, event listeners, window actions, shortcuts and
  key bindings run on the client thread.
- `LinkSource.match` runs wherever the game shows chat, for every link of every message: no IO, no
  blocking, thread-safe.
- `Resolver.resolve` never runs on the client thread and may block.
- On a `MediaHandle`, `id()`, `kind()`, `url()`, `snapshot()` and `isAlive()` are safe from any
  thread. Every control (`play`, `seek`, `queue()`, …) and `MediaPlayer.play` belong to the client
  thread and throw `WrongThreadException` anywhere else.

## What an addon can register

Every id is namespaced, `owner:name`, and the owner must be the addon's own id: `myaddon:share`
for an addon `myaddon`. Two addons cannot collide, and an addon cannot register under the mod's
namespace. Registering the same id twice throws `IllegalArgumentException`.

| Registry | Type | What it does |
|---|---|---|
| `sources()` | `LinkSource` | Recognises links in chat and elsewhere, as a `MediaRef` with a kind and a label. |
| `resolvers()` | `Resolver` | Turns a ref into stream URLs for ffmpeg. Asked before every built-in resolver. |
| `themes()` | `ThemeSpec` | A palette in the theme setting. |
| `windowActions()` | `WindowAction` | A button in the corner row of every window. |
| `shortcuts()` | `ShortcutSpec` | Ctrl+key over the chat. |
| `keyBindings()` | `KeyBindingSpec` | A key the player binds in the controls screen. |
| `settings()` | `SettingSpec` | A setting in the mod's settings screen and file. |
| `interceptors()` | `Interceptor` | Refuses chat links or playback. |
| `events()` | `EventSubscriptions` | Playback events, for the game session. |

### Link sources

```java
final class ArchiveOrgSource implements LinkSource {
    public String id() { return "myaddon:archive_org"; }

    public Optional<MediaRef> match(URI uri, String raw) {
        boolean item = "archive.org".equals(uri.getHost()) && uri.getPath().startsWith("/details/");
        return item ? Optional.of(new MediaRef(raw, MediaKind.VIDEO, id(), Text.literal("[archive]")))
                    : Optional.empty();
    }
}
```

- Sources are asked in descending `priority()`, and an addon's source is asked before the built-in
  sources of the same priority. The first match wins, so a link belongs to one source.
- `uri` has already passed `WebUrls.isWebUrl`: an absolute `http(s)` URL with a host. The ref's
  URL must pass it too; the mod hands it to ffmpeg, yt-dlp and the browser, and this check keeps
  chat links from reaching local files or being read as command-line options.
- A ref keeps the id of the source that made it, whatever `sourceId` the source wrote in it.
- **No resolver needed for most sites.** The mod gives any video or audio ref that no other
  resolver takes to yt-dlp. A source for a site yt-dlp knows is all an addon needs.
- A source can also implement `CollectionSource` (its links are playlists; `entryKind()` says
  whether the entries are videos or sounds) and `TimestampLinks` (the link to a moment, which is
  what the copy button puts on the clipboard when Shift is held).

### Resolvers

`Resolver.handles(ref)` is cheap and thread-safe; `resolve(ref, options)` returns a future of
`ResolvedMedia`: the video and/or audio stream URLs, HTTP headers ffmpeg sends with them,
metadata (title, duration, size), and when the URLs expire. Results are cached until then.
Addon resolvers are asked in the order registered, before the built-in ones.

### Themes

`ThemeSpec` lists only the roles it changes; the rest keep the dark theme's colours, so a role
added in a later version still has one. Colours are `0xAARRGGBB`, and alpha matters: window and
panel backgrounds are translucent in the dark theme. The id is what the player's settings store.
A theme whose addon is removed draws as dark, and the setting keeps the id, so the theme comes
back with the addon.

### Window actions

```java
final class RestartAction implements WindowAction {
    public String id() { return "myaddon:restart"; }
    public ActionIcon icon() { return ActionIcon.RETRY; }
    public Text tooltip() { return Text.translatable("action.myaddon.restart"); }

    public boolean appliesTo(MediaWindow window) {
        return window.handle().isPresent();            // players only
    }

    public void onClick(MediaWindow window, boolean shift) {
        window.handle().ifPresent(handle -> handle.seek(Duration.ZERO));
    }
}
```

- Buttons sit left of the heart, in registration order, at most three per window.
- `ActionIcon` is one of the mod's own glyphs, drawn at the row's size in the theme's colours, so
  the button looks like the mod's in every theme.
- `appliesTo` is asked every frame for every window. Keep it to field reads.
- `MediaWindow` says what the window shows: its kind (`IMAGE`, `VIDEO`, `AUDIO`), its link, and
  for a player its `MediaHandle`. Use it only during the call.

### Shortcuts and key bindings

```java
context.shortcuts().register(ShortcutSpec.ctrl("myaddon:restart", 'R', player -> {
    player.ifPresent(handle -> handle.seek(Duration.ZERO));
    return player.isPresent();
}));
context.keyBindings().register(
        new KeyBindingSpec("myaddon:restart", "key.myaddon.restart", player -> player.ifPresent(h -> h.seek(Duration.ZERO))));
```

- A shortcut is Ctrl (Cmd on macOS) plus a letter or digit, with or without Shift, over the chat.
  The mod's own shortcuts are matched first, so an addon cannot take one of their keys. Return
  `false` when there was nothing to do, so the key goes on to the chat field.
- A key binding starts unbound and is listed under the mod's category in the controls screen. Its
  name key is also its id in the game's options file; keep it stable. It works in the world and
  over the mod's screens.
- Both are told the front-most player: for shortcuts the front-most shown window, for key
  bindings the audio bar before a video window, as the mod's own transport keys do.

### Settings

The same specs the mod uses for its own settings: `BoolSetting`, `IntSetting`, `EnumSetting`,
`ChoiceSetting`, `StringSetting`, `StringListSetting`.

```java
static final BoolSetting RESTART_BUTTON = new BoolSetting(
        SettingInfo.of("myaddon:restart_button", "myaddon:general",
                        Text.translatable("settings.myaddon.restart_button"))
                .withDescription(Text.translatable("settings.myaddon.restart_button.description")),
        true);
```

- The settings screen shows each group on its left; a group's name is the translation key
  `settings.<owner>.group.<name>` (`settings.myaddon.group.general`), from the addon's language file.
- Values are stored in the mod's `settings.json` under the setting's id. A value read from the file
  or set by code goes through the spec's `normalize` first.
- Read and change values through `MediaPlayer.settings()`, with the same spec.

### Interceptors

```java
context.interceptors().register(new Interceptor() {
    public String id() { return "myaddon:party"; }

    public boolean allowChatLink(MediaRef ref, Optional<String> sender) {
        return sender.map(party::isMember).orElse(true);
    }

    public boolean allowPlay(MediaRef ref, PlayOrigin origin) {
        return origin == PlayOrigin.API || party.allows(ref.url());
    }
});
```

The player's own filter (blocked hosts and senders) comes first; an interceptor can only refuse
more. The first refusal wins. `PlayOrigin` says who asked: `CHAT_CLICK`, `COMMAND`, `KEYBIND`,
`PLAYLIST`, `HISTORY` or `API`. A refused chat link stays in the message as plain text.

## The running mod: `MediaPlayer`

`context.onReady(callback)` hands over the running mod once every addon is registered.

| Method | |
|---|---|
| `apiVersion()`, `supports(Capability)` | What the installed mod implements. |
| `recognize(url)` | What a source makes of a link, as a chat message would. |
| `play(MediaRequest)` | Plays a link as a click in chat would, through the interceptors. Returns the playback's handle; empty for a picture (pinned, with no playback), a link no source claims, or a refusal. |
| `handles()` | Every open playback. |
| `events()` | Event subscriptions, counted against this addon. |
| `library()` | The player's playlists and history, read only. |
| `settings()` | The values of registered settings. |

`MediaRequest.of(url)` plays as a plain click; `withAudioOnly(true)` is Alt-click (the audio
bar), `withNewWindow(true)` is Shift-click (a window of its own, within the window caps).

The history is personal data: what the player watched and when. Do not send it anywhere without
asking them.

## Handles and events

A `MediaHandle` is one playback: a queue, the track playing now, and a volume. The same playback
always has the same handle. `snapshot()` is a consistent view (`PlaybackState`, position,
duration, title, tracks). The controls are `play`, `pause`, `togglePause`, `seek`, `next`,
`previous`, `setVolume`, `close`, and `queue()` for the waiting tracks (`entries`, `add`,
`jumpTo`, `remove`, `move`, `clear`, repeat and shuffle). On a closed handle they do nothing.

Events are a sealed hierarchy:

```java
context.events().on(MediaEvent.TrackChanged.class, event -> nowPlaying.set(event.url()));
context.events().onAny(event -> {
    switch (event) {
        case MediaEvent.Started started -> {}
        case MediaEvent.Paused(var handle, var at) -> {}
        case MediaEvent.Resumed(var handle, var at) -> {}
        case MediaEvent.Seeked(var handle, var from, var to) -> {}
        case MediaEvent.TrackChanged(var handle, var url) -> {}
        case MediaEvent.Ended ended -> {}
        case MediaEvent.Failed(var handle, var failure) -> log(failure.causeId(), failure.detail());
        case MediaEvent.Closed closed -> {}
    }
});
```

For one playback, events come in the order they happened: `TrackChanged`, `Started` once it
plays, then any `Paused`, `Resumed` and `Seeked`, then `Ended` or `Failed`, and the next
`TrackChanged`; `Closed` is last. Listeners run on the client thread. `FailureRecord.causeId()` is
a stable id (`unavailable`, `private`, `geo_blocked`, `tool_missing`, …); new ones may appear, so
a switch over it needs a default.

## Errors in addon code

Every call into an addon goes through a guard. An exception is logged once with the addon's id
and the registration's id, and the call is treated as having found nothing, allowed, or done
nothing. A registration that fails ten times is disabled with a warning until the game restarts,
and the rest of the addon keeps working. An exception in `register` is logged, and what was
registered before it stays.

## Versions and capabilities

- The API follows semantic versioning. `ApiVersion.CURRENT` is the version an addon compiled
  against; `mod.apiVersion().satisfies(new ApiVersion(4, 1, 0))` asks whether the installed mod
  has at least what 4.1.0 added.
- `Capability` names every planned feature, including ones that have not shipped:
  `mod.supports(Capability.SURFACES)` is `false` in 4.0. Constants are never removed, so asking
  is safe against an older mod.
- Registries and methods are added in minor versions. The mod implements `AddonContext`,
  `MediaPlayer`, `MediaHandle` and the other interfaces it hands out; addons only call them, so
  a method added to one of them does not break an addon.

Not in 4.0 yet, each behind a `Capability` that answers `false`: picture decoders for formats the
mod does not read, buttons into an addon's screen from the playlist and history screens, surfaces
(media drawn into a texture the addon draws), sound with no window and the mixer channels, sound
placed in the world, watch-together hooks, and the counters and failure log. Also not yet:
placement, sizing and chrome in `MediaRequest`, and editing the library.

## Testing an addon

An addon that uses only the API is plain Java and is tested without the game: hand `register` an
`AddonContext` that records what it is given.
[`ExampleAddonTest`](../examples/example-addon/src/test/java/com/example/lmpaddon/ExampleAddonTest.java)
does exactly that.

To try one in the game from this repository, run a development client with the example addon:

```bash
./gradlew :platform:1.21.1-fabric:runClient -Plmp.exampleAddon
```

```bash
./gradlew :platform:1.21.1-neoforge:runClient -Plmp.exampleAddon
```

The log then says what it registered:

```
[UiAddons] Addon exampleaddon: {settings=1, themes=1, window actions=1, sources=1, key bindings=1, shortcuts=1}
```
