package com.lia.mediaplayer.core.image;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.platform.TextureService;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The pictures of the world session, shared by the chat's hover preview and the image windows:
 * one download, one decode and one set of textures per link, however many views show it.
 * Client thread only.
 *
 * <p>Kept by bytes of texture, least recently shown first out; views ask every frame, which is
 * what keeps a shown picture in. Decoded frames reach the GPU through {@link #tick}, a bounded
 * number of bytes a tick, so a 200-frame GIF arriving costs a few ticks, not one long hitch.
 */
public final class ImageCache implements AutoCloseable {

    /** Default limit on the textures kept. */
    public static final long DEFAULT_MAX_BYTES = 256L * 1024 * 1024;

    /** Texture bytes uploaded per tick: a 1080p still, about, or a dozen GIF frames. */
    static final long UPLOAD_BUDGET = 16L * 1024 * 1024;

    private static final int MAX_ENTRIES = 128;

    private final TextureService textures;
    private final AsyncCache<MediaRef, ImageTextures> cache;

    public ImageCache(ImageLoader loader, TextureService textures, ClientThread clientThread, long maxBytes) {
        this(ref -> loader.load(ref).thenApply(ImageTextures::new), textures, clientThread, maxBytes);
    }

    ImageCache(
            Function<MediaRef, CompletableFuture<ImageTextures>> loader,
            TextureService textures,
            ClientThread clientThread,
            long maxBytes) {
        this.textures = textures;
        this.cache = new AsyncCache<>(
                "images",
                clientThread,
                loader,
                ImageTextures::bytes,
                image -> image.release(textures),
                maxBytes,
                MAX_ENTRIES);
    }

    /**
     * The picture of {@code ref}, loading it on first ask. {@link AsyncCache.Entry.Ready} only
     * once every frame is on the GPU; until then it is still loading.
     */
    public AsyncCache.Entry<ImageTextures> get(MediaRef ref) {
        AsyncCache.Entry<ImageTextures> entry = cache.get(ref);
        if (entry instanceof AsyncCache.Entry.Ready<ImageTextures> ready
                && !ready.value().isComplete()) {
            return new AsyncCache.Entry.Loading<>();
        }
        return entry;
    }

    /** Forgets a failure (or a picture), so the next {@link #get} fetches it again. */
    public void retry(MediaRef ref) {
        cache.invalidate(ref);
    }

    /** Uploads waiting frames, up to the budget. Once a client tick. */
    public void tick() {
        long[] budget = {UPLOAD_BUDGET};
        cache.forEachReady(image -> {
            if (budget[0] > 0 && !image.isComplete()) {
                budget[0] -= image.upload(textures, budget[0]);
            }
        });
    }

    public void setMaxBytes(long maxBytes) {
        cache.setMaxWeight(maxBytes);
    }

    /** Texture bytes held. */
    public long bytes() {
        return cache.weight();
    }

    /** Drops every picture: the world was left. */
    public void clear() {
        cache.clear();
    }

    @Override
    public void close() {
        clear();
    }
}
