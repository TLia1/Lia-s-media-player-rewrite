package com.lia.mediaplayer.core.link;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.policy.Interceptors;
import com.lia.mediaplayer.core.policy.LinkFilter;
import com.lia.mediaplayer.core.text.Keys;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Decides which links of a chat message become labels, at the moment the message is shown.
 *
 * <p>The platform hands over the message as its styled runs of plain text and gets back the
 * spans to replace; it keeps every style it does not need to change. A link becomes a label
 * when a source claims it, the player's {@link LinkFilter} allows its sender and its host, and
 * no addon's interceptor refuses it.
 * Nothing is fetched here: a link is only read once the player hovers or clicks it.
 *
 * <p>The rewriter remembers the links it labelled, and the UI acts only on those. A click on
 * any other link — one the filter refused, or a server's own clickable text — is left to the
 * game, so the filter cannot be bypassed by a link that happens to be clickable anyway.
 */
public final class ChatRewriter {

    /** Chat keeps 100 messages; a few links each, with room to spare. */
    private static final int REMEMBERED = 1024;

    private static final int GOLD = 0xFFAA00;
    private static final int AQUA = 0x55FFFF;
    private static final int GREEN = 0x55FF55;

    private final SourceRegistry sources;
    private final Interceptors interceptors;
    private volatile LinkFilter filter = LinkFilter.NONE;
    private final Set<String> labelled = Collections.newSetFromMap(Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > REMEMBERED;
        }
    }));

    public ChatRewriter(SourceRegistry sources) {
        this(sources, new Interceptors());
    }

    public ChatRewriter(SourceRegistry sources, Interceptors interceptors) {
        this.sources = sources;
        this.interceptors = interceptors;
    }

    /** Replaces the filter; messages shown from now on follow it. */
    public void setFilter(LinkFilter newFilter) {
        filter = newFilter;
    }

    public LinkFilter filter() {
        return filter;
    }

    /**
     * The links of a message to show as labels, in order. Empty — the common case — means the
     * message is shown as it came.
     *
     * @param runs the message's text, split where its style changes
     * @param sender the display name of the player who sent it, or {@code null} for a system
     *     message
     */
    public List<ChatLink> rewrite(List<String> runs, @Nullable String sender) {
        LinkFilter current = filter;
        if (!current.allowsSender(sender)) {
            return List.of();
        }
        List<ChatLink> links = List.of();
        for (int run = 0; run < runs.size(); run++) {
            for (LinkScanner.Span span : LinkScanner.scan(runs.get(run))) {
                if (!current.allowsUrl(span.url())) {
                    continue;
                }
                Optional<MediaRef> ref = sources.match(span.url());
                if (ref.isEmpty() || !interceptors.allowChatLink(ref.get(), sender)) {
                    continue;
                }
                if (links.isEmpty()) {
                    links = new ArrayList<>();
                }
                links.add(link(run, span, ref.get()));
                labelled.add(span.url());
            }
        }
        return links;
    }

    /** Whether {@code url} was shown as a label: the only links a click or hover acts on. */
    public boolean isLabelled(String url) {
        return labelled.contains(url);
    }

    private ChatLink link(int run, LinkScanner.Span span, MediaRef ref) {
        MediaKind playsAs = sources.playsAs(ref);
        int rgb = switch (playsAs) {
            case IMAGE -> GOLD;
            case AUDIO -> GREEN;
            case VIDEO, COLLECTION -> AQUA;
        };
        // A picture's hover is its preview; the others say what the modifiers do.
        Optional<Text> hint = switch (ref.kind()) {
            case IMAGE -> Optional.empty();
            case AUDIO -> Optional.of(Text.translatable(Keys.CHAT_HINT_AUDIO));
            case VIDEO -> Optional.of(Text.translatable(Keys.CHAT_HINT_VIDEO));
            case COLLECTION ->
                Optional.of(Text.translatable(
                        playsAs == MediaKind.AUDIO ? Keys.CHAT_HINT_AUDIO_COLLECTION : Keys.CHAT_HINT_COLLECTION));
        };
        return new ChatLink(run, span.start(), span.end(), ref, ref.label(), hint, rgb, ref.kind() != MediaKind.IMAGE);
    }
}
