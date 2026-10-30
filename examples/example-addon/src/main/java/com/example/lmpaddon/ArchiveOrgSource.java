package com.example.lmpaddon;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.LinkSource;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Internet Archive item pages, {@code https://archive.org/details/<item>}. No resolver is
 * needed: the mod hands any video or audio link no built-in resolver takes to yt-dlp, which
 * reads these pages.
 *
 * <p>{@link #match} is called for every link of every chat message, so it is string work only.
 */
final class ArchiveOrgSource implements LinkSource {

    @Override
    public String id() {
        return ExampleAddon.ID + ":archive_org";
    }

    @Override
    public Optional<MediaRef> match(URI uri, String raw) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean item = (host.equals("archive.org") || host.equals("www.archive.org"))
                && path.startsWith("/details/")
                && path.length() > "/details/".length();
        if (!item) {
            return Optional.empty();
        }
        return Optional.of(new MediaRef(raw, MediaKind.VIDEO, id(), Text.literal("[archive]")));
    }
}
