package com.lia.mediaplayer.api.media;

/** How a queue repeats once its current track ends. */
public enum RepeatMode {
    /** Play the queue once, then stop. */
    OFF,
    /** Start the queue over once its last track has played. */
    ALL,
    /** Replay the current track. */
    ONE;

    /** The next mode in the loop button's cycle: {@code OFF → ALL → ONE → OFF}. */
    public RepeatMode next() {
        return switch (this) {
            case OFF -> ALL;
            case ALL -> ONE;
            case ONE -> OFF;
        };
    }
}
