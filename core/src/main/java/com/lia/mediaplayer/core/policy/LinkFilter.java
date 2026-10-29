package com.lia.mediaplayer.core.policy;

import com.lia.mediaplayer.api.source.WebUrls;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The player's own policy on <em>whose</em> chat links become labels and <em>which</em> ones.
 *
 * <p>Purely local: a filtered link stays in the message as the text it was, it is only not
 * turned into something the mod plays. Nothing is hidden and no message is cancelled — that
 * would be a chat filter, which is not this mod's business. The filter governs what other
 * people put in front of the player, so it applies to chat only; a link the player types into
 * {@code /show} is never filtered.
 *
 * @param mode how the two host lists apply
 * @param blockedHosts hosts never offered under {@link Mode#BLOCKLIST}
 * @param allowedHosts the only hosts offered under {@link Mode#ALLOWLIST}
 * @param blockedSenders players whose messages are never scanned for links
 */
public record LinkFilter(Mode mode, List<String> blockedHosts, List<String> allowedHosts, List<String> blockedSenders) {

    /** Everything from everyone: the default. */
    public static final LinkFilter NONE = new LinkFilter(Mode.OFF, List.of(), List.of(), List.of());

    public enum Mode {
        /** No host filtering. */
        OFF,
        /** Everything except the blocked hosts. */
        BLOCKLIST,
        /** Nothing except the allowed hosts. */
        ALLOWLIST
    }

    public LinkFilter {
        blockedHosts = normalise(blockedHosts);
        allowedHosts = normalise(allowedHosts);
        blockedSenders = normalise(blockedSenders);
    }

    /**
     * Whether messages from {@code sender} are scanned for links at all. A message with no
     * sender — a system message — always is: there is nobody to have blocked.
     */
    public boolean allowsSender(@Nullable String sender) {
        return sender == null || !nameMatches(sender, blockedSenders);
    }

    /** Whether a link to {@code url}'s host may become a label. */
    public boolean allowsUrl(String url) {
        return switch (mode) {
            case OFF -> true;
            case BLOCKLIST -> !hostMatches(url, blockedHosts);
            // An empty allow-list really does mean nothing, which is the honest reading of the
            // mode and visible the moment it is switched on.
            case ALLOWLIST -> hostMatches(url, allowedHosts);
        };
    }

    /**
     * Whether {@code url}'s host is one of {@code hosts} or a sub-domain of one, so that one
     * entry covers a site and its CDNs: {@code discordapp.com} matches {@code
     * cdn.discordapp.com}. Whole labels only — {@code evil-discordapp.com} does not match — and
     * a leading {@code www.} is ignored on both sides.
     */
    static boolean hostMatches(String url, List<String> hosts) {
        if (hosts.isEmpty() || !WebUrls.isWebUrl(url)) {
            return false;
        }
        String host;
        try {
            host = new URI(url).getHost();
        } catch (URISyntaxException e) {
            return false;
        }
        if (host == null) {
            return false;
        }
        host = stripWww(host.toLowerCase(Locale.ROOT));
        for (String entry : hosts) {
            String listed = stripWww(entry);
            if (!listed.isEmpty() && (host.equals(listed) || host.endsWith("." + listed))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code name} contains one of {@code names}. Containment, because a message
     * arrives with the sender's display name: on a server that prefixes ranks, "Steve" arrives
     * as "[VIP] Steve". A short entry can catch a longer name, which is the right way round for
     * a filter someone reached for on purpose.
     */
    static boolean nameMatches(String name, List<String> names) {
        String needle = name.toLowerCase(Locale.ROOT);
        for (String entry : names) {
            if (!entry.isEmpty() && needle.contains(entry)) {
                return true;
            }
        }
        return false;
    }

    private static String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static List<String> normalise(List<String> entries) {
        return entries.stream()
                .map(entry -> entry.strip().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .toList();
    }
}
