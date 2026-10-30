package com.lia.mediaplayer.core.link;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.CollectionSource;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.TimestampLinks;
import com.lia.mediaplayer.api.source.WebUrls;
import com.lia.mediaplayer.core.addon.AddonGuard;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The link sources, asked in descending priority; the first match wins. Sources of equal
 * priority keep the order they were given in, so built-ins listed first win ties — except
 * against an addon's source, which is asked before the built-ins of its priority.
 *
 * <p>Addon sources are called through an {@link AddonGuard}, and the refs they return carry
 * their own id whatever they claimed, so an addon cannot pass its links off as a built-in
 * site's. Matching is safe from any thread; adding belongs to startup.
 */
public final class SourceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceRegistry.class);

    private final List<Entry> builtins;
    private final List<Entry> addons = new ArrayList<>();
    private volatile List<Entry> ordered;

    public SourceRegistry(List<LinkSource> sources) {
        this.builtins = sources.stream().map(source -> new Entry(source, null)).toList();
        this.ordered = sort();
    }

    /** Adds an addon's source, called through {@code guard}. */
    public synchronized void addAddonSource(LinkSource source, AddonGuard guard) {
        addons.add(new Entry(source, guard));
        ordered = sort();
    }

    private List<Entry> sort() {
        var all = new ArrayList<Entry>(addons);
        all.addAll(builtins);
        all.sort(Comparator.comparingInt(Entry::priority).reversed());
        return List.copyOf(all);
    }

    /** The source that claims {@code raw}, if any. Never throws; a source that does is skipped. */
    public Optional<MediaRef> match(String raw) {
        if (!WebUrls.isWebUrl(raw)) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        for (Entry entry : ordered) {
            Optional<MediaRef> ref = entry.match(uri, raw);
            if (ref.isPresent()) {
                return ref;
            }
        }
        return Optional.empty();
    }

    /**
     * What {@code ref} plays as: its own kind, or for a collection the kind of its entries —
     * video when its source does not say.
     */
    public MediaKind playsAs(MediaRef ref) {
        if (ref.kind() != MediaKind.COLLECTION) {
            return ref.kind();
        }
        Entry entry = entry(ref.sourceId());
        if (entry != null && entry.source() instanceof CollectionSource collection) {
            MediaKind kind = entry.call(collection::entryKind, MediaKind.VIDEO);
            return kind == MediaKind.AUDIO ? MediaKind.AUDIO : MediaKind.VIDEO;
        }
        return MediaKind.VIDEO;
    }

    /**
     * The link that opens {@code ref} at {@code position}: from its source if it is a {@link
     * TimestampLinks}, otherwise from the built-in rules of {@link ShareLinks}.
     */
    public Optional<String> linkAt(MediaRef ref, Duration position) {
        Entry entry = entry(ref.sourceId());
        if (entry != null && entry.source() instanceof TimestampLinks links) {
            Optional<String> link = entry.call(() -> links.linkAt(ref, position), Optional.empty());
            return link.filter(WebUrls::isWebUrl);
        }
        return ShareLinks.atTime(ref, position);
    }

    public List<LinkSource> sources() {
        return ordered.stream().map(Entry::source).toList();
    }

    private @Nullable Entry entry(String sourceId) {
        for (Entry entry : ordered) {
            if (entry.source().id().equals(sourceId)) {
                return entry;
            }
        }
        return null;
    }

    /** A source, and the guard it is called through if an addon registered it. */
    private record Entry(LinkSource source, @Nullable AddonGuard guard) {

        int priority() {
            return guard == null ? source.priority() : guard.call(source.id(), source::priority, 0);
        }

        Optional<MediaRef> match(URI uri, String raw) {
            if (guard == null) {
                try {
                    return source.match(uri, raw);
                } catch (RuntimeException e) {
                    LOGGER.error("Link source {} failed on {}", source.id(), raw, e);
                    return Optional.empty();
                }
            }
            return guard.call(source.id(), () -> source.match(uri, raw), Optional.<MediaRef>empty())
                    .map(ref -> ref.sourceId().equals(source.id())
                            ? ref
                            : new MediaRef(ref.url(), ref.kind(), source.id(), ref.label()));
        }

        <T> T call(Supplier<? extends T> action, T fallback) {
            return guard == null ? action.get() : guard.call(source.id(), action, fallback);
        }
    }
}
