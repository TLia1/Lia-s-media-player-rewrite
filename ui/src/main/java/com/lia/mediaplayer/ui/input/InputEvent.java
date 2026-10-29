package com.lia.mediaplayer.ui.input;

/**
 * One piece of input, in GUI pixels. Only presses, releases, scrolls, keys and characters
 * cross the platform seam; moves, drags and click counts are made by {@link PointerTracker}
 * from the cursor position, so they behave the same on every loader and version.
 */
public sealed interface InputEvent {

    /** A button went down. {@code clicks} is 2 for the second press of a double click. */
    record PointerDown(double x, double y, int button, Mods mods, int clicks) implements InputEvent {}

    record PointerUp(double x, double y, int button, Mods mods) implements InputEvent {}

    /** The cursor moved with no button held. */
    record PointerMove(double x, double y) implements InputEvent {}

    /**
     * The cursor moved with {@code button} held; {@code dx, dy} since the last pointer event,
     * {@code mods} as held now (Shift pressed mid-drag frees a window from snapping).
     */
    record PointerDrag(double x, double y, int button, double dx, double dy, Mods mods) implements InputEvent {}

    /** Wheel movement: positive {@code delta} is away from the user. */
    record Scroll(double x, double y, double delta, Mods mods) implements InputEvent {}

    record KeyDown(Key key, Mods mods) implements InputEvent {}

    record CharTyped(int codePoint) implements InputEvent {}

    /** The left button, the one that clicks. */
    int PRIMARY_BUTTON = 0;
}
