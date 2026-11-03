# Lia's Media Player

**Pictures, GIFs, videos and music — right inside Minecraft.**

Lia's Media Player turns media links shared in chat into previews and floating players. Watch a clip, keep a reference picture beside your build, or listen to a playlist while you play.

**Minecraft 1.21.1, 1.21.11 and 26.3 · Fabric & NeoForge · Client-side only**

Install it on your own client; the server and other players do not need it. On Fabric, you also need [Fabric API](https://modrinth.com/mod/fabric-api).

## From chat to your screen

Supported links become compact, coloured labels in chat: **[picture]**, **[gif]**, **[youtube]**, **[audio]**, and more.

- **Hover an image label** to preview it. Animated pictures play in the preview.
- **Click** to pin a picture or start a video or audio player. More video or audio links join the front player's queue.
- **Alt-click a video** to listen to its sound in a compact audio bar.
- **Shift-click** to open a separate player instead of adding to the existing queue.

You can also play a link from your clipboard with a configurable keybind, or use `/show` without posting it in chat.

## Pictures and GIFs

Preview a picture beside your cursor, then click to keep it on screen. Pinned pictures can be moved, resized, hidden, or closed, and stay visible on your HUD during normal gameplay.

- **PNG, JPEG, GIF and BMP**, plus **WebP, animated WebP, AVIF and animated PNG** through ffmpeg.
- **Tenor and Giphy** share links resolve to the GIF itself.
- Previews fit within your screen and scale down large pictures.
- Drag a window's corner to resize it, scroll over a pinned picture to zoom, or use **Ctrl + scroll** to zoom resizable media windows.

## Videos and streams

Click a video label to watch it in a floating window, with sound and playback controls.

| Source | Supported links |
|---|---|
| **YouTube** | Videos, short links, Shorts, live links and playlist pages |
| **Twitch** | Streams and VODs |
| **Vimeo** | Video pages and embeds |
| **Streamable** | Clips and embeds |
| **Reddit** | `v.redd.it` links and video post pages |
| **Direct video** | MP4, WEBM, MOV, MKV, M4V, AVI, FLV, OGV and TS |
| **Streams** | HLS (`.m3u8`) and DASH (`.mpd`) manifests |

The player includes **play/pause, previous/next, ±10-second jumps, a seek bar, volume, mute, loop and shuffle**. Seeking controls are unavailable for live streams without a seekable timeline.

Open the **queue panel** to see what is coming next. Double-click an entry to play it, drag entries into order, or remove the ones you no longer want. Click a YouTube playlist page to queue its videos; Alt-click to listen instead.

**Theatre mode** fills the screen and hides the controls when idle. Double-click the video or press **Ctrl + F** to enter or leave it; your window returns to its previous position and size.

## Music without a big window

Audio opens in a compact bar with playback, seeking, volume, queue, loop and shuffle controls. Hide it and the music keeps playing.

- **SoundCloud** tracks and sets.
- **Bandcamp** tracks and albums.
- **Direct audio:** MP3, WAV, OGG/OGA, FLAC, M4A, AAC, OPUS, WEBA, WMA and AIFF/AIF.
- **Video as audio:** Alt-click a video link to listen without its picture.

## Arrange your media around your game

Open chat to interact with your media windows, or bind **Open the media controls** for a dedicated screen that leaves the world running behind it.

- **Drag, resize and zoom** windows to suit your screen.
- **Snap** windows to the screen and each other. Hold **Shift** while dragging to position them freely.
- **Hide** a player to keep listening; a now-playing banner announces new tracks from hidden players.
- **Keep pictures and videos on your HUD** while you play.
- **Remember your layout:** new windows reuse the saved position and size for their kind, along with player loop, shuffle and queue-panel preferences.
- **Copy a link** from a window, or **Shift-click the copy button** to include the playback time on supported sites.
- **Open the original in your browser** or save it as a favourite from the title bar.

## Saved playlists, history and favourites

Open **Playlists** above the chat, or assign its keybind, to build your own library.

- Create and rename playlists, add links, search tracks, drag them into order, and remove entries.
- Expand a **YouTube playlist or SoundCloud set** into individual tracks.
- Play in order or shuffled, with optional looping.
- Import links from your clipboard and copy playlists out as **M3U** text for sharing.
- See thumbnails beside playlist entries and history rows, with titles and thumbnails cached between visits.

The **History** screen keeps recently played media within reach. Search by name or URL, play something again, or add it to a playlist. Use the **heart** to keep a favourite: favourites survive clearing the ordinary history and are excluded from its size limit.

Playlists, history and favourites are saved between sessions.

## Make it yours

Open **Media Player Settings** from the pause menu, Fabric's Mod Menu, NeoForge's mod list, or a keybind. Settings are grouped and searchable, with descriptions and individual reset buttons. Changes apply immediately and save automatically.

- **Four themes:** Dark, High contrast, Minecraft and Light.
- **Video quality:** choose a maximum of 360p, 480p, 720p or 1080p; the default is 720p.
- **Window preferences:** default placement and limits for pinned pictures, video players and audio bars.
- **Chat preferences:** toggle hover previews, allow or block domains, and ignore media links from selected senders.
- **Performance:** adjust picture memory and disk-cache limits, clear the cache, or let hidden video players switch to sound only after five seconds.
- **Tools:** see their status, control automatic yt-dlp updates, or use **Update the tools** to refresh them manually.
- **Debug overlay:** inspect player resolution, buffers, dropped frames and rendering costs when troubleshooting.

The rewrite adjusts video decoding to the window's size, within your quality limit, and reduces the frame rate at lower resolutions. Pictures are decoded to a size suited to how they are displayed, while cached titles and thumbnails reduce repeat downloads.

## Controls

Assign keys in **Options → Controls → Lia's Media Player** for playback, previous/next, volume, mute, hiding or closing all windows, opening media controls, playlists and settings, or playing the clipboard link. **All are unbound by default.**

These shortcuts also work on the front player while chat or the media controls screen is open:

| Shortcut | Action |
|---|---|
| **Space** | Play / pause |
| **← / →** | Seek back / forward 5 seconds |
| **Shift + ← / →** | Seek back / forward 30 seconds |
| **Ctrl + ↑ / ↓** | Volume up / down |
| **Ctrl + M** | Mute / unmute |
| **Ctrl + L** | Cycle loop: off → queue → track |
| **Ctrl + S** | Toggle shuffle |
| **Ctrl + N / P** | Next / previous track |
| **Ctrl + F** | Toggle theatre mode |

Space and the seek shortcuts only control playback while you are not typing. Ctrl shortcuts remain available while writing a message. On macOS, use **Cmd** in place of Ctrl.

### Chat command

```text
/show <image|video|audio> <url> [newPlayer]
```

Choose `image`, `video` or `audio` and paste the link. Add `true` at the end to request a separate window; otherwise, videos and audio join an existing player's queue. Use `audio` with a video link to play only its sound.

## Getting started and upgrading

Playback uses **ffmpeg** and **yt-dlp**. The mod finds installed copies or downloads them into its own `liasmediaplayer/bin/` folder on first launch. Automatic yt-dlp updates are enabled by default. If playback fails, the player shows an explanation and retry controls, with a tool-update option when appropriate.

**Coming from v2?** The first launch imports your old settings, playlists, history and window preferences, and preserves the original files as `*.v2.bak` backups.

**For addon developers:** the rewrite provides **API 4** for custom media sources, resolvers, themes, window actions, shortcuts, keybinds and settings. Existing addons need to be ported; some older API capabilities are not available yet. See the repository's API and porting guides for details.

---

*Lia's Media Player displays media on your own client. Playback and your saved library are local to you; the mod does not synchronise viewing with other players.*
