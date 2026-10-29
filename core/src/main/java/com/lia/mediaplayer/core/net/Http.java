package com.lia.mediaplayer.core.net;

import com.lia.mediaplayer.core.lifecycle.ClientThread;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The mod's one HTTP client. Every network request the mod makes goes through it.
 *
 * <p>HTTP/2 where the server offers it, redirects followed within the same or a stronger
 * scheme, one User-Agent, and a size cap on every body, checked while it streams so an
 * endless response costs nothing but the cap. Calls block; make them from a background
 * thread — on the client thread they fail at once.
 */
public final class Http implements AutoCloseable {

    public static final String USER_AGENT = "Mozilla/5.0 (compatible; LiasMediaPlayer/3)";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private final ClientThread clientThread;
    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("lmp-http-", 0).factory());
    private final HttpClient client;

    public Http(ClientThread clientThread) {
        this.clientThread = clientThread;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(executor)
                .build();
    }

    /** Receives download progress. {@code total} is -1 when the server did not say. */
    @FunctionalInterface
    public interface Progress {
        Progress NONE = (bytes, total) -> {};

        void update(long bytes, long total);
    }

    /**
     * Fetches {@code uri} into memory.
     *
     * @throws HttpStatusException on any status but 200
     * @throws IOException on a network failure, a timeout, or a body over {@code maxBytes}
     */
    public byte[] get(URI uri, long maxBytes, Duration timeout) throws IOException {
        HttpResponse<InputStream> response = send(uri, timeout);
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        var out = new ByteArrayOutputStream();
        try (InputStream body = response.body()) {
            copyCapped(body, out, maxBytes, total, Progress.NONE, uri);
        }
        return out.toByteArray();
    }

    /**
     * Streams {@code uri} into {@code target}, replacing it.
     *
     * @param minBytes a body shorter than this is an error page, not the file
     * @return the number of bytes written
     */
    public long download(URI uri, Path target, long minBytes, long maxBytes, Duration timeout, Progress progress)
            throws IOException {
        HttpResponse<InputStream> response = send(uri, timeout);
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long written;
        try (InputStream body = response.body();
                OutputStream file = Files.newOutputStream(target)) {
            written = copyCapped(body, file, maxBytes, total, progress, uri);
        }
        if (written < minBytes) {
            throw new IOException(
                    "Download from " + uri + " is only " + written + " bytes; expected at least " + minBytes);
        }
        return written;
    }

    private HttpResponse<InputStream> send(URI uri, Duration timeout) throws IOException {
        if (clientThread.isCurrent()) {
            throw new IllegalStateException("HTTP requests must not be made on the client thread");
        }
        var request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        }
        if (response.statusCode() != 200) {
            response.body().close();
            throw new HttpStatusException(response.statusCode(), uri);
        }
        return response;
    }

    private static long copyCapped(
            InputStream in, OutputStream out, long maxBytes, long total, Progress progress, URI uri)
            throws IOException {
        if (total > maxBytes) {
            throw new IOException("Response from " + uri + " is " + total + " bytes, over the limit of " + maxBytes);
        }
        byte[] buffer = new byte[64 * 1024];
        long copied = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            copied += read;
            if (copied > maxBytes) {
                throw new IOException("Response from " + uri + " exceeded the limit of " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
            progress.update(copied, total);
        }
        return copied;
    }

    /** Aborts requests in flight. */
    @Override
    public void close() {
        client.shutdownNow();
        executor.shutdownNow();
    }
}
