package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.library.Library;
import com.lia.mediaplayer.core.link.ShareLinks;
import com.lia.mediaplayer.core.platform.Platform;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * What the windows reach beyond their playback: the clipboard and browser, the hearts of the
 * history, the titles of queued links, links to a moment, and the addons' buttons.
 *
 * @param titles the title of a link if it is known, looking it up if it is not
 * @param linkAt the link that opens a ref at a position, where its source can say
 */
public record WindowServices(
        Platform platform,
        Favorites favorites,
        Function<String, Optional<String>> titles,
        BiFunction<MediaRef, Duration, Optional<String>> linkAt,
        AddonActions actions) {

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

    /** The windows' services over the engine, with the addons' buttons. */
    public static WindowServices of(MediaPlayerCore core, AddonActions actions) {
        return new WindowServices(
                core.platform(), Favorites.of(core.library()), core.titles()::get, core.sources()::linkAt, actions);
    }

    /** Only the platform: no hearts, no titles, no addons. For tests. */
    public static WindowServices of(Platform platform) {
        return new WindowServices(
                platform, Favorites.NONE, url -> Optional.empty(), ShareLinks::atTime, AddonActions.NONE);
    }

    /** These services with {@code newActions} as the addons' buttons. */
    public WindowServices withActions(AddonActions newActions) {
        return new WindowServices(platform, favorites, titles, linkAt, newActions);
    }
}
