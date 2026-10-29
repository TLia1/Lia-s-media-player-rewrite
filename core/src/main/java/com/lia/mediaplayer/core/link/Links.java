package com.lia.mediaplayer.core.link;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Small URI helpers shared by the built-in sources. */
final class Links {

    private Links() {}

    /** The lower-cased host without a leading {@code www.}, or an empty string. */
    static String host(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return "";
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /** The lower-cased path, or an empty string. */
    static String path(URI uri) {
        String path = uri.getPath();
        return path == null ? "" : path.toLowerCase(Locale.ROOT);
    }

    /** The lower-cased extension of the path's last segment, without the dot. */
    static String extension(URI uri) {
        String path = path(uri);
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(dot + 1) : "";
    }

    /** The lower-cased path's non-empty segments: {@code /a//b/} is {@code [a, b]}. */
    static List<String> segments(URI uri) {
        return split(path(uri));
    }

    /** The non-empty segments of {@code path}, as written. */
    static List<String> split(String path) {
        var segments = new ArrayList<String>();
        for (String segment : path.split("/", -1)) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    /** The first non-blank value of query parameter {@code name}. */
    static Optional<String> query(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return Optional.empty();
        }
        for (String pair : query.split("&", -1)) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                String value = pair.substring(eq + 1);
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }
}
