# Example addon

An addon of Lia's Media Player that uses nothing but its [API](../../docs/API.md), built the way an
addon outside this repository would be: a plain Java library, no Minecraft, no loader plugin. It
is one jar for both loaders and every Minecraft version the mod is built for.

It adds:

- **a link source**: Internet Archive item pages (`https://archive.org/details/…`) become an
  `[archive]` label in chat and play through yt-dlp;
- **a theme**, "Sunset", in the mod's theme setting;
- **a window button**, "Restart", on video windows and audio bars (Shift: and play), with a
  setting under "Example addon" that hides it;
- the same restart as **Ctrl+R** over the chat and as **a key** to bind in the controls screen;
- a log line for every track that starts.

| File | What |
|---|---|
| [`ExampleAddon.java`](src/main/java/com/example/lmpaddon/ExampleAddon.java) | The addon: everything it registers. |
| [`ArchiveOrgSource.java`](src/main/java/com/example/lmpaddon/ArchiveOrgSource.java) | The link source. |
| [`ExampleAddonMod.java`](src/main/java/com/example/lmpaddon/neoforge/ExampleAddonMod.java) | NeoForge's entry: hands the addon over. The only class that names a loader. |
| [`fabric.mod.json`](src/main/resources/fabric.mod.json) | Fabric's entry: the `liasmediaplayer` entrypoint. |
| [`neoforge.mods.toml`](src/main/resources/META-INF/neoforge.mods.toml) | NeoForge's mod entry and the dependency on the API. |
| [`ExampleAddonTest.java`](src/test/java/com/example/lmpaddon/ExampleAddonTest.java) | Tests the addon without the game. |

Build it:

```bash
./gradlew :example-addon:build
```

The jar lands in `examples/example-addon/build/libs/` and goes in the mods folder beside the mod,
on either loader. To try it in a development client instead:

```bash
./gradlew :platform:1.21.1-fabric:runClient -Plmp.exampleAddon
```
