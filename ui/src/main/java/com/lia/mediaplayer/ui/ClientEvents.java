package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.screen.ScreenContext;

/**
 * The moments the game tells the mod about, called by the loader bridges on the client
 * thread. The reverse direction of {@link com.lia.mediaplayer.core.platform.Platform}. The
 * bridges make no decisions: they translate "consumed" into their loader's way of saying it.
 */
public interface ClientEvents {

    /** End of a client tick. */
    void tick();

    /**
     * A screen finished drawing. {@code mouseX, mouseY} is where the cursor is this frame, and
     * {@code mods} the modifiers held now: moves and drags are made from them.
     */
    void renderScreen(ScreenContext screen, Canvas canvas, double mouseX, double mouseY, Mods mods);

    /** The in-world HUD finished drawing; {@code screen} is the screen open above it, if any. */
    void renderHud(ScreenContext screen, Canvas canvas);

    /**
     * Input over an open screen: a press, release, scroll, key or character. A press's click
     * count is ignored; the UI counts clicks itself.
     *
     * @return whether the mod took it, in which case the screen must not see it
     */
    boolean input(ScreenContext screen, InputEvent event);

    /** A screen was opened, or the open one rebuilt (the window was resized). */
    void screenOpened(ScreenContext screen);

    /** A key mapping of the mod was pressed, in the world or over a screen. */
    void hotkeyPressed(Hotkey hotkey);

    /** The player left a world (disconnect, or back to the title screen). */
    void worldLeft();
}
