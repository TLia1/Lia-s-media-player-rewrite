package com.lia.mediaplayer.core.link;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.WebUrls;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The link sources, asked in descending priority; the first match wins. Sources of equal
 * priority keep the order they were given in, so built-ins listed first win ties.
 */
public final class SourceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceRegistry.class);

    private final List<LinkSource> sources;

    public SourceRegistry(List<LinkSource> sources) {
        var sorted = new ArrayList<>(sources);
        sorted.sort(Comparator.comparingInt(LinkSource::priority).reversed());
        this.sources = List.copyOf(sorted);
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
        for (LinkSource source : sources) {
            try {
                Optional<MediaRef> ref = source.match(uri, raw);
                if (ref.isPresent()) {
                    return ref;
                }
            } catch (RuntimeException e) {
                LOGGER.error("Link source {} failed on {}", source.id(), raw, e);
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
        for (LinkSource source : sources) {
            if (source.id().equals(ref.sourceId()) && source instanceof CollectionSource collection) {
                return collection.entryKind();
            }
        }
        return MediaKind.VIDEO;
    }

    public List<LinkSource> sources() {
        return sources;
    }
}
