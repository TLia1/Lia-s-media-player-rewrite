package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.event.EventSubscriptions;
import com.lia.mediaplayer.api.library.LibraryView;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaRequest;
import com.lia.mediaplayer.api.settings.SettingValues;
import com.lia.mediaplayer.api.source.MediaRef;
import java.util.List;
import java.util.Optional;

/**
 * The running mod, as an addon sees it. Handed to {@link AddonContext#onReady} callbacks; keep
 * it in a field.
 *
 * <p>Queries are safe from any thread. {@link #play} and every control of a {@link MediaHandle}
 * run on the client thread and throw {@link WrongThreadException} elsewhere.
 *
 * @since 4.0.0
 */
public interface MediaPlayer {

    /** The API the installed mod implements; check it with {@link ApiVersion#satisfies}. */
    ApiVersion apiVersion();

    /** Whether the installed mod has {@code capability}. */
    boolean supports(Capability capability);

    /** What a source makes of {@code url}, as a chat message would: empty if no source claims it. */
    Optional<MediaRef> recognize(String url);

    /**
     * Plays a link as a click on it in chat would: in the front window of its kind, queued after
     * what plays there, or in a new window. Interceptors are asked, with {@link
     * com.lia.mediaplayer.api.policy.PlayOrigin#API}.
     *
     * @return the playback it plays or is queued in; empty when no source claims the link, an
     *     interceptor refused it, or it is a picture (pinned in a window, with no playback)
     */
    Optional<MediaHandle> play(MediaRequest request);

    /** Every playback alive now, oldest first. Client thread. */
    List<MediaHandle> handles();

    EventSubscriptions events();

    LibraryView library();

    /** The values of settings, read through the specs the addon registered. */
    SettingValues settings();
}
