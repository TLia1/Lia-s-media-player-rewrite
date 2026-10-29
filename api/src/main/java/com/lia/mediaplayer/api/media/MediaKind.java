package com.lia.mediaplayer.api.media;

/** What a recognised link points at. A link is claimed by exactly one kind. */
public enum MediaKind {
    /** A still or animated picture. */
    IMAGE,
    /** Moving pictures, usually with sound. */
    VIDEO,
    /** Sound only. */
    AUDIO,
    /** A playlist, set or album that expands into a queue of other links. */
    COLLECTION
}
