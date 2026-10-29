package com.lia.mediaplayer.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.core.testing.MediaServer;
import com.lia.mediaplayer.core.testing.TestClientThread;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpTest {

    @TempDir
    Path dir;

    private final TestClientThread client = new TestClientThread();
    private final Http http = new Http(client);

    @AfterEach
    void close() {
        http.close();
        client.close();
    }

    @Test
    void fetchesWithinItsLimits() throws IOException {
        try (var server = new MediaServer()) {
            URI small = URI.create(server.serve("/small", Files.write(dir.resolve("small"), new byte[100])));
            assertThat(http.get(small, 100, Duration.ofSeconds(5))).hasSize(100);
            assertThatThrownBy(() -> http.get(small, 99, Duration.ofSeconds(5))).hasMessageContaining("over the limit");

            URI missing = URI.create(server.fail("/missing", 404));
            assertThatThrownBy(() -> http.get(missing, 100, Duration.ofSeconds(5)))
                    .isInstanceOfSatisfying(
                            HttpStatusException.class,
                            e -> assertThat(e.status()).isEqualTo(404))
                    .hasMessageContaining("HTTP 404");
        }
    }

    @Test
    void downloadsToAFileAndRejectsErrorPages() throws IOException {
        try (var server = new MediaServer()) {
            URI file = URI.create(server.serve("/file", Files.write(dir.resolve("src"), new byte[5000])));
            Path target = dir.resolve("out");
            assertThat(http.download(file, target, 1000, 10_000, Duration.ofSeconds(5), Http.Progress.NONE))
                    .isEqualTo(5000);
            assertThat(Files.size(target)).isEqualTo(5000);
            assertThatThrownBy(
                            () -> http.download(file, target, 6000, 10_000, Duration.ofSeconds(5), Http.Progress.NONE))
                    .hasMessageContaining("only 5000 bytes");
        }
    }

    @Test
    void refusesToBlockTheClientThread() {
        assertThatThrownBy(
                        () -> client.call(() -> http.get(URI.create("http://127.0.0.1:9/"), 1, Duration.ofSeconds(1))))
                .rootCause()
                .hasMessageContaining("client thread");
    }
}
