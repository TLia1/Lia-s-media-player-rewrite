package com.lia.mediaplayer.core.settings;

/** What a hidden video window's player does with its picture. */
public enum WhenHidden {
    /** Keeps decoding, as v2 did: showing the window again is instant. */
    KEEP_VIDEO,
    /** After a few seconds hidden, plays the sound alone; showing the window brings the picture back. */
    SOUND_ONLY
}
