package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.api.text.Text;

/**
 * Adds the game's own buttons to the vanilla screen that was just built: the way the mod puts
 * a button inside a screen it does not draw, such as the pause menu.
 */
public interface ButtonSink {

    /** Adds nothing: for screens built where buttons cannot be added. */
    ButtonSink NONE = new ButtonSink() {
        @Override
        public int screenWidth() {
            return 0;
        }

        @Override
        public void add(Text label, int x, int y, int width, int height, Runnable onPress) {}
    };

    /** The screen's width, in GUI pixels. */
    int screenWidth();

    void add(Text label, int x, int y, int width, int height, Runnable onPress);
}
