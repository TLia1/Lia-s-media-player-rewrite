package com.lia.mediaplayer.core.tools;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The last lines a process wrote to one of its streams, bounded by size. ffmpeg and yt-dlp
 * explain a failure on stderr, and the explanation is the last thing they write.
 *
 * <p>Thread-safe: one thread appends, any thread reads.
 */
public final class OutputTail {

    private final int maxChars;
    private final Deque<String> lines = new ArrayDeque<>();
    private int chars;

    public OutputTail(int maxChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be positive");
        }
        this.maxChars = maxChars;
    }

    public synchronized void append(String line) {
        String kept = line.length() > maxChars ? line.substring(line.length() - maxChars) : line;
        lines.addLast(kept);
        chars += kept.length() + 1;
        while (chars > maxChars && lines.size() > 1) {
            chars -= lines.removeFirst().length() + 1;
        }
    }

    /** The retained lines, joined with newlines, trimmed. */
    public synchronized String text() {
        return String.join("\n", lines).strip();
    }
}
