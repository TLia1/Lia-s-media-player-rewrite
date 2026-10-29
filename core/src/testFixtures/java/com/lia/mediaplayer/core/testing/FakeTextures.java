package com.lia.mediaplayer.core.testing;

import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.platform.TextureService;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Textures that live in memory: each one keeps the last pixels uploaded to it. Records what
 * was created, uploaded and released, and like the real one frees released textures only at
 * {@link #flushReleases()}.
 */
public final class FakeTextures implements TextureService {

    private final Map<Long, byte[]> live = new HashMap<>();
    private final List<TextureRef> created = new ArrayList<>();
    private final List<TextureRef> pendingRelease = new ArrayList<>();
    private final List<TextureRef> released = new ArrayList<>();
    private long nextId = 1;
    private int uploads;

    @Override
    public TextureRef create(String debugName, int width, int height) {
        var ref = new TextureRef(nextId++, width, height);
        live.put(ref.id(), new byte[width * height * 4]);
        created.add(ref);
        return ref;
    }

    @Override
    public boolean upload(TextureRef texture, ByteBuffer rgba) {
        byte[] pixels = live.get(texture.id());
        if (pixels == null) {
            throw new IllegalStateException("Upload to a texture that is not live: " + texture);
        }
        if (rgba.capacity() != pixels.length) {
            return false;
        }
        rgba.duplicate().rewind().get(pixels);
        uploads++;
        return true;
    }

    @Override
    public void release(TextureRef texture) {
        if (live.containsKey(texture.id()) && !pendingRelease.contains(texture)) {
            pendingRelease.add(texture);
        }
    }

    /** Frees what was released: what the real service does between frames. */
    public void flushReleases() {
        for (TextureRef ref : pendingRelease) {
            live.remove(ref.id());
            released.add(ref);
        }
        pendingRelease.clear();
    }

    public boolean isLive(TextureRef ref) {
        return live.containsKey(ref.id());
    }

    /** The pixels last uploaded to a live texture. */
    public byte[] pixels(TextureRef ref) {
        byte[] pixels = live.get(ref.id());
        if (pixels == null) {
            throw new IllegalStateException("Not live: " + ref);
        }
        return pixels.clone();
    }

    public List<TextureRef> created() {
        return List.copyOf(created);
    }

    public List<TextureRef> released() {
        return List.copyOf(released);
    }

    public int liveCount() {
        return live.size();
    }

    public int uploads() {
        return uploads;
    }
}
