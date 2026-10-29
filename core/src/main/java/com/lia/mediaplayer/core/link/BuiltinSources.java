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
 * The sources the mod ships with: v2's sites and file types, one rule each, disjoint so a link
 * belongs to exactly one of them. Where two readings of a site overlap (a playlist page and a
 * video page), the narrower one has the higher priority.
 */
public final class BuiltinSources {

    public static final String PICTURE = id("picture");
    public static final String TENOR = id("tenor");
    public static final String GIPHY = id("giphy");
    public static final String YOUTUBE = id("youtube");
    public static final String YOUTUBE_PLAYLIST = id("youtube_playlist");
    public static final String TWITCH = id("twitch");
    public static final String VIMEO = id("vimeo");
    public static final String STREAMABLE = id("streamable");
    public static final String REDDIT = id("reddit");
    public static final String SOUNDCLOUD = id("soundcloud");
    public static final String SOUNDCLOUD_SET = id("soundcloud_set");
    public static final String BANDCAMP = id("bandcamp");
    public static final String BANDCAMP_ALBUM = id("bandcamp_album");
    public static final String VIDEO_FILE = id("video_file");
    public static final String AUDIO_FILE = id("audio_file");
    public static final String STREAM = id("stream");

    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "m.youtube.com", "music.youtube.com");
    private static final Set<String> SOUNDCLOUD_HOSTS =
            Set.of("soundcloud.com", "m.soundcloud.com", "on.soundcloud.com", "snd.sc");
    private static final Set<String> REDDIT_HOSTS =
            Set.of("reddit.com", "old.reddit.com", "new.reddit.com", "m.reddit.com");
    /** What the image decoders read without ffmpeg. WebP and AVIF join with the ffmpeg fallback. */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");

    private static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mp4", "webm", "mov", "mkv", "m4v", "avi", "flv", "ogv", "ts");
    private static final Set<String> AUDIO_EXTENSIONS =
            Set.of("mp3", "wav", "ogg", "oga", "flac", "m4a", "aac", "opus", "weba", "wma", "aiff", "aif");
    private static final Set<String> STREAM_EXTENSIONS = Set.of("m3u8", "mpd");

    /** Giphy media ids are long; a one-word slug without one ({@code /gifs/cats}) is not a GIF. */
    private static final int GIPHY_MIN_ID = 8;

    private static final int STREAMABLE_MAX_ID = 12;

    private BuiltinSources() {}

    /** Every built-in source. */
    public static List<LinkSource> all() {
        return List.of(
                new Simple(
                        PICTURE, MediaKind.IMAGE, "picture", 0, uri -> IMAGE_EXTENSIONS.contains(Links.extension(uri))),
                new Simple(TENOR, MediaKind.IMAGE, "gif", 0, BuiltinSources::isTenorPage),
                new Simple(
                        GIPHY, MediaKind.IMAGE, "gif", 0, uri -> giphyGif(uri).isPresent()),
                // A playlist page is the narrower reading of a YouTube link, so it is asked first.
                new Collection(
                        YOUTUBE_PLAYLIST, "youtube_playlist", MediaKind.VIDEO, BuiltinSources::isYouTubePlaylist),
                new Simple(YOUTUBE, MediaKind.VIDEO, "youtube", 0, BuiltinSources::isYouTubeVideo),
                new Simple(TWITCH, MediaKind.VIDEO, "twitch", 0, BuiltinSources::isTwitch),
                new Simple(VIMEO, MediaKind.VIDEO, "vimeo", 0, BuiltinSources::isVimeo),
                new Simple(STREAMABLE, MediaKind.VIDEO, "streamable", 0, BuiltinSources::isStreamable),
                new Simple(REDDIT, MediaKind.VIDEO, "reddit", 0, BuiltinSources::isRedditVideo),
                new Collection(SOUNDCLOUD_SET, "soundcloud_set", MediaKind.AUDIO, BuiltinSources::isSoundCloudSet),
                new Simple(SOUNDCLOUD, MediaKind.AUDIO, "soundcloud", 0, BuiltinSources::isSoundCloud),
                new Collection(BANDCAMP_ALBUM, "bandcamp_album", MediaKind.AUDIO, BuiltinSources::isBandcampAlbum),
                new Simple(BANDCAMP, MediaKind.AUDIO, "bandcamp", 0, BuiltinSources::isBandcampTrack),
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

    /** The translation keys of the labels the built-in sources give their links. */
    public static List<String> labelKeys() {
        return all().stream()
                .map(source -> source instanceof Simple simple ? simple.labelKey() : ((Collection) source).labelKey())
                .distinct()
                .toList();
    }

    /** The sources whose links are read as they are, with no resolution: files and manifests. */
    public static boolean isDirect(String sourceId) {
        return sourceId.equals(VIDEO_FILE)
                || sourceId.equals(AUDIO_FILE)
                || sourceId.equals(STREAM)
                || sourceId.equals(PICTURE);
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

    /** {@code soundcloud.com/<artist>/sets/<name>}: a playlist or an album. */
    static boolean isSoundCloudSet(URI uri) {
        return SOUNDCLOUD_HOSTS.contains(Links.host(uri))
                && Links.segments(uri).size() >= 3
                && Links.segments(uri).get(1).equals("sets");
    }

    /** Artists get their own sub-domain: {@code <artist>.bandcamp.com/track/<name>}. */
    static boolean isBandcampTrack(URI uri) {
        return isBandcampHost(uri) && Links.path(uri).startsWith("/track/");
    }

    static boolean isBandcampAlbum(URI uri) {
        return isBandcampHost(uri) && Links.path(uri).startsWith("/album/");
    }

    private static boolean isBandcampHost(URI uri) {
        String host = Links.host(uri);
        return host.equals("bandcamp.com") || host.endsWith(".bandcamp.com");
    }

    /** A stream or a video on {@code twitch.tv}: anything with a path. */
    static boolean isTwitch(URI uri) {
        String host = Links.host(uri);
        return (host.equals("twitch.tv") || host.equals("m.twitch.tv"))
                && Links.path(uri).length() > 1;
    }

    /**
     * {@code vimeo.com/<id>} (and the unlisted {@code /<id>/<hash>}) or {@code
     * player.vimeo.com/video/<id>}. The numeric id is what separates a video from the site's
     * channels, groups and search pages.
     */
    static boolean isVimeo(URI uri) {
        String host = Links.host(uri);
        List<String> segments = Links.segments(uri);
        if (host.equals("player.vimeo.com")) {
            return segments.size() >= 2 && segments.getFirst().equals("video") && isDigits(segments.get(1));
        }
        return host.equals("vimeo.com") && !segments.isEmpty() && isDigits(segments.getFirst());
    }

    /**
     * {@code streamable.com/<id>} or its {@code /e/<id>} embed. The id is a short alphanumeric
     * code; requiring that shape keeps the site's own pages ({@code /login}) out.
     */
    static boolean isStreamable(URI uri) {
        if (!Links.host(uri).equals("streamable.com")) {
            return false;
        }
        List<String> segments = Links.segments(uri);
        if (!segments.isEmpty() && segments.getFirst().equals("e")) {
            segments = segments.subList(1, segments.size());
        }
        if (segments.isEmpty()) {
            return false;
        }
        String id = segments.getFirst();
        return !id.isEmpty() && id.length() <= STREAMABLE_MAX_ID && isAlphanumeric(id);
    }

    /**
     * A {@code v.redd.it/<id>} share link, or the comment page a post lives on. Listings
     * (a subreddit, a user page) are not claimed: most of what is on them is not a video.
     */
    static boolean isRedditVideo(URI uri) {
        String host = Links.host(uri);
        if (host.equals("v.redd.it")) {
            return Links.path(uri).length() > 1;
        }
        return REDDIT_HOSTS.contains(host) && Links.path(uri).contains("/comments/");
    }

    /**
     * A Tenor share page, {@code tenor.com/view/…} or with a language prefix ({@code /fr/view/…}).
     * Tenor's own media links end in {@code .gif} and are pictures.
     */
    static boolean isTenorPage(URI uri) {
        return Links.host(uri).equals("tenor.com") && Links.path(uri).contains("/view/");
    }

    /**
     * The GIF behind a Giphy page ({@code giphy.com/gifs/<words>-<id>}, {@code /clips/},
     * {@code /stickers/}), built from the page's media id with no request: Giphy serves every
     * GIF from a fixed address.
     */
    public static Optional<String> giphyGif(URI uri) {
        if (!Links.host(uri).equals("giphy.com")) {
            return Optional.empty();
        }
        // The id is case-sensitive, so it is read from the path as written.
        String rawPath = uri.getPath();
        if (rawPath == null) {
            return Optional.empty();
        }
        List<String> segments = Links.split(rawPath);
        if (segments.size() < 2 || !Set.of("gifs", "clips", "stickers").contains(segments.getFirst())) {
            return Optional.empty();
        }
        String slug = segments.get(1);
        String id = slug.substring(slug.lastIndexOf('-') + 1);
        if (id.length() < GIPHY_MIN_ID || !isAlphanumeric(id)) {
            return Optional.empty();
        }
        return Optional.of("https://i.giphy.com/media/" + id + "/giphy.gif");
    }

    private static boolean isDigits(String text) {
        return !text.isEmpty() && text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static boolean isAlphanumeric(String text) {
        return text.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
    }

    private static String id(String name) {
        return ModInfo.MOD_ID + ":" + name;
    }

    private static String labelKey(String labelName) {
        return "chat." + ModInfo.MOD_ID + ".label." + labelName;
    }

    private record Simple(String id, MediaKind kind, String labelName, int priority, Predicate<URI> test)
            implements LinkSource {

        String labelKey() {
            return BuiltinSources.labelKey(labelName);
        }

        @Override
        public Optional<MediaRef> match(URI uri, String raw) {
            if (!test.test(uri)) {
                return Optional.empty();
            }
            return Optional.of(new MediaRef(raw, kind, id, Text.translatable(labelKey())));
        }
    }

    /** A page that lists other links. It is the narrower reading of its site, so it is asked first. */
    private record Collection(String id, String labelName, MediaKind entryKind, Predicate<URI> test)
            implements CollectionSource {

        String labelKey() {
            return BuiltinSources.labelKey(labelName);
        }

        @Override
        public int priority() {
            return 10;
        }

        @Override
        public Optional<MediaRef> match(URI uri, String raw) {
            if (!test.test(uri)) {
                return Optional.empty();
            }
            return Optional.of(new MediaRef(raw, MediaKind.COLLECTION, id, Text.translatable(labelKey())));
        }
    }
}
