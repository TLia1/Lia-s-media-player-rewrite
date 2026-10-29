package com.lia.mediaplayer.core.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * A local HTTP server for media in tests: serves files with byte ranges (ffmpeg seeks with
 * them) and answers chosen paths with a status code. Counts requests per path.
 */
public final class MediaServer implements AutoCloseable {

    private static final Pattern RANGE = Pattern.compile("bytes=(\\d+)-(\\d*)");

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Path> files = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

    public MediaServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    /** Serves {@code file} at {@code path} and returns its URL. */
    public String serve(String path, Path file) {
        files.put(path, file);
        statuses.remove(path);
        return url(path);
    }

    /** Answers {@code path} with {@code status} and an empty body, and returns its URL. */
    public String fail(String path, int status) {
        statuses.put(path, status);
        return url(path);
    }

    public String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    public int hits(String path) {
        AtomicInteger count = hits.get(path);
        return count == null ? 0 : count.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            hits.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
            Integer status = statuses.get(path);
            Path file = files.get(path);
            if (status != null || file == null) {
                exchange.sendResponseHeaders(status != null ? status : 404, -1);
                return;
            }
            try (var in = new RandomAccessFile(file.toFile(), "r")) {
                long length = in.length();
                long start = 0;
                long end = length - 1;
                String range = exchange.getRequestHeaders().getFirst("Range");
                var matcher = range == null ? null : RANGE.matcher(range);
                boolean partial = matcher != null && matcher.matches();
                if (partial) {
                    start = Long.parseLong(matcher.group(1));
                    if (!matcher.group(2).isEmpty()) {
                        end = Math.min(end, Long.parseLong(matcher.group(2)));
                    }
                    if (start >= length) {
                        exchange.getResponseHeaders().add("Content-Range", "bytes */" + length);
                        exchange.sendResponseHeaders(416, -1);
                        return;
                    }
                    exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + length);
                }
                exchange.getResponseHeaders().add("Accept-Ranges", "bytes");
                long count = end - start + 1;
                exchange.sendResponseHeaders(partial ? 206 : 200, count);
                in.seek(start);
                byte[] buffer = new byte[64 * 1024];
                try (OutputStream out = exchange.getResponseBody()) {
                    while (count > 0) {
                        int read = in.read(buffer, 0, (int) Math.min(buffer.length, count));
                        if (read < 0) {
                            break;
                        }
                        out.write(buffer, 0, read);
                        count -= read;
                    }
                } catch (IOException e) {
                    // The client went away: ffmpeg closing a connection to seek.
                }
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
