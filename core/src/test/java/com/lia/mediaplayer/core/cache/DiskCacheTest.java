package com.lia.mediaplayer.core.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskCacheTest {

    @TempDir
    Path dir;

    private static byte[] bytes(int size) {
        return new byte[size];
    }

    private long files() throws IOException {
        try (Stream<Path> list = Files.list(dir)) {
            return list.count();
        }
    }

    @Test
    void keepsWhatIsWrittenUnderAHashedName() {
        var cache = new DiskCache(dir, ".jpg", 1000);
        cache.write("https://example.com/a?x=../../etc", "hello".getBytes(StandardCharsets.UTF_8));
        assertThat(cache.read("https://example.com/a?x=../../etc"))
                .hasValueSatisfying(data ->
                        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("hello"));
        assertThat(cache.read("https://example.com/b")).isEmpty();
        assertThat(cache.fileName("anything")).matches("[0-9a-f]{32}\\.jpg");
        assertThat(dir.resolve(cache.fileName("https://example.com/a?x=../../etc")))
                .exists();
        assertThat(cache.bytes()).isEqualTo(5);
    }

    @Test
    void prunesTheLeastRecentlyUsedPastItsCap() throws IOException {
        var cache = new DiskCache(dir, ".jpg", 250);
        cache.write("a", bytes(100));
        cache.write("b", bytes(100));
        Files.setLastModifiedTime(
                dir.resolve(cache.fileName("a")), FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
        Files.setLastModifiedTime(
                dir.resolve(cache.fileName("b")), FileTime.from(Instant.parse("2021-01-01T00:00:00Z")));
        // Reading a touches it: now b is the oldest.
        assertThat(cache.read("a")).isPresent();
        cache.write("c", bytes(100));
        assertThat(cache.read("b")).isEmpty();
        assertThat(cache.read("a")).isPresent();
        assertThat(cache.read("c")).isPresent();
        assertThat(cache.bytes()).isEqualTo(200);
    }

    @Test
    void pruningAtStartAppliesANewCapAndSweepsLeftovers() throws IOException {
        var cache = new DiskCache(dir, ".jpg", 1000);
        cache.write("a", bytes(300));
        cache.write("b", bytes(300));
        Files.write(dir.resolve("write-123.tmp"), bytes(10));
        Files.write(dir.resolve("unrelated.txt"), bytes(10));

        var restarted = new DiskCache(dir, ".jpg", 400);
        restarted.prune();
        assertThat(restarted.bytes()).isEqualTo(300);
        assertThat(dir.resolve("write-123.tmp")).doesNotExist();
        assertThat(dir.resolve("unrelated.txt")).exists();
    }

    @Test
    void aCapOfZeroKeepsNothingAndClearEmptiesIt() throws IOException {
        var cache = new DiskCache(dir, ".jpg", 1000);
        cache.write("a", bytes(10));
        cache.write("b", bytes(10));
        cache.clear();
        assertThat(files()).isZero();
        assertThat(cache.bytes()).isZero();

        cache.setMaxBytes(0);
        cache.write("c", bytes(10));
        assertThat(cache.read("c")).isEmpty();
        assertThat(cache.maxBytes()).isZero();
    }

    @Test
    void aMissingFolderIsAnEmptyCache() {
        var cache = new DiskCache(dir.resolve("not/yet"), ".jpg", 1000);
        cache.prune();
        assertThat(cache.read("a")).isEmpty();
        cache.write("a", bytes(3));
        assertThat(cache.read("a")).isPresent();
    }
}
