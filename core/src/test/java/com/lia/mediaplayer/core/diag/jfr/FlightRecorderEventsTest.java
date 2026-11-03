package com.lia.mediaplayer.core.diag.jfr;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.source.ResolveOptions;
import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.link.BuiltinSources;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.resolve.DirectResolver;
import com.lia.mediaplayer.core.resolve.ResolveCache;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import com.lia.mediaplayer.core.testing.TestClientThread;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The events show up in a recording, with their fields, and cost nothing without one. */
class FlightRecorderEventsTest {

    @TempDir
    Path dir;

    @Test
    void aRecordingSeesResolutionsCacheLoadsAndUnderruns() throws Exception {
        List<RecordedEvent> events;
        try (var recording = new Recording();
                var client = new TestClientThread()) {
            for (String name : List.of("Resolve", "CacheLoad", "RingUnderrun", "FrameUpload", "SessionStart")) {
                recording.enable("liasmediaplayer." + name).withoutThreshold();
            }
            recording.start();

            var chain = new ResolverChain(
                    List.of(new DirectResolver(InstantSource.system())), new ResolveCache(InstantSource.system(), 4));
            MediaRef ref = new SourceRegistry(BuiltinSources.all())
                    .match("https://cdn.example/a.mp4")
                    .orElseThrow();
            chain.resolve(ref, ResolveOptions.DEFAULT).get();
            chain.resolve(ref, ResolveOptions.DEFAULT).get();

            var cache = new AsyncCache<String, String>(
                    "test",
                    client,
                    key -> CompletableFuture.completedFuture(key + "!"),
                    String::length,
                    v -> {},
                    100,
                    4);
            client.run(() -> cache.get("key"));
            client.run(() -> {});

            RingUnderrunEvent.report(7, "audio", 2);
            var upload = FrameUploadEvent.start(640, 360);
            upload.finish();
            SessionStartEvent.start(7, 1, false, true, true).finish(360, true);

            recording.stop();
            Path file = dir.resolve("events.jfr");
            recording.dump(file);
            events = RecordingFile.readAllEvents(file);
        }
        assertThat(events)
                .filteredOn(e -> e.getEventType().getName().equals("liasmediaplayer.Resolve"))
                .extracting(e -> e.getBoolean("cached"))
                .containsExactly(false, true);
        assertThat(events)
                .filteredOn(e -> e.getEventType().getName().equals("liasmediaplayer.CacheLoad"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getString("cache")).isEqualTo("test");
                    assertThat(e.getLong("weight")).isEqualTo(4);
                    assertThat(e.getBoolean("succeeded")).isTrue();
                });
        assertThat(events)
                .filteredOn(e -> e.getEventType().getName().equals("liasmediaplayer.RingUnderrun"))
                .singleElement()
                .satisfies(e -> assertThat(e.getLong("count")).isEqualTo(2));
        assertThat(events)
                .filteredOn(e -> e.getEventType().getName().equals("liasmediaplayer.FrameUpload"))
                .singleElement()
                .satisfies(e -> assertThat(e.getLong("bytes")).isEqualTo(640L * 360 * 4));
        assertThat(events)
                .filteredOn(e -> e.getEventType().getName().equals("liasmediaplayer.SessionStart"))
                .singleElement()
                .satisfies(e -> assertThat(e.getInt("height")).isEqualTo(360));
        assertThat(events.getFirst().getEventType().getCategoryNames()).contains(Events.CATEGORY);
    }

    @Test
    void withoutARecordingNothingIsKept() {
        var event = ResolveEvent.start("https://example.com/x");
        assertThat(event.isEnabled()).isFalse();
        event.finish(false, true);
    }
}
