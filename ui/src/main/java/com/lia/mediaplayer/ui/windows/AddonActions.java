package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.api.ui.ActionIcon;
import com.lia.mediaplayer.api.ui.MediaWindow;
import com.lia.mediaplayer.api.ui.WindowAction;
import com.lia.mediaplayer.core.addon.AddonGuard;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.icon.Icon;
import com.lia.mediaplayer.ui.widget.IconButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The buttons addons put in the corner row of every window, left of the heart: the first
 * {@value #MAX_PER_WINDOW} registered, each shown on the windows it says it applies to. Every
 * call into the addon goes through the {@link AddonGuard}; a button that keeps failing hides
 * for good.
 */
public final class AddonActions {

    public static final int MAX_PER_WINDOW = 3;

    /** No addon buttons: for tests, and a mod with no addons. */
    public static final AddonActions NONE = new AddonActions(List::of, new AddonGuard());

    private final Supplier<List<WindowAction>> actions;
    private final AddonGuard guard;

    /** @param actions read when a window opens */
    public AddonActions(Supplier<List<WindowAction>> actions, AddonGuard guard) {
        this.actions = actions;
        this.guard = guard;
    }

    /** One addon button, and when it shows. */
    record Button(IconButton button, BooleanSupplier shown) {}

    /** The buttons for a window that {@code window} describes. */
    List<Button> buttons(MediaWindow window) {
        List<WindowAction> all = actions.get();
        var buttons = new ArrayList<Button>();
        for (WindowAction action : all.subList(0, Math.min(MAX_PER_WINDOW, all.size()))) {
            String id = action.id();
            Icon icon = icon(guard.call(id, action::icon, ActionIcon.PLUS));
            Supplier<Text> tooltip = () -> guard.call(id, action::tooltip, Text.literal(id));
            var button = new IconButton(icon, mods -> guard.run(id, () -> action.onClick(window, mods.shift())))
                    .tooltip(tooltip);
            // Made once: asked every frame.
            Supplier<Boolean> applies = () -> action.appliesTo(window);
            buttons.add(new Button(button, () -> !guard.isDisabled(id) && guard.call(id, applies, false)));
        }
        return buttons;
    }

    /** A player window, as an addon's button sees it. */
    static MediaWindow player(MediaKind kind, PlaybackControl control) {
        return new MediaWindow() {
            @Override
            public MediaKind kind() {
                return kind;
            }

            @Override
            public Optional<String> url() {
                return control.currentUrl();
            }

            @Override
            public Optional<MediaHandle> handle() {
                return control.handle();
            }
        };
    }

    /** A picture window, as an addon's button sees it. */
    static MediaWindow picture(MediaRef ref) {
        return new MediaWindow() {
            @Override
            public MediaKind kind() {
                return MediaKind.IMAGE;
            }

            @Override
            public Optional<String> url() {
                return Optional.of(ref.url());
            }

            @Override
            public Optional<MediaHandle> handle() {
                return Optional.empty();
            }
        };
    }

    static Icon icon(ActionIcon icon) {
        return switch (icon) {
            case PLAY -> Glyphs::play;
            case NEXT -> Glyphs::next;
            case PREVIOUS -> Glyphs::previous;
            case LOOP -> (c, x, y, color) -> Glyphs.loop(c, x, y, false, color);
            case SHUFFLE -> Glyphs::shuffle;
            case RETRY -> Glyphs::retry;
            case COPY -> Glyphs::copy;
            case EXTERNAL_LINK -> Glyphs::externalLink;
            case HEART -> (c, x, y, color) -> Glyphs.heart(c, x, y, false, color);
            case HIDE -> Glyphs::hide;
            case THEATER -> Glyphs::theater;
            case QUEUE -> Glyphs::queue;
            case PLUS -> Glyphs::plus;
            case UP -> Glyphs::up;
            case DOWN -> Glyphs::down;
            case NOTE -> Glyphs::note;
            case CLOSE -> Glyphs::close;
        };
    }
}
