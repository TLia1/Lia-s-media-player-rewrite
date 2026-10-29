package com.lia.mediaplayer.ui;

import com.lia.mediaplayer.ui.screen.UiScreen;
import java.util.Optional;

/**
 * What the UI needs from the game that the engine does not: showing its own screens, and
 * reading the chat. The platform implements it with its one vanilla host screen.
 */
public interface UiHost {

    /** Opens {@code screen} in place of whatever screen is open. */
    void openScreen(UiScreen screen);

    /** Closes the screen that is open, if it is one of the mod's. */
    void closeScreen();

    /** The mod's screen that is open, if the open screen is one. */
    Optional<UiScreen> currentScreen();

    /**
     * The link of the chat text under {@code (x, y)}, in GUI pixels: the address its click
     * opens. Asked every frame the chat is open, so the platform keeps the answer while neither
     * the cursor nor the chat moves.
     */
    Optional<String> chatLinkAt(double x, double y);
}
