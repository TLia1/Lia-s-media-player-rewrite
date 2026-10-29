package com.lia.mediaplayer.api.source;

/**
 * One URL ffmpeg reads from.
 *
 * @param url always passes {@link WebUrls#isWebUrl}, so a resolver cannot point ffmpeg at a
 *     local file
 * @param protocol how it is served, as yt-dlp names it ({@code "https"}, {@code "m3u8_native"},
 *     …); informational
 */
public record StreamUrl(String url, String protocol) {

    public StreamUrl {
        WebUrls.require(url);
    }

    public static StreamUrl of(String url) {
        return new StreamUrl(url, "https");
    }
}
