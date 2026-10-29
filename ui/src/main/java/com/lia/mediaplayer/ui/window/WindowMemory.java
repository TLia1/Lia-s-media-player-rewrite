package com.lia.mediaplayer.ui.window;

import com.lia.mediaplayer.ui.canvas.Rect;
import java.util.Optional;

/** Where the player last left a window of each kind, so the next one opens there. */
public interface WindowMemory {

    /** Remembers nothing: every window opens in the default corner. */
    WindowMemory NONE = new WindowMemory() {
        @Override
        public Optional<Rect> frame(String kind) {
            return Optional.empty();
        }

        @Override
        public void remember(String kind, Rect frame) {}
    };

    /**
     * Where the last window of {@code kind} was left. A width or height of 0 means it was never
     * resized that way, and the new window keeps its own.
     */
    Optional<Rect> frame(String kind);

    /** A window of {@code kind} was moved or resized to {@code frame} by the player. */
    void remember(String kind, Rect frame);
}
