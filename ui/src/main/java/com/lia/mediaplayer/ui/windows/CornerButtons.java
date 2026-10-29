package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.link.ShareLinks;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.widget.IconButton;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The buttons in a window's title strip that every kind shares: the heart, copy the link
 * (Shift: at the current time, where the site can say so), open it in the browser, hide, and
 * close.
 */
final class CornerButtons {

    private CornerButtons() {}

    /** The heart: keeps the link in the history for good. */
    static IconButton favorite(Supplier<Optional<MediaRef>> current, MediaKind kind, WindowServices services) {
        return new IconButton(
                        (c, x, y, color) -> Glyphs.heart(c, x, y, isFavorite(current, services), color),
                        () -> current.get()
                                .ifPresent(ref -> services.favorites().toggle(ref.url(), kind)))
                .activeWhen(() -> isFavorite(current, services))
                .tooltip(() -> Text.translatable(isFavorite(current, services) ? Keys.UNFAVORITE : Keys.FAVORITE));
    }

    private static boolean isFavorite(Supplier<Optional<MediaRef>> current, WindowServices services) {
        return current.get()
                .map(ref -> services.favorites().isFavorite(ref.url()))
                .orElse(false);
    }

    /** Copies the link; with Shift, the link that opens at {@code position}. */
    static IconButton copy(Supplier<Optional<MediaRef>> current, Supplier<Duration> position, WindowServices services) {
        return new IconButton(
                        Glyphs::copy,
                        (Mods mods) -> current.get().ifPresent(ref -> {
                            String link = mods.shift()
                                    ? ShareLinks.atTime(ref, position.get()).orElse(ref.url())
                                    : ref.url();
                            services.platform().clipboard().set(link);
                        }))
                .tooltip(Text.translatable(Keys.COPY_LINK));
    }

    static IconButton browser(Supplier<Optional<MediaRef>> current, WindowServices services) {
        return new IconButton(
                        Glyphs::externalLink,
                        () -> current.get().ifPresent(ref -> services.platform().openInBrowser(ref.url())))
                .tooltip(Text.translatable(Keys.OPEN_IN_BROWSER));
    }

    static IconButton hide(Runnable hide) {
        return new IconButton(Glyphs::hide, hide).tooltip(Text.translatable(Keys.HIDE));
    }

    static IconButton close(Runnable close) {
        return new IconButton(Glyphs::close, close).dangerous().tooltip(Text.translatable(Keys.CLOSE));
    }
}
