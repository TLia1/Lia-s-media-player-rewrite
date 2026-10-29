package com.lia.mediaplayer.core.link;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the {@code http(s)} links in a piece of text. Called for every chat message, so a
 * message without a link costs one {@code indexOf}.
 */
public final class LinkScanner {

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    /** Sentence punctuation that ends a message more often than it ends a link. */
    private static final String TRAILING = ".,;:!?'\">]}";

    private LinkScanner() {}

    /** A link found at {@code [start, end)} of the text. */
    public record Span(int start, int end, String url) {}

    /** Whether {@code text} can contain a link at all: the cheap test before {@link #scan}. */
    public static boolean mayContainLink(String text) {
        return text.indexOf("http") >= 0 || text.indexOf("HTTP") >= 0 || text.indexOf("Http") >= 0;
    }

    /** The links in {@code text}, in order, with trailing punctuation left out. */
    public static List<Span> scan(String text) {
        if (!mayContainLink(text)) {
            return List.of();
        }
        var spans = new ArrayList<Span>();
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            int end = trimEnd(text, matcher.start(), matcher.end());
            if (end > matcher.start()) {
                spans.add(new Span(matcher.start(), end, text.substring(matcher.start(), end)));
            }
        }
        return spans;
    }

    /**
     * Where the link really ends: before trailing punctuation, and before a closing bracket
     * that has no opening one inside the link — so "(see https://x/a_(b))" keeps the link's own
     * brackets and drops the sentence's.
     */
    private static int trimEnd(String text, int start, int end) {
        int e = end;
        while (e > start) {
            char last = text.charAt(e - 1);
            if (TRAILING.indexOf(last) >= 0) {
                e--;
            } else if (last == ')' && count(text, start, e, '(') < count(text, start, e, ')')) {
                e--;
            } else {
                break;
            }
        }
        return e;
    }

    private static int count(String text, int from, int to, char c) {
        int n = 0;
        for (int i = from; i < to; i++) {
            if (text.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
