package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.core.link.ChatLink;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Mods;
import com.lia.mediaplayer.ui.screen.ScreenContext;
import java.util.List;
import org.jspecify.annotations.Nullable;

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

    /**
     * A screen was opened, or the open one rebuilt (the window was resized). {@code buttons}
     * adds the game's own buttons to it.
     */
    void screenOpened(ScreenContext screen, ButtonSink buttons);

    /** A key mapping of the mod was pressed, in the world or over a screen, with {@code mods} held. */
    void hotkeyPressed(Hotkey hotkey, Mods mods);

    /**
     * A chat message is about to be shown. The platform hands over its text split where its
     * style changes, and replaces the returned spans with labels; an empty answer — almost
     * every message — means the message is shown as it came. Any thread the game shows chat on.
     *
     * @param sender the display name of the player who sent it, or {@code null} for a system
     *     message
     */
    List<ChatLink> chatDisplayed(List<String> runs, @Nullable String sender);

    /** The player left a world (disconnect, or back to the title screen). */
    void worldLeft();
}
