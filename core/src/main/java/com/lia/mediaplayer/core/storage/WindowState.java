package com.lia.mediaplayer.core.storage;

import com.lia.mediaplayer.api.media.RepeatMode;

/**
 * How the player left the windows of one kind: where, how big, how loud, and the modes of their
 * queue.
 * Kept per kind, not per link: "my video window lives bottom left at this size" is about how
 * someone arranges their screen, not about the clip that happened to play.
 *
 * @param placed whether {@code x, y} were set by the player; otherwise the default corner applies
 * @param width the window's width, 0 when never resized
 * @param height the window's height, 0 when never resized, or when only the width means anything
 * @param queueOpen whether the queue panel was showing
 * @param volume the player's own volume, linear, 0 to 1, before the game's master volume
 */
public record WindowState(
        boolean placed,
        int x,
        int y,
        int width,
        int height,
        RepeatMode repeat,
        boolean shuffle,
        boolean queueOpen,
        float volume) {

    public static final WindowState DEFAULT = new WindowState(false, 0, 0, 0, 0, RepeatMode.OFF, false, false, 1);

    public WindowState {
        width = Math.max(0, width);
        height = Math.max(0, height);
        volume = Float.isNaN(volume) ? 1 : Math.max(0, Math.min(1, volume));
    }

    public boolean sized() {
        return width > 0;
    }

    public WindowState withFrame(int newX, int newY, int newWidth, int newHeight) {
        return new WindowState(true, newX, newY, newWidth, newHeight, repeat, shuffle, queueOpen, volume);
    }

    public WindowState withModes(RepeatMode newRepeat, boolean newShuffle) {
        return new WindowState(placed, x, y, width, height, newRepeat, newShuffle, queueOpen, volume);
    }

    public WindowState withQueueOpen(boolean open) {
        return new WindowState(placed, x, y, width, height, repeat, shuffle, open, volume);
    }

    public WindowState withVolume(float newVolume) {
        return new WindowState(placed, x, y, width, height, repeat, shuffle, queueOpen, newVolume);
    }
}
