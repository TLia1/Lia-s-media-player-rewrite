package com.lia.mediaplayer.ui.input;

/**
 * The modifier keys held with a piece of input.
 *
 * @param mac whether the game runs on macOS, where Cmd plays the part Ctrl plays elsewhere
 */
public record Mods(boolean shift, boolean ctrl, boolean alt, boolean meta, boolean mac) {

    public static final Mods NONE = new Mods(false, false, false, false, false);

    /** Ctrl, or Cmd on macOS: the modifier of shortcuts. Callers never think about the platform. */
    public boolean primary() {
        return mac ? meta : ctrl;
    }

    public boolean none() {
        return !shift && !ctrl && !alt && !meta;
    }

    public static Mods shiftOnly() {
        return new Mods(true, false, false, false, false);
    }

    public static Mods ctrlOnly() {
        return new Mods(false, true, false, false, false);
    }
}
