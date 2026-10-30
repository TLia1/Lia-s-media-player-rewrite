package com.lia.mediaplayer.api.media;

import com.lia.mediaplayer.api.source.WebUrls;

/**
 * What an addon asks the mod to play, and how. The link goes through the same sources, filters
 * and interceptors as a click on it in chat.
 *
 * @param url an {@code http(s)} link
 * @param audioOnly play the sound alone, in the audio bar, as Alt-click does
 * @param newWindow open a window of its own instead of queueing after what the front window
 *     plays, as Shift-click does; the window caps still apply
 * @since 4.0.0
 */
public record MediaRequest(String url, boolean audioOnly, boolean newWindow) {

    public MediaRequest {
        WebUrls.require(url);
    }

    /** {@code url}, played as a plain click would. */
    public static MediaRequest of(String url) {
        return new MediaRequest(url, false, false);
    }

    public MediaRequest withAudioOnly(boolean value) {
        return new MediaRequest(url, value, newWindow);
    }

    public MediaRequest withNewWindow(boolean value) {
        return new MediaRequest(url, audioOnly, value);
    }
}
