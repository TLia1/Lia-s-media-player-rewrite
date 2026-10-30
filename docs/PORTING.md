# Porting an addon from API 3.x to 4.0

API 4.0 comes with the rewrite of the mod (v3). It is a new major version: nothing of 3.x
compiles unchanged. Three changes run through all of it:

- **One entry point.** An addon implements `MediaPlayerAddon` and registers everything from its
  `register(AddonContext)`, at one moment the mod chooses, on both loaders. No static
  registration methods, no "register from your entry point or your key never works".
- **No Minecraft types.** `Component` is `api.text.Text`, a `ResourceLocation` is a namespaced
  `String` id, and nothing takes a `GuiGraphics`, `KeyMapping` or `Minecraft`. The same addon jar
  works on every Minecraft version the mod is built for.
- **Handles, not ids.** The `long`-id methods of API 2.0 are gone; playbacks are `MediaHandle`s.

The [API guide](API.md) describes 4.0 in full. This page maps 3.x to it.

## Getting in

| 3.x | 4.0 |
|---|---|
| `LiasMediaPlayerApi.getInstance()` → `IMediaPlayerAPI` | `context.onReady(mod -> …)` → `MediaPlayer` |
| `PlaybackEvent.Type.LIFECYCLE_READY` | `context.onReady(…)` |
| NeoForge: calls from the `@Mod` constructor | `LiasMediaPlayerApi.registerAddon("myaddon", new MyAddon())` from the `@Mod` constructor |
| Fabric: calls from `onInitializeClient`, or the `liasmediaplayer:sources` entrypoint | the `liasmediaplayer` entrypoint, naming the `MediaPlayerAddon` class |
| `ApiVersion.supports(Capability)` (static) | `mod.supports(Capability)` |
| `ApiVersion.asString()`, `atLeast(…)` | `mod.apiVersion()`, `ApiVersion.satisfies(…)` |
| NeoForge `versionRange="[3.4.0,)"` | `versionRange="[4.0.0,5)"` |

Registration is only possible while `register` runs. Code that registered later (from a world
join, a command) must register at startup instead, and decide at runtime whether to act.

## Sources, resolvers and metadata

| 3.x | 4.0 |
|---|---|
| `MediaSource` (`matches(url)`, `kind()`, `label(url)`) | `LinkSource` (`id()`, `match(URI, raw)` → `Optional<MediaRef>`, `priority()`) |
| `MediaSourceProvider`, `MediaSourceRegistrationEvent.register` | `context.sources().register(source)` |
| `IMediaPlayerAPI.registerSource(source)` | `context.sources().register(source)`, at startup |
| `MediaKind.IMAGE / VIDEO / AUDIO` | the same, plus `COLLECTION` for playlists (with `CollectionSource.entryKind()`) |
| `source.MediaResolver`, `registerResolver` | `Resolver` (`id()`, `handles`, `resolve` → `CompletableFuture<ResolvedMedia>`), `context.resolvers().register` |
| `MediaMetadataProvider`, `MediaMetadata` | the `MediaMetadata` of your `ResolvedMedia`: title, duration, thumbnail, size |
| the mod deciding "copy at the current time" for its own sites | `TimestampLinks.linkAt(ref, position)` on your source |

A label that was a styled `Component` is a `Text` now (`Text.literal`, `Text.translatable`).

## Playing and controlling

| 3.x | 4.0 |
|---|---|
| `playVideo(url)`, `playAudio(url)` → `long` | `mod.play(MediaRequest.of(url))`, `.withAudioOnly(true)` → `Optional<MediaHandle>` |
| `play(MediaRequest)` with `placement`, `sizing`, `chrome` | `MediaRequest(url, audioOnly, newWindow)`; placement, sizing and chrome are not in 4.0 |
| `togglePause(long)`, `close(long)`, `seekVideo(fraction)` | `handle.togglePause()`, `handle.close()`, `handle.seek(Duration)` |
| `getHandle(long)` | keep the `MediaHandle`; `mod.handles()` lists the open ones |
| `MediaHandle.addListener` | `context.events()` and compare `event.handle()` |
| `PlaybackState` `LOADING / PLAYING / PAUSED / ENDED / FAILED` | `PlaybackState` `IDLE / RESOLVING / BUFFERING / PLAYING / PAUSED / SEEKING / ENDED / FAILED`, read from `handle.snapshot()` |
| positions in microseconds (`long`) | `java.time.Duration` |
| `MediaQueue`, `QueueEntry` | `handle.queue()` → `QueueView`, `QueueEntry(url, kind, sourceId)` |
| front-most player helpers | the `Optional<MediaHandle>` handed to shortcuts, key bindings and window actions |

A handle's controls belong to the client thread and throw `WrongThreadException` elsewhere,
instead of misbehaving.

## Events

| 3.x | 4.0 |
|---|---|
| `PlaybackEvents.register(listener)` | `context.events().onAny(listener)`, or `.on(MediaEvent.Paused.class, …)` for one type |
| `PlaybackEvents.unregister` | `close()` the `Subscription` |
| `PlaybackEvent.getType()` `STARTED`, `PAUSED`, `RESUMED`, `SEEKED`, `ENDED`, `FAILED`, `STOPPED` | `MediaEvent.Started`, `Paused(at)`, `Resumed(at)`, `Seeked(from, to)`, `Ended`, `Failed(failure)`, `Closed` |
| `QUEUE_CHANGED`, `METADATA_RESOLVED`, `WORLD_LEFT` | not in 4.0; `TrackChanged(url)` is new, and leaving a world closes every playback (`Closed`) |
| `getPlayerKind()` | `event.handle().kind()` |
| listeners on whichever thread caused the change | always the client thread |

A pinned picture has no playback and sends no events.

## Settings

| 3.x | 4.0 |
|---|---|
| `ConfigOption<T>` | `SettingSpec<T>` with a `SettingInfo` (id, group, label, description, warning) |
| `IntSliderOption` | `IntSetting.of(info, default, min, max)`, `.withFormat(key)` |
| `EnumOption<E>` | `EnumSetting<E>(info, default, names)` |
| `StepSliderOption<T>` | `ChoiceSetting` (ids and names), or `EnumSetting` |
| — | `BoolSetting`, `StringSetting`, `StringListSetting` |
| `registerConfigOption(option)` | `context.settings().register(spec)` |
| `option.getValue()` | `mod.settings().get(spec)`; `set`, `onChange` beside it |
| group `"myaddon"` | a namespaced group, `"myaddon:general"`, named by the key `settings.myaddon.group.general` |
| labels as translation key strings | `Text` |

Values written by 3.x under an addon's option id are kept by the mod's v2 import and read again
if the 4.0 setting has the same id and a compatible value.

## Buttons, keys and themes

| 3.x | 4.0 |
|---|---|
| `registerWindowAction(WindowAction)` | `context.windowActions().register(action)` |
| `WindowAction.tooltip()` → `Component` | → `Text` |
| `appliesTo(MediaHandle)`, `onClick(MediaHandle)` | `appliesTo(MediaWindow)`, `onClick(MediaWindow, shift)`; picture windows get the button too, with no handle |
| `ActionIcon` | `api.ui.ActionIcon`, the same idea, more glyphs |
| `MediaKeybinds.register(KeyMapping, minecraft -> …)` | `context.keyBindings().register(new KeyBindingSpec(id, nameKey, player -> …))`; the mod makes the `KeyMapping` |
| `MediaKeybinds.registerWindowShortcut(keyCode, mods, action)` | `context.shortcuts().register(ShortcutSpec.ctrl(id, 'J', player -> …))` |
| `MediaThemes.register(MediaTheme.builder(id, name).set(role, argb).build())` | `context.themes().register(ThemeSpec.of(id, name).with(ThemeRole, argb))` |

## Interceptors

| 3.x | 4.0 |
|---|---|
| `registerInterceptor(MediaInterceptor)` | `context.interceptors().register(interceptor)`, with an `id()` |
| `allowChatLink(url, sender, kind)` | `allowChatLink(MediaRef, Optional<String> sender)` |
| `beforePlay(request, origin)` returning `null` to refuse | `allowPlay(ref, origin)` returning `false` |
| `beforePlay` returning a rewritten request | not in 4.0 |
| `decorateLabel(url, kind, label)` | not in 4.0 |
| `PlayOrigin.RESTORE` | gone: windows are not restored across sessions |

## Library

| 3.x | 4.0 |
|---|---|
| `getPlaylists()`, `PlaylistInfo` | `mod.library().playlists()` → `PlaylistView(id, name, urls)`; a playlist is followed by its id |
| `getHistory(limit)`, `HistoryItem` | `mod.library().history()` → `HistoryView` |
| `isFavorite(url)` | `mod.library().isFavorite(url)` |
| `setFavorite`, `clearHistory`, playlist edits, m3u | not in 4.0: the library is read only |

## Not in 4.0 yet

Each of these has a `Capability` that answers `false` in 4.0 and will answer `true` in the
version that brings it back. Check before relying on one.

| 3.x | Capability |
|---|---|
| `render.MediaSurface`, `MediaSurfaces`, `MediaGraphics`, `MediaWidget`, `SurfacePixels` | `SURFACES` |
| `audio.MediaAudio`, `AudioOptions`, `AudioControls`, `MediaMixer`, `AudioChannel` | `HEADLESS_AUDIO` |
| `audio.AudioPlacement` (world, entity) | `POSITIONAL_AUDIO` |
| `sync.MediaSync`, `PlaybackSyncAdapter`, `SyncAction`, `SyncControl` | `SYNC` |
| `image.ImageDecoder`, `DecodedImage` | `IMAGE_DECODERS` |
| `screen.MediaScreenTab` | `SCREEN_ENTRIES` |
| `diag.MediaDiagnostics`, `MediaPlayerStats`, `MediaPlayerLog` | `DIAGNOSTICS` |
| `tools.MediaTools`, `MediaInfo` | none planned: probing stays inside the mod |

When they come back, positions are `api.audio.Vec3d` records instead of `Vec3`, and following an
entity is a `Supplier<Vec3d>` the addon writes, `() -> new Vec3d(e.getX(), e.getY(), e.getZ())`.
