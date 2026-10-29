package com.lia.mediaplayer.core.library;

import com.lia.mediaplayer.api.source.WebUrls;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Playlists as m3u text, the format every other player reads and writes. Pure.
 *
 * <p>Written as extended m3u: {@code #EXTM3U}, then {@code #EXTINF:-1,<title>} and the link for
 * each entry. The duration is always {@code -1}, because knowing it would take a probe per
 * entry. Read leniently: every line that is not blank and not a {@code #} comment is a link,
 * kept only if it is {@code http(s)} — an m3u file is a list of paths, exactly the shape that
 * would otherwise hand ffmpeg a local file. Stored titles are skipped; the mod finds its own.
 */
public final class M3u {

    private static final String HEADER = "#EXTM3U";

    private M3u() {}

    /**
     * The playlist as m3u text, ending in a newline.
     *
     * @param titleOf the title to write for a link, or {@code null} to write the link itself
     */
    public static String export(Playlist playlist, Function<String, @Nullable String> titleOf) {
        var out = new StringBuilder(HEADER).append('\n');
        for (String url : playlist.urls()) {
            String title = titleOf.apply(url);
            // A newline in a title would end the line early and turn the rest into a link.
            out.append("#EXTINF:-1,")
                    .append(oneLine(title == null ? url : title))
                    .append('\n');
            out.append(url).append('\n');
        }
        return out.toString();
    }

    /** The {@code http(s)} links in {@code content}, in order, repeats kept. Never throws. */
    public static List<String> parse(@Nullable String content) {
        var urls = new ArrayList<String>();
        if (content == null) {
            return urls;
        }
        for (String raw : content.lines().toList()) {
            String line = raw.strip();
            if (!line.isEmpty() && !line.startsWith("#") && WebUrls.isWebUrl(line)) {
                urls.add(line);
            }
        }
        return urls;
    }

    private static String oneLine(String text) {
        return text.replace('\r', ' ').replace('\n', ' ').strip();
    }
}
