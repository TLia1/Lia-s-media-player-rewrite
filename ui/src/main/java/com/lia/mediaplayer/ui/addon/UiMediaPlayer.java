package com.lia.mediaplayer.ui.addon;

import com.lia.mediaplayer.api.ApiVersion;
import com.lia.mediaplayer.api.Capability;
import com.lia.mediaplayer.api.MediaPlayer;
import com.lia.mediaplayer.api.event.EventSubscriptions;
import com.lia.mediaplayer.api.library.LibraryView;
import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.api.media.MediaRequest;
import com.lia.mediaplayer.api.settings.SettingValues;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.MediaPlayerCore;
import java.util.List;
import java.util.Optional;

/**
 * The running mod as one addon sees it. Each addon has its own, so the listeners it subscribes
 * through {@link #events()} count against it in the guard, not against another addon.
 */
public final class UiMediaPlayer implements MediaPlayer {

    /** Plays a request the way the UI plays a link; the handle of the playback it went to. */
    @FunctionalInterface
    public interface Player {
        Optional<MediaHandle> play(MediaRef ref, MediaRequest request);
    }

    private final MediaPlayerCore core;
    private final Player player;
    private final EventSubscriptions events;

    public UiMediaPlayer(MediaPlayerCore core, Player player, String addonId) {
        this.core = core;
        this.player = player;
        this.events = core.mediaEvents().subscriptions(core.addonGuard(), addonId + ":events");
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public boolean supports(Capability capability) {
        return switch (capability) {
            case LINK_SOURCES,
                    RESOLVERS,
                    THEMES,
                    WINDOW_ACTIONS,
                    SHORTCUTS,
                    KEY_BINDINGS,
                    SETTINGS,
                    INTERCEPTORS,
                    PLAYBACK_EVENTS,
                    HANDLES,
                    LIBRARY -> true;
            case IMAGE_DECODERS, SCREEN_ENTRIES, SURFACES, HEADLESS_AUDIO, POSITIONAL_AUDIO, SYNC, DIAGNOSTICS -> false;
        };
    }

    @Override
    public Optional<MediaRef> recognize(String url) {
        return core.sources().match(url.strip());
    }

    @Override
    public Optional<MediaHandle> play(MediaRequest request) {
        if (!core.platform().clientThread().isCurrent()) {
            throw new WrongThreadException("play is called on the client thread, not "
                    + Thread.currentThread().getName());
        }
        return recognize(request.url()).flatMap(ref -> player.play(ref, request));
    }

    @Override
    public List<MediaHandle> handles() {
        return core.handles().all();
    }

    @Override
    public EventSubscriptions events() {
        return events;
    }

    @Override
    public LibraryView library() {
        return core.libraryReader();
    }

    @Override
    public SettingValues settings() {
        return core.settingValues();
    }
}
