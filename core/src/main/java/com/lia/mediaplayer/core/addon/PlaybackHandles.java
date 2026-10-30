package com.lia.mediaplayer.core.addon;

import com.lia.mediaplayer.api.media.MediaHandle;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.playback.Playback;
import com.lia.mediaplayer.core.playback.PlaybackRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link MediaHandle} per playback, made the first time it is asked for and forgotten when
 * the playback closes, so the same playback always has the same handle.
 */
public final class PlaybackHandles {

    private final PlaybackRegistry registry;
    private final SourceRegistry sources;
    private final ClientThread clientThread;
    private final Map<Long, PlaybackHandle> handles = new ConcurrentHashMap<>();

    public PlaybackHandles(PlaybackRegistry registry, SourceRegistry sources, ClientThread clientThread) {
        this.registry = registry;
        this.sources = sources;
        this.clientThread = clientThread;
        registry.addListener(new PlaybackRegistry.Listener() {
            @Override
            public void closed(Playback playback) {
                // After the other listeners of this close, so a Closed event carries the same
                // handle as the events before it.
                clientThread.execute(() -> handles.remove(playback.id()));
            }
        });
    }

    /** The handle of {@code playback}. */
    public MediaHandle of(Playback playback) {
        if (playback.isClosed()) {
            // Not kept: nothing would ever remove it.
            PlaybackHandle known = handles.get(playback.id());
            return known != null ? known : new PlaybackHandle(playback, registry, sources, clientThread);
        }
        return handles.computeIfAbsent(
                playback.id(), id -> new PlaybackHandle(playback, registry, sources, clientThread));
    }

    /** The playback behind a handle this class made. */
    public Optional<Playback> playback(MediaHandle handle) {
        return handle instanceof PlaybackHandle own ? Optional.of(own.playback()) : Optional.empty();
    }

    /** The handles of every open playback, oldest first. Client thread. */
    public List<MediaHandle> all() {
        clientThread.checkCurrent();
        return registry.playbacks().stream().map(this::of).toList();
    }
}
