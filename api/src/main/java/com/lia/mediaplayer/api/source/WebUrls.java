package com.lia.mediaplayer.api.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The one shape of URL the mod hands to an external tool: absolute {@code http} or
 * {@code https} with a host.
 *
 * <p>Media URLs reach ffmpeg, yt-dlp and the system browser, all of which interpret far
 * more than {@code http(s)}: {@code file:}, {@code concat:}, a custom protocol handler, or a
 * string starting with {@code -} that a command-line tool reads as an option. Chat links can
 * be crafted by anyone on a server, so every link, resolver answer and imported playlist
 * entry passes this gate before it goes further.
 */
public final class WebUrls {

    private WebUrls() {}

    /** Whether {@code url} is an absolute {@code http(s)} URL with a host. */
    public static boolean isWebUrl(String url) {
        if (url.isBlank() || url.startsWith("-")) {
            return false;
        }
        try {
            var uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return false;
            }
            // getHost() is null for hosts URI considers non-compliant (an underscore, for
            // one), which are unusual but real; the raw authority still says a host was given.
            // "file:///x.mp4" and "https:///x.mp4" have neither.
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return true;
            }
            String authority = uri.getAuthority();
            return authority != null && !authority.isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Returns {@code url} if it passes {@link #isWebUrl}.
     *
     * @throws IllegalArgumentException otherwise
     */
    public static String require(String url) {
        if (!isWebUrl(url)) {
            throw new IllegalArgumentException("Not an http(s) URL: '" + url + "'");
        }
        return url;
    }
}
