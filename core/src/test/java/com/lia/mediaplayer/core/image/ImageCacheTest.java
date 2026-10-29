package com.lia.mediaplayer.core.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.FakeTextures;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageCacheTest {

    private static final MediaRef GIF = ref("https://cdn.example/a.gif");
    private static final MediaRef PNG = ref("https://cdn.example/b.png");

    @TempDir
    Path dir;

    private final Map<MediaRef, CompletableFuture<ImageTextures>> loads = new HashMap<>();

    private static MediaRef ref(String url) {
        return new MediaRef(url, MediaKind.IMAGE, "test:image", Text.literal("[picture]"));
    }

    /** {@code frames} frames of {@code side × side}, 100 ms each when animated. */
    private static ImageTextures image(int side, int frames) {
        var list = new ArrayList<DecodedImage.Frame>();
        for (int i = 0; i < frames; i++) {
            list.add(new DecodedImage.Frame(ByteBuffer.allocate(side * side * 4), frames > 1 ? 100 : 0));
        }
        return new ImageTextures(new DecodedImage(side, side, list));
    }

    private ImageCache cache(FakePlatform platform, long maxBytes) {
        return new ImageCache(
                ref -> loads.computeIfAbsent(ref, r -> new CompletableFuture<>()),
                platform.textures(),
                platform.clientThread(),
                maxBytes);
    }

    @Test
    void aGifIsReadyOnceEveryFrameIsUploadedAFewATick() {
        var platform = new FakePlatform(dir);
        var cache = cache(platform, Long.MAX_VALUE);
        // 1024² is 4 MB a frame: the 16 MB budget uploads four a tick.
        assertThat(cache.get(GIF)).isInstanceOf(AsyncCache.Entry.Loading.class);
        loads.get(GIF).complete(image(1024, 10));
        platform.runClientTasks();

        FakeTextures textures = platform.textures();
        assertThat(cache.get(GIF)).isInstanceOf(AsyncCache.Entry.Loading.class);
        cache.tick();
        assertThat(textures.liveCount()).isEqualTo(4);
        cache.tick();
        assertThat(cache.get(GIF)).isInstanceOf(AsyncCache.Entry.Loading.class);
        cache.tick();

        var entry = cache.get(GIF);
        assertThat(entry).isInstanceOf(AsyncCache.Entry.Ready.class);
        ImageTextures ready = ((AsyncCache.Entry.Ready<ImageTextures>) entry).value();
        assertThat(textures.liveCount()).isEqualTo(10);
        assertThat(ready.frameCount()).isEqualTo(10);
        // Frames follow a wall clock shared by every view.
        assertThat(ready.frameAt(0)).isEqualTo(ready.frameAt(99_000_000L));
        assertThat(ready.frameAt(0)).isNotEqualTo(ready.frameAt(100_000_000L));
        assertThat(ready.frameAt(1_000_000_000L)).isEqualTo(ready.frameAt(0));
        assertThat(cache.bytes()).isEqualTo(10L * 1024 * 1024 * 4);
    }

    @Test
    void aStillIsOneTextureAndOneTick() {
        var platform = new FakePlatform(dir);
        var cache = cache(platform, Long.MAX_VALUE);
        cache.get(PNG);
        loads.get(PNG).complete(image(4, 1));
        platform.runClientTasks();
        cache.tick();
        var ready = (AsyncCache.Entry.Ready<ImageTextures>) cache.get(PNG);
        assertThat(ready.value().frameAt(123_456_789L)).isNotNull();
        assertThat(ready.value().width()).isEqualTo(4);
    }

    @Test
    void releasesTheTexturesOfWhatItDrops() {
        var platform = new FakePlatform(dir);
        var cache = cache(platform, 2 * 64 * 64 * 4);
        for (MediaRef ref : List.of(GIF, PNG)) {
            cache.get(ref);
            loads.get(ref).complete(image(64, 2));
            platform.runClientTasks();
            cache.tick();
        }
        platform.textures().flushReleases();
        assertThat(platform.textures().liveCount()).as("the GIF gave way").isEqualTo(2);

        cache.clear();
        platform.textures().flushReleases();
        assertThat(platform.textures().liveCount()).isZero();
        assertThat(cache.bytes()).isZero();
    }

    @Test
    void retryForgetsAFailure() {
        var platform = new FakePlatform(dir);
        var cache = cache(platform, Long.MAX_VALUE);
        cache.get(PNG);
        loads.remove(PNG).completeExceptionally(new java.io.IOException("gone"));
        platform.runClientTasks();
        assertThat(cache.get(PNG)).isInstanceOf(AsyncCache.Entry.Failed.class);

        cache.retry(PNG);
        assertThat(cache.get(PNG)).isInstanceOf(AsyncCache.Entry.Loading.class);
        assertThat(loads).containsKey(PNG);
    }
}
