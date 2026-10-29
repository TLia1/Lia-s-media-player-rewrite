package com.lia.mediaplayer.core.diag;

import com.lia.mediaplayer.core.ModInfo;
import java.util.List;
import java.util.Locale;

/**
 * Why a playback failed, read from the raw diagnostic it left behind — yt-dlp's or ffmpeg's
 * stderr, or one of the engine's own messages — in terms a player can act on.
 *
 * <p>Matching is by substring because none of these tools have stable error codes: the
 * wording is the only thing there is, and it is stable enough that yt-dlp's own issue
 * templates quote it. The raw text is kept as {@link #detail()}, because it is what a bug
 * report needs.
 *
 * @param detail the raw text, trimmed
 */
public record PlaybackError(Cause cause, String detail) {

    public PlaybackError {
        detail = detail.strip();
    }

    /** What went wrong. {@code suggestsToolUpdate} marks the causes a newer yt-dlp or ffmpeg would fix. */
    public enum Cause {
        TOOL_MISSING("tool_missing", true),
        EXTRACTOR_OUTDATED("extractor_outdated", true),
        AGE_RESTRICTED("age_restricted", false),
        SIGN_IN_REQUIRED("sign_in", false),
        PRIVATE("private", false),
        GEO_BLOCKED("geo_blocked", false),
        LIVE_NOT_STARTED("live_not_started", false),
        UNAVAILABLE("unavailable", false),
        TIMEOUT("timeout", false),
        NOT_FOUND("not_found", false),
        LINK_EXPIRED("link_expired", false),
        NETWORK("network", false),
        UNSUPPORTED_FORMAT("unsupported_format", false),
        DECODE_FAILED("decode_failed", false),
        NO_AUDIO_DEVICE("no_audio_device", false),
        UNKNOWN("unknown", false);

        private final String id;
        private final boolean suggestsToolUpdate;

        Cause(String id, boolean suggestsToolUpdate) {
            this.id = id;
            this.suggestsToolUpdate = suggestsToolUpdate;
        }

        /** The stable id, e.g. {@code "geo_blocked"}; addons match on it rather than on wording. */
        public String id() {
            return id;
        }

        public String messageKey() {
            return "error." + ModInfo.MOD_ID + ".cause." + id;
        }

        public String hintKey() {
            return messageKey() + ".hint";
        }

        public boolean suggestsToolUpdate() {
            return suggestsToolUpdate;
        }
    }

    /** A cause and the lowercase fragments that identify it. */
    private record Rule(Cause cause, List<String> needles) {
        Rule(Cause cause, String... needles) {
            this(cause, List.of(needles));
        }
    }

    /**
     * Checked in order, so the narrower reading of an overlapping message wins: "blocked it in
     * your country" is also a "Video unavailable", and "Sign in to confirm your age" is also a
     * "Sign in to confirm".
     */
    private static final List<Rule> RULES = List.of(
            new Rule(
                    Cause.TOOL_MISSING,
                    "is required to play",
                    "failed to run yt-dlp",
                    "cannot run program",
                    "error=2, no such file"),
            new Rule(
                    Cause.EXTRACTOR_OUTDATED,
                    "unable to extract",
                    "nsig extraction failed",
                    "signature extraction failed",
                    "please report this issue",
                    "update to the latest version",
                    "yt-dlp is out of date",
                    "unable to download api page",
                    "failed to extract any player response",
                    "unsupported client",
                    "unable to recognize playlist"),
            new Rule(
                    Cause.AGE_RESTRICTED,
                    "confirm your age",
                    "age-restricted",
                    "age restricted",
                    "inappropriate for some users"),
            new Rule(
                    Cause.SIGN_IN_REQUIRED,
                    "sign in to confirm",
                    "not a bot",
                    "login required",
                    "requires authentication",
                    "members-only",
                    "join this channel",
                    "use --cookies"),
            new Rule(Cause.PRIVATE, "private video", "video is private", "this playlist is private"),
            new Rule(
                    Cause.GEO_BLOCKED,
                    "in your country",
                    "geo restrict",
                    "geo-restricted",
                    "not available in your",
                    "not available from your location",
                    "blocked it in your"),
            new Rule(
                    Cause.LIVE_NOT_STARTED,
                    "live event will begin",
                    "premieres in",
                    "this live event",
                    "not currently live"),
            // A format-selector miss, not a missing video, but it contains UNAVAILABLE's
            // "is not available". It must stay above UNAVAILABLE; the other UNSUPPORTED_FORMAT
            // needles are broader and must stay below it.
            new Rule(Cause.UNSUPPORTED_FORMAT, "requested format is not available"),
            new Rule(
                    Cause.UNAVAILABLE,
                    "video unavailable",
                    "video is unavailable",
                    "is not available",
                    "has been removed",
                    "removed by the uploader",
                    "has been terminated",
                    "no longer available",
                    "isn't available",
                    "found no playable media"),
            new Rule(Cause.NO_AUDIO_DEVICE, "no audio device", "no line matching", "line unavailable"),
            new Rule(Cause.TIMEOUT, "timed out", "timeout"),
            new Rule(Cause.NOT_FOUND, "404", "not found"),
            new Rule(Cause.LINK_EXPIRED, "403", "forbidden"),
            new Rule(
                    Cause.NETWORK,
                    "connection refused",
                    "connection reset",
                    "network is unreachable",
                    "no route to host",
                    "failed to resolve",
                    "getaddrinfo",
                    "unknownhostexception",
                    "name resolution",
                    "ssl",
                    "tls handshake",
                    "unable to download webpage"),
            new Rule(
                    Cause.UNSUPPORTED_FORMAT,
                    "invalid data found",
                    "protocol not found",
                    "unknown protocol",
                    "decoder not found",
                    "does not contain any stream",
                    "no streams",
                    "matches no streams"),
            new Rule(
                    Cause.DECODE_FAILED,
                    "ffprobe could not read",
                    "could not parse ffprobe",
                    "ffmpeg exited",
                    "failed to read frame"));

    /**
     * Reads {@code raw} and says what it means. Never throws: an unrecognised message is
     * {@link Cause#UNKNOWN}, which still gets a readable line and a retry.
     */
    public static PlaybackError classify(String raw) {
        String text = raw.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            for (String needle : rule.needles()) {
                if (text.contains(needle)) {
                    return new PlaybackError(rule.cause(), raw);
                }
            }
        }
        return new PlaybackError(Cause.UNKNOWN, raw);
    }

    /** Classifies a failure, using the messages of the whole cause chain. */
    public static PlaybackError of(Throwable failure) {
        var text = new StringBuilder();
        Throwable t = failure;
        for (int depth = 0; t != null && depth < 8; depth++, t = t.getCause()) {
            String message = t.getMessage();
            if (message != null) {
                text.append(message).append('\n');
            }
        }
        return classify(text.isEmpty() ? failure.getClass().getSimpleName() : text.toString());
    }

    /** One line for logs and the CLI harness. */
    public String shortText() {
        String firstLine = detail.lines().findFirst().orElse("");
        return cause.id() + (firstLine.isEmpty() ? "" : ": " + firstLine);
    }
}
