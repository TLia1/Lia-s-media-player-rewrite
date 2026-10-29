package com.lia.mediaplayer.core.resolve;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * When a signed stream URL stops working. YouTube (and the CDNs built like it) put the
 * deadline in the URL itself: {@code ?expire=1767225600}, or {@code /expire/1767225600/} in
 * manifest paths.
 */
final class StreamExpiry {

    /** Assumed for a URL that does not say: conservative, since a re-resolve costs a second. */
    static final Duration DEFAULT_VALIDITY = Duration.ofHours(1);

    /** Resolve again this long before the deadline, so a stream is not opened just as it dies. */
    static final Duration MARGIN = Duration.ofMinutes(2);

    private static final Pattern QUERY = Pattern.compile("(?:^|&)expire=(\\d{9,12})(?:&|$)");
    private static final Pattern PATH = Pattern.compile("/expire/(\\d{9,12})(?:/|$)");

    private StreamExpiry() {}

    /** The deadline written into {@code url}, if it has one. */
    static Optional<Instant> of(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String query = uri.getRawQuery();
        if (query != null) {
            var matcher = QUERY.matcher(query);
            if (matcher.find()) {
                return Optional.of(Instant.ofEpochSecond(Long.parseLong(matcher.group(1))));
            }
        }
        String path = uri.getRawPath();
        if (path != null) {
            var matcher = PATH.matcher(path);
            if (matcher.find()) {
                return Optional.of(Instant.ofEpochSecond(Long.parseLong(matcher.group(1))));
            }
        }
        return Optional.empty();
    }

    /** When to stop trusting a resolution made {@code now} whose URLs say {@code deadline}. */
    static Instant cacheUntil(Optional<Instant> deadline, Instant now) {
        return deadline.map(d -> d.minus(MARGIN)).orElse(now.plus(DEFAULT_VALIDITY));
    }
}
