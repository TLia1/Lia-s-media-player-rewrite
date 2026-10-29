package com.lia.mediaplayer.core.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.MediaPlayerCore;
import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.net.HttpStatusException;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestCores;
import com.lia.mediaplayer.core.testing.TestImages;
import com.lia.mediaplayer.core.testing.TestImages.GifFrame;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pictures through the engine's real pipeline: link, HTTP, decode, cache, textures. */
class ImageLoaderTest {

    @TempDir
    Path gameDir;

    @TempDir
    Path files;

    private static AsyncCache.Entry<ImageTextures> settle(FakePlatform platform, ImageCache images, MediaRef ref)
            throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            platform.runClientTasks();
            images.tick();
            AsyncCache.Entry<ImageTextures> entry = images.get(ref);
            if (!(entry instanceof AsyncCache.Entry.Loading<ImageTextures>)) {
                return entry;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("Still loading " + ref.url());
    }

    @Test
    void picturesAndGifsFromChatLinksReachTheirTextures() throws Exception {
        var platform = new FakePlatform(gameDir);
        try (var server = new MediaServer();
                var core = new MediaPlayerCore(platform, TestCores.OFFLINE)) {
            String png = server.serve("/cat.png", TestImages.write(files, "cat.png", TestImages.png(6, 4, 0xFF336699)));
            String gif = server.serve(
                    "/dance.gif",
                    TestImages.write(
                            files,
                            "dance.gif",
                            TestImages.gif(
                                    3,
                                    3,
                                    List.of(
                                            GifFrame.full(3, 3, 0xFFFF0000, 10),
                                            GifFrame.full(3, 3, 0xFF00FF00, 10)))));

            MediaRef pngRef = core.sources().match(png).orElseThrow();
            var still = settle(platform, core.images(), pngRef);
            assertThat(still).isInstanceOfSatisfying(AsyncCache.Entry.Ready.class, ready -> {
                var image = (ImageTextures) ready.value();
                assertThat(image.width()).isEqualTo(6);
                assertThat(platform.textures().pixels(image.frameAt(0))[0] & 0xFF)
                        .isEqualTo(0x33);
            });

            var animated =
                    settle(platform, core.images(), core.sources().match(gif).orElseThrow());
            assertThat(animated)
                    .isInstanceOfSatisfying(
                            AsyncCache.Entry.Ready.class,
                            ready -> assertThat(((ImageTextures) ready.value()).frameCount())
                                    .isEqualTo(2));
            // One request each, however often they were asked for.
            assertThat(server.hits("/cat.png")).isEqualTo(1);

            core.onWorldLeft();
            platform.textures().flushReleases();
            assertThat(platform.textures().liveCount()).isZero();
        }
    }

    @Test
    void aMissingOrBrokenPictureFails() throws Exception {
        var platform = new FakePlatform(gameDir);
        try (var server = new MediaServer();
                var core = new MediaPlayerCore(platform, TestCores.OFFLINE)) {
            String missing = server.fail("/gone.png", 404);
            String broken = server.serve(
                    "/fake.jpg", TestImages.write(files, "fake.jpg", "<html>".getBytes(StandardCharsets.UTF_8)));

            assertThat(settle(
                            platform,
                            core.images(),
                            core.sources().match(missing).orElseThrow()))
                    .isInstanceOfSatisfying(
                            AsyncCache.Entry.Failed.class,
                            failed -> assertThat(failed.cause()).isInstanceOf(HttpStatusException.class));
            assertThat(settle(
                            platform,
                            core.images(),
                            core.sources().match(broken).orElseThrow()))
                    .isInstanceOfSatisfying(
                            AsyncCache.Entry.Failed.class,
                            failed -> assertThat(failed.cause())
                                    .isInstanceOf(ImageDecoders.UnsupportedImageException.class));
        }
    }
}
