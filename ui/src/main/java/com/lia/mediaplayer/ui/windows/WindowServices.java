package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.library.Library;
import com.lia.mediaplayer.core.platform.Platform;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the windows reach beyond their playback: the clipboard and browser, the hearts of the
 * history, and the titles of queued links.
 *
 * @param titles the title of a link if it is known, looking it up if it is not
 */
public record WindowServices(Platform platform, Favorites favorites, Function<String, Optional<String>> titles) {

    /** The hearts of the history. */
    public interface Favorites {

        Favorites NONE = new Favorites() {
            @Override
            public boolean isFavorite(String url) {
                return false;
            }

            @Override
            public void toggle(String url, MediaKind kind) {}
        };

        boolean isFavorite(String url);

        void toggle(String url, MediaKind kind);

        static Favorites of(Library library) {
            return new Favorites() {
                @Override
                public boolean isFavorite(String url) {
                    return library.isFavorite(url);
                }

                @Override
                public void toggle(String url, MediaKind kind) {
                    var unused = library.toggleFavorite(url, kind);
                }
            };
        }
    }

    /** The windows' services over the engine. */
    public static WindowServices of(MediaPlayerCore core) {
        return new WindowServices(core.platform(), Favorites.of(core.library()), core.titles()::get);
    }

    /** Only the platform: no hearts, no titles. For tests. */
    public static WindowServices of(Platform platform) {
        return new WindowServices(platform, Favorites.NONE, url -> Optional.empty());
    }
}
