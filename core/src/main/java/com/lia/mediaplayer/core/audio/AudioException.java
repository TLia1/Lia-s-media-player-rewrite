package com.lia.mediaplayer.core.audio;

/** A sound line could not be opened. */
public final class AudioException extends Exception {

    private static final long serialVersionUID = 1L;

    public AudioException(String message) {
        super(message);
    }

    public AudioException(String message, Throwable cause) {
        super(message, cause);
    }
}
