package com.lia.mediaplayer.api.ui;

import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaKind;
import java.util.Optional;

/**
 * The window a {@link WindowAction} is drawn on or clicked in. Client thread only, and only for
 * the moment of the call: do not keep it.
 *
 * @since 4.0.0
 */
public interface MediaWindow {

    /** {@link MediaKind#IMAGE} for a pinned picture, {@link MediaKind#VIDEO} or {@link MediaKind#AUDIO} for a player. */
    MediaKind kind();

    /** The link the window shows now; empty for a player between tracks. */
    Optional<String> url();

    /** The playback of a video window or audio bar; empty for a picture. */
    Optional<MediaHandle> handle();
}
