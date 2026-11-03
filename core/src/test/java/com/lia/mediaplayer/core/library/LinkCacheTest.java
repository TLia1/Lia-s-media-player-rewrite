package com.lia.mediaplayer.core.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.source.MediaMetadata;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.api.source.ResolvedMedia;
import com.lia.mediaplayer.api.source.Resolver;
import com.lia.mediaplayer.api.source.StreamUrl;
import com.lia.mediaplayer.core.cache.DiskCache;
import com.lia.mediaplayer.core.image.ImageDecoders;
import com.lia.mediaplayer.core.image.ImageTextures;
import com.lia.mediaplayer.core.lifecycle.CoreExecutors;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.net.Http;
import com.lia.mediaplayer.core.resolve.DirectResolver;
import com.lia.mediaplayer.core.resolve.ResolveCache;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import com.lia.mediaplayer.core.storage.DataPaths;
import com.lia.mediaplayer.core.storage.JsonStore;
import com.lia.mediaplayer.core.testing.FakePlatform;
import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestImages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The titles and thumbnails kept between game sessions: {@link LinkInfoStore}, {@link Titles}, {@link Thumbnails}. */
class LinkCacheTest {

    private static final String VIDEO = "https://www.youtube.com/watch?v=aqz-KE-bpKQ";

    @TempDir
    Path gameDir;

    @TempDir
    Path files;

    private final CoreExecutors executors = new CoreExecutors();
    private final SourceRegistry sources = new SourceRegistry(BuiltinSources.all());
    private final AtomicInteger resolutions = new AtomicInteger();
    private FakePlatform platform;
    private DataPaths paths;
    private MediaServer server;
    private Http http;
    private String thumbnailUrl = "";

    @BeforeEach
    void start() throws Exception {
        platform = new FakePlatform(gameDir);
        paths = DataPaths.under(gameDir);
        server = new MediaServer();
        http = new Http(platform.clientThread());
        thumbnailUrl =
                server.serve("/thumb.png", TestImages.write(files, "thumb.png", TestImages.png(640, 360, 0xFF2266AA)));
    }

    @AfterEach
    void stop() {
        http.close();
        server.close();
        executors.close();
    }

    /** Resolves every video link to a title and the served thumbnail, counting the calls. */
    private final class Site implements Resolver {
        @Override
        public String id() {
            return "test:site";
        }

        @Override
        public boolean handles(MediaRef ref) {
            return ref.sourceId().endsWith(":youtube");
        }

        @Override
        public CompletableFuture<ResolvedMedia> resolve(MediaRef ref, ResolveOptions options) {
            resolutions.incrementAndGet();
            var metadata = new MediaMetadata(
                    Optional.of("Big Buck Bunny"),
                    Optional.empty(),
                    Optional.of(thumbnailUrl),
                    false,
                    0,
                    0,
                    0,
                    MediaMetadata.Tracks.UNKNOWN);
            return CompletableFuture.completedFuture(new ResolvedMedia(
                    ref,
                    Optional.of(StreamUrl.of("https://cdn.example/v.mp4")),
                    Optional.empty(),
                    Map.of(),
                    metadata,
                    Instant.MAX));
        }
    }

    /** A store over {@code titles.json}, as a new game session would open it. */
    private JsonStore<Map<String, LinkInfo>> file() {
        return new JsonStore<>(
                paths.titles(),
                LinkInfoStore.codec(),
                LinkInfoStore.SCHEMA,
                List.of(),
                executors.io(),
                executors.timer(),
                Duration.ofMillis(10));
    }

    private LinkInfoStore links(JsonStore<Map<String, LinkInfo>> file) {
        var chain = new ResolverChain(
                List.of(new Site(), new DirectResolver(InstantSource.system())),
                new ResolveCache(InstantSource.system(), 8));
        return new LinkInfoStore(file, sources, chain, executors.io());
    }

    private Thumbnails thumbnails(LinkInfoStore links, DiskCache disk) {
        return new Thumbnails(
                links,
                disk,
                http,
                ImageDecoders.Fallback.NONE,
                executors.io(),
                executors.decode(),
                platform.textures(),
                platform.clientThread());
    }

    private <T> T await(Supplier<Optional<T>> poll) throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            platform.runClientTasks();
            Optional<T> value = poll.get();
            if (value.isPresent()) {
                return value.get();
            }
            Thread.sleep(5);
        }
        throw new AssertionError("Timed out");
    }

    @Test
    void aSecondGameSessionHasTheTitlesAndPicturesWithoutAskingAnyone() throws Exception {
        var file = file();
        var links = links(file);
        var disk = new DiskCache(paths.thumbnails(), ".jpg", 1024 * 1024);
        var titles = new Titles(links, platform.clientThread());
        var thumbnails = thumbnails(links, disk);

        assertThat(await(() -> titles.get(VIDEO))).isEqualTo("Big Buck Bunny");
        ImageTextures picture = await(() -> thumbnails.get(VIDEO));
        assertThat(picture.width()).isEqualTo(Thumbnails.WIDTH);
        assertThat(picture.height()).isEqualTo(Thumbnails.HEIGHT);
        assertThat(platform.textures().pixels(picture.frameAt(0))[2] & 0xFF).isGreaterThan(0x90);
        assertThat(resolutions).hasValue(1);
        assertThat(server.hits("/thumb.png")).isEqualTo(1);
        assertThat(disk.bytes()).isPositive();
        file.flush();
        assertThat(Files.readString(paths.titles())).contains("Big Buck Bunny", thumbnailUrl);

        // The next game session: the same files, nothing in memory.
        var nextLinks = links(file());
        var nextTitles = new Titles(nextLinks, platform.clientThread());
        var nextThumbnails = thumbnails(nextLinks, new DiskCache(paths.thumbnails(), ".jpg", 1024 * 1024));
        assertThat(await(() -> nextTitles.get(VIDEO))).isEqualTo("Big Buck Bunny");
        assertThat(await(() -> nextThumbnails.get(VIDEO)).width()).isEqualTo(Thumbnails.WIDTH);
        assertThat(resolutions).hasValue(1);
        assertThat(server.hits("/thumb.png")).isEqualTo(1);

        nextThumbnails.clear();
        platform.textures().flushReleases();
        assertThat(platform.textures().isLive(picture.frameAt(0))).isTrue();
        thumbnails.close();
        platform.textures().flushReleases();
        assertThat(platform.textures().liveCount()).isZero();
    }

    @Test
    void aPictureIsItsOwnThumbnailAndABareFileHasNone() throws Exception {
        var links = links(file());
        var disk = new DiskCache(paths.thumbnails(), ".jpg", 1024 * 1024);
        assertThat(links.lookup(thumbnailUrl).get().thumbnail()).contains(thumbnailUrl);
        var thumbnails = thumbnails(links, disk);
        assertThat(await(() -> thumbnails.get(thumbnailUrl)).width()).isEqualTo(Thumbnails.WIDTH);

        String clip = "https://cdn.example/clip.mp4";
        assertThat(links.lookup(clip).get()).isEqualTo(LinkInfo.NONE);
        assertThat(links.lookup("https://www.youtube.com/playlist?list=PL123").get())
                .isEqualTo(LinkInfo.NONE);
    }

    @Test
    void clearingForgetsTheLinks() throws Exception {
        var links = links(file());
        var unused = links.lookup(VIDEO).get();
        assertThat(links.size()).isEqualTo(1);
        links.clear();
        assertThat(links.size()).isZero();
        // Looked up again; the resolver's own cache may answer it this time.
        var again = links.lookup(VIDEO).get();
        assertThat(again.title()).isEqualTo("Big Buck Bunny");
        assertThat(links.size()).isEqualTo(1);
    }

    @Test
    void theFileSkipsWhatIsNotALink() throws Exception {
        Files.createDirectories(paths.cache());
        Files.writeString(paths.titles(), """
                {"schema": 1, "links": {
                  "file:///etc/passwd": {"title": "no"},
                  "https://a.example/x": {"title": "A", "thumbnail": "javascript:alert(1)"},
                  "https://b.example/y": "not an object"
                }}
                """);
        var links = links(file());
        assertThat(links.size()).isEqualTo(1);
        assertThat(links.lookup("https://a.example/x").get()).isEqualTo(new LinkInfo("A", Optional.empty()));
        assertThat(resolutions).hasValue(0);
    }
}
