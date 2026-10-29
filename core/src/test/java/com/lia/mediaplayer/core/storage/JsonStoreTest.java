package com.lia.mediaplayer.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonStoreTest {

    /** A file holding one number, under "count". */
    private record Counter(int count) {}

    private static final JsonStore.Codec<Counter> CODEC = new JsonStore.Codec<>() {
        @Override
        public Counter defaults() {
            return new Counter(0);
        }

        @Override
        public Counter decode(JsonObject json) {
            return new Counter(json.get("count").getAsInt());
        }

        @Override
        public JsonObject encode(Counter value) {
            var json = new JsonObject();
            json.addProperty("count", value.count());
            return json;
        }
    };

    @TempDir
    Path dir;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void stopTimer() {
        timer.shutdownNow();
    }

    private JsonStore<Counter> store(Duration debounce, List<JsonStore.Migration> migrations, int schema) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);
        return new JsonStore<>(
                dir.resolve("counter.json"), CODEC, schema, migrations, Runnable::run, timer, debounce, clock);
    }

    private JsonStore<Counter> store() {
        return store(Duration.ofHours(1), List.of(), 1);
    }

    private String file() throws IOException {
        return Files.readString(dir.resolve("counter.json"), StandardCharsets.UTF_8);
    }

    @Test
    void aMissingFileGivesTheDefaults() {
        assertThat(store().get()).isEqualTo(new Counter(0));
        assertThat(dir.resolve("counter.json")).doesNotExist();
    }

    @Test
    void anUpdateIsWrittenOnFlushWithItsSchema() throws IOException {
        var store = store();
        store.update(c -> new Counter(c.count() + 5));
        assertThat(dir.resolve("counter.json")).doesNotExist();
        store.flush();
        assertThat(file()).contains("\"schema\": 1").contains("\"count\": 5");
        assertThat(store().get()).isEqualTo(new Counter(5));
    }

    @Test
    void anUpdateThatChangesNothingWritesNothing() {
        var store = store();
        store.update(c -> new Counter(0));
        store.flush();
        assertThat(dir.resolve("counter.json")).doesNotExist();
    }

    @Test
    void writesHappenOnceChangesStop() throws Exception {
        var store = store(Duration.ofMillis(50), List.of(), 1);
        for (int i = 1; i <= 10; i++) {
            final int n = i;
            store.set(new Counter(n));
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(dir.resolve("counter.json")) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(file()).contains("\"count\": 10");
    }

    @Test
    void anUnreadableFileIsSetAsideNotOverwritten() throws IOException {
        Files.writeString(dir.resolve("counter.json"), "{ not json");
        var store = store();
        assertThat(store.get()).isEqualTo(new Counter(0));
        Path aside = dir.resolve("counter.broken-20261029-120000.json");
        assertThat(aside).exists().hasContent("{ not json");
        store.set(new Counter(1));
        store.close();
        assertThat(file()).contains("\"count\": 1");
        assertThat(aside).exists();
    }

    @Test
    void anOldSchemaIsMigratedStepByStep() throws IOException {
        Files.writeString(dir.resolve("counter.json"), "{\"schema\": 1, \"n\": 3}");
        var store = store(
                Duration.ofHours(1),
                List.of(
                        new JsonStore.Migration(1, json -> {
                            json.add("total", json.remove("n"));
                            return json;
                        }),
                        new JsonStore.Migration(2, json -> {
                            json.add("count", json.remove("total"));
                            return json;
                        })),
                3);
        assertThat(store.get()).isEqualTo(new Counter(3));
    }

    @Test
    void aMissingMigrationKeepsTheFileAside() throws IOException {
        Files.writeString(dir.resolve("counter.json"), "{\"schema\": 1, \"count\": 3}");
        assertThat(store(Duration.ofHours(1), List.of(), 2).get()).isEqualTo(new Counter(0));
        assertThat(dir.resolve("counter.json")).doesNotExist();
    }

    @Test
    void aNewerSchemaIsReadAsFarAsPossible() throws IOException {
        Files.writeString(dir.resolve("counter.json"), "{\"schema\": 9, \"count\": 4, \"extra\": true}");
        assertThat(store().get()).isEqualTo(new Counter(4));
    }
}
