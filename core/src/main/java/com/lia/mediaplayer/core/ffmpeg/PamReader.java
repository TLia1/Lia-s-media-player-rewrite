package com.lia.mediaplayer.core.ffmpeg;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;

/**
 * Reads the header in front of each frame ffmpeg writes with {@code -c:v pam}:
 *
 * <pre>
 * P7
 * WIDTH 1280
 * HEIGHT 720
 * DEPTH 4
 * MAXVAL 255
 * TUPLTYPE RGB_ALPHA
 * ENDHDR
 * </pre>
 *
 * followed by {@code WIDTH × HEIGHT × DEPTH} bytes. Allocation-free: the header is parsed byte
 * by byte from the (buffered) process stream.
 */
final class PamReader {

    /** The geometry of one frame. */
    record Header(int width, int height, int depth) {
        int frameBytes() {
            return width * height * depth;
        }
    }

    private static final int MAX_LINE = 80;
    private static final int MAX_SIDE = 16_384;

    private PamReader() {}

    /**
     * Reads one header.
     *
     * @return {@code null} at a clean end of stream, before the first byte of a header
     * @throws IOException if the stream ends mid-header or the header is not one ffmpeg writes
     */
    static @Nullable Header readHeader(InputStream in) throws IOException {
        int first = in.read();
        if (first < 0) {
            return null;
        }
        if (first != 'P' || in.read() != '7' || in.read() != '\n') {
            throw new IOException("Not a PAM frame");
        }
        int width = -1;
        int height = -1;
        int depth = -1;
        while (true) {
            int key = readKey(in);
            switch (key) {
                case KEY_WIDTH -> width = readNumber(in);
                case KEY_HEIGHT -> height = readNumber(in);
                case KEY_DEPTH -> depth = readNumber(in);
                case KEY_END -> {
                    if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE || depth != 4) {
                        throw new IOException("Unsupported PAM frame " + width + "x" + height + "x" + depth);
                    }
                    return new Header(width, height, depth);
                }
                default -> skipLine(in);
            }
        }
    }

    private static final int KEY_OTHER = 0;
    private static final int KEY_WIDTH = 1;
    private static final int KEY_HEIGHT = 2;
    private static final int KEY_DEPTH = 3;
    private static final int KEY_END = 4;

    /**
     * Reads the first word of a header line and says which one it was. Stops after the space,
     * or after the newline for {@code ENDHDR}.
     */
    private static int readKey(InputStream in) throws IOException {
        int key = keyOf(next(in), next(in));
        if (key == KEY_OTHER) {
            return KEY_OTHER;
        }
        int length = 2;
        int c;
        while ((c = next(in)) != ' ' && c != '\n') {
            if (++length > MAX_LINE) {
                throw new IOException("PAM header line too long");
            }
        }
        if (key == KEY_END && c != '\n') {
            skipLine(in);
        }
        return key;
    }

    /** The four keys that matter differ in their first two letters. */
    private static int keyOf(int first, int second) {
        if (first == 'W' && second == 'I') {
            return KEY_WIDTH;
        }
        if (first == 'H' && second == 'E') {
            return KEY_HEIGHT;
        }
        if (first == 'D' && second == 'E') {
            return KEY_DEPTH;
        }
        if (first == 'E' && second == 'N') {
            return KEY_END;
        }
        return KEY_OTHER;
    }

    private static int readNumber(InputStream in) throws IOException {
        int value = 0;
        int digits = 0;
        int c;
        while ((c = next(in)) != '\n') {
            if (c < '0' || c > '9' || ++digits > 6) {
                throw new IOException("Bad number in PAM header");
            }
            value = value * 10 + (c - '0');
        }
        if (digits == 0) {
            throw new IOException("Missing number in PAM header");
        }
        return value;
    }

    private static void skipLine(InputStream in) throws IOException {
        int length = 0;
        while (next(in) != '\n') {
            if (++length > MAX_LINE) {
                throw new IOException("PAM header line too long");
            }
        }
    }

    private static int next(InputStream in) throws IOException {
        int c = in.read();
        if (c < 0) {
            throw new EOFException("Stream ended inside a PAM header");
        }
        return c;
    }
}
