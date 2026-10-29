package com.lia.mediaplayer.core.link;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.ModInfo;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The sources the mod ships with. The playback milestone needs YouTube, SoundCloud and
 * direct files; the other sites of v2 (Twitch, Vimeo, Streamable, Reddit, Bandcamp, image
 * hosts) join with chat integration.
 */
public final class BuiltinSources {

    public static final String YOUTUBE = id("youtube");
    public static final String YOUTUBE_PLAYLIST = id("youtube_playlist");
    public static final String SOUNDCLOUD = id("soundcloud");
    public static final String VIDEO_FILE = id("video_file");
    public static final String AUDIO_FILE = id("audio_file");
    public static final String STREAM = id("stream");

    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "m.youtube.com", "music.youtube.com");
    private static final Set<String> SOUNDCLOUD_HOSTS =
            Set.of("soundcloud.com", "m.soundcloud.com", "on.soundcloud.com", "snd.sc");
    private static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mp4", "webm", "mov", "mkv", "m4v", "avi", "flv", "ogv", "ts");
    private static final Set<String> AUDIO_EXTENSIONS =
            Set.of("mp3", "wav", "ogg", "oga", "flac", "m4a", "aac", "opus", "weba", "wma", "aiff", "aif");
    private static final Set<String> STREAM_EXTENSIONS = Set.of("m3u8", "mpd");

    private BuiltinSources() {}

    /** Every built-in source. */
    public static List<LinkSource> all() {
        return List.of(
                // A playlist page is the narrower reading of a YouTube link, so it is asked first.
                new Simple(
                        YOUTUBE_PLAYLIST,
                        MediaKind.COLLECTION,
                        "youtube_playlist",
                        10,
                        BuiltinSources::isYouTubePlaylist),
                new Simple(YOUTUBE, MediaKind.VIDEO, "youtube", 0, BuiltinSources::isYouTubeVideo),
                new Simple(SOUNDCLOUD, MediaKind.AUDIO, "soundcloud", 0, BuiltinSources::isSoundCloud),
                new Simple(
                        VIDEO_FILE,
                        MediaKind.VIDEO,
                        "video",
                        0,
                        uri -> VIDEO_EXTENSIONS.contains(Links.extension(uri))),
                new Simple(
                        AUDIO_FILE,
                        MediaKind.AUDIO,
                        "audio",
                        0,
                        uri -> AUDIO_EXTENSIONS.contains(Links.extension(uri))),
                new Simple(
                        STREAM, MediaKind.VIDEO, "stream", 0, uri -> STREAM_EXTENSIONS.contains(Links.extension(uri))));
    }

    /** The sources whose links ffmpeg opens as they are, with no resolution. */
    public static boolean isDirect(String sourceId) {
        return sourceId.equals(VIDEO_FILE) || sourceId.equals(AUDIO_FILE) || sourceId.equals(STREAM);
    }

    /** {@code youtube.com/watch}, {@code youtu.be/…}, {@code /shorts/}, {@code /embed/}, {@code /live/}. */
    static boolean isYouTubeVideo(URI uri) {
        String host = Links.host(uri);
        String path = Links.path(uri);
        if (host.equals("youtu.be")) {
            return path.length() > 1;
        }
        return YOUTUBE_HOSTS.contains(host)
                && (path.startsWith("/watch")
                        || path.startsWith("/shorts/")
                        || path.startsWith("/embed/")
                        || path.startsWith("/live/"));
    }

    /** {@code youtube.com/playlist?list=…}. A watch link with a {@code list} plays the one video. */
    static boolean isYouTubePlaylist(URI uri) {
        return YOUTUBE_HOSTS.contains(Links.host(uri))
                && Links.path(uri).startsWith("/playlist")
                && Links.query(uri, "list").isPresent();
    }

    static boolean isSoundCloud(URI uri) {
        return SOUNDCLOUD_HOSTS.contains(Links.host(uri)) && Links.path(uri).length() > 1;
    }

    private static String id(String name) {
        return ModInfo.MOD_ID + ":" + name;
    }

    private record Simple(String id, MediaKind kind, String labelName, int priority, Predicate<URI> test)
            implements LinkSource {

        @Override
        public Optional<MediaRef> match(URI uri, String raw) {
            if (!test.test(uri)) {
                return Optional.empty();
            }
            return Optional.of(
                    new MediaRef(raw, kind, id, Text.translatable("chat." + ModInfo.MOD_ID + ".label." + labelName)));
        }
    }
}
