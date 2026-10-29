package com.lia.mediaplayer.core.link;

import com.lia.mediaplayer.api.source.MediaRef;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * The link to send someone so that it opens where the player is in it — {@code &t=137s}
 * rather than "it's somewhere around four minutes". Purely textual. Each site spells the
 * position its own way; a site with no spelling for it gets nothing, because a stray {@code
 * t=} that does nothing looks like a bug.
 *
 * <p>Decided by the source that claimed the link, never by asking "is this YouTube?" of the
 * string again.
 */
public final class ShareLinks {

    private ShareLinks() {}

    /** {@code ref}'s link at {@code position}, if its site can say where to start. */
    public static Optional<String> atTime(MediaRef ref, Duration position) {
        long seconds = position.toSeconds();
        String url = ref.url();
        if (seconds <= 0) {
            return Optional.empty();
        }
        String source = ref.sourceId();
        if (source.equals(BuiltinSources.YOUTUBE)) {
            // /embed/ is the one YouTube form that ignores t= and wants start=.
            return Optional.of(
                    url.contains("/embed/")
                            ? withQueryParam(url, "start", Long.toString(seconds))
                            : withQueryParam(url, "t", seconds + "s"));
        }
        if (source.equals(BuiltinSources.TWITCH)) {
            return Optional.of(withQueryParam(url, "t", twitchTime(seconds)));
        }
        if (source.equals(BuiltinSources.VIMEO)) {
            return Optional.of(withFragment(url, "t=" + seconds + "s"));
        }
        if (source.equals(BuiltinSources.SOUNDCLOUD)) {
            // SoundCloud reads the fragment as a clock reading, not a count of seconds.
            return Optional.of(withFragment(url, "t=" + clockTime(seconds)));
        }
        return Optional.empty();
    }

    /** Twitch's spelling of a duration: {@code 1h02m03s}, leading zero units dropped. */
    static String twitchTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh%02dm%02ds", hours, minutes, secs);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm%02ds", minutes, secs);
        }
        return secs + "s";
    }

    /** {@code m:ss}, or {@code h:mm:ss} from an hour on. */
    static String clockTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    /**
     * {@code url} with {@code key=value} in its query, replacing a parameter of that name. By
     * hand rather than through {@code URI}, which would re-encode the path: the link goes onto
     * someone's clipboard and should come out as they were given it, plus one parameter.
     */
    static String withQueryParam(String url, String key, String value) {
        String fragment = "";
        String rest = url;
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            fragment = rest.substring(hash);
            rest = rest.substring(0, hash);
        }
        String base = rest;
        String query = "";
        int question = rest.indexOf('?');
        if (question >= 0) {
            base = rest.substring(0, question);
            query = rest.substring(question + 1);
        }
        var kept = new StringBuilder();
        for (String param : query.split("&", -1)) {
            if (param.isEmpty() || param.equals(key) || param.startsWith(key + "=")) {
                continue;
            }
            kept.append(param).append('&');
        }
        kept.append(key).append('=').append(value);
        return base + "?" + kept + fragment;
    }

    static String withFragment(String url, String fragment) {
        int hash = url.indexOf('#');
        return (hash >= 0 ? url.substring(0, hash) : url) + "#" + fragment;
    }
}
