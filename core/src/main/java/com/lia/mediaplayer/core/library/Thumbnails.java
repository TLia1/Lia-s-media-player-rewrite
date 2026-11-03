package com.lia.mediaplayer.core.library;

import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.cache.DiskCache;
import com.lia.mediaplayer.core.image.DecodedImage;
import com.lia.mediaplayer.core.image.ImageDecoders;
import com.lia.mediaplayer.core.image.ImageTextures;
import com.lia.mediaplayer.core.lifecycle.ClientThread;
import com.lia.mediaplayer.core.net.Http;
import com.lia.mediaplayer.core.platform.TextureService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The small pictures beside the rows of a list. A thumbnail is fetched once, shrunk to at most
 * {@value #WIDTH}×{@value #HEIGHT}, and kept as a JPEG in the {@link DiskCache}; after that it
 * costs a file read, on any later visit, in any game session. Where the picture is comes from
 * the {@link LinkInfoStore}, so a link already seen is not resolved again either. Only rows on
 * screen ask. Client thread only.
 */
public final class Thumbnails implements AutoCloseable {

    /** The largest thumbnail kept: four times the box a list shows it in, for large GUI scales. */
    public static final int WIDTH = 192;

    public static final int HEIGHT = 108;

    /** Texture memory the thumbnails of the world session may hold. */
    static final long MAX_TEXTURE_BYTES = 24L * 1024 * 1024;

    static final int MAX_ENTRIES = 512;
    /** A site's own thumbnail can be a full-size picture. */
    static final long MAX_DOWNLOAD_BYTES = 4L * 1024 * 1024;

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final TextureService textures;
    private final AsyncCache<String, ImageTextures> cache;

    /**
     * @param fallback decodes the WebP thumbnails YouTube serves
     */
    public Thumbnails(
            LinkInfoStore links,
            DiskCache disk,
            Http http,
            ImageDecoders.Fallback fallback,
            Executor io,
            Executor decode,
            TextureService textures,
            ClientThread clientThread) {
        this.textures = textures;
        var loader = new Loader(links, disk, http, fallback, io, decode);
        this.cache = new AsyncCache<>(
                "thumbnails",
                clientThread,
                loader::load,
                ImageTextures::bytes,
                image -> image.release(textures),
                MAX_TEXTURE_BYTES,
                MAX_ENTRIES);
    }

    /** The thumbnail of {@code url}, on the GPU, once loaded; starts loading it on first ask. */
    public Optional<ImageTextures> get(String url) {
        if (!(cache.get(url) instanceof AsyncCache.Entry.Ready<ImageTextures> ready)) {
            return Optional.empty();
        }
        ImageTextures image = ready.value();
        if (!image.isComplete()) {
            // One small still: uploaded whole the first time it is drawn.
            var unused = image.upload(textures, 1);
        }
        return image.isComplete() ? Optional.of(image) : Optional.empty();
    }

    /** Releases every texture; the disk keeps the pictures. */
    public void clear() {
        cache.clear();
    }

    @Override
    public void close() {
        clear();
    }

    /** Disk, or link → picture address → download → shrink → disk; then decode. Off the client thread. */
    private record Loader(
            LinkInfoStore links,
            DiskCache disk,
            Http http,
            ImageDecoders.Fallback fallback,
            Executor io,
            Executor decode) {

        CompletableFuture<ImageTextures> load(String url) {
            return CompletableFuture.supplyAsync(() -> disk.read(url), io)
                    .thenCompose(cached ->
                            cached.map(CompletableFuture::completedFuture).orElseGet(() -> fetch(url)))
                    .thenApplyAsync(jpeg -> new ImageTextures(decodeJpeg(jpeg)), decode);
        }

        private CompletableFuture<byte[]> fetch(String url) {
            return links.lookup(url)
                    .thenApplyAsync(
                            info -> download(info.thumbnail()
                                    .orElseThrow(() ->
                                            new UncheckedIOException(new IOException("No thumbnail for " + url)))),
                            io)
                    .thenApplyAsync(this::shrink, decode)
                    .thenApplyAsync(
                            jpeg -> {
                                disk.write(url, jpeg);
                                return jpeg;
                            },
                            io);
        }

        private byte[] download(String thumbnail) {
            try {
                return http.get(URI.create(thumbnail), MAX_DOWNLOAD_BYTES, TIMEOUT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private byte[] shrink(byte[] picture) {
            try {
                return ImageDecoders.jpeg(ImageDecoders.decode(picture, fallback), WIDTH, HEIGHT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static DecodedImage decodeJpeg(byte[] jpeg) {
            try {
                return ImageDecoders.decode(jpeg);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
