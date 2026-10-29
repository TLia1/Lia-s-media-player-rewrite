package com.lia.mediaplayer.ui.screen;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.theme.Theme;
import com.lia.mediaplayer.ui.widget.Column;
import com.lia.mediaplayer.ui.widget.Label;
import com.lia.mediaplayer.ui.widget.Spacer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The media windows, live, without the chat: the world stays visible and unpaused behind
 * them, and a line at the bottom says how to leave.
 */
public final class MediaControlsScreen extends UiScreen {

    private final BooleanSupplier noWindows;
    private final Label empty;

    public MediaControlsScreen(Supplier<Theme> theme, LongSupplier nanoTime, BooleanSupplier noWindows) {
        this(theme, nanoTime, noWindows, new Column());
    }

    private MediaControlsScreen(
            Supplier<Theme> theme, LongSupplier nanoTime, BooleanSupplier noWindows, Column column) {
        super(Text.translatable(Keys.CONTROLS_TITLE), theme, nanoTime, column);
        this.noWindows = noWindows;
        column.padding(6, 0);
        column.add(new Spacer(), 1);
        empty = column.add(
                new Label(Text.translatable(Keys.CONTROLS_EMPTY), Role.TEXT)
                        .align(Label.Align.CENTER)
                        .shadow(true),
                0,
                true);
        column.add(new Spacer(0, 4));
        column.add(
                new Label(Text.translatable(Keys.CONTROLS_HINT), Role.TEXT_SUBTLE)
                        .align(Label.Align.CENTER)
                        .shadow(true),
                0,
                true);
    }

    @Override
    protected void beforeRender() {
        empty.setVisible(noWindows.getAsBoolean());
    }

    @Override
    public boolean showsWindows() {
        return true;
    }

    @Override
    public boolean drawsBackground() {
        return false;
    }
}
