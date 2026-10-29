package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.core.platform.TextMeasurer;
import java.util.ArrayList;
import java.util.List;

/** Breaks text into lines that fit a width, between words where it can. */
public final class TextWrap {

    private TextWrap() {}

    /**
     * {@code text} as lines no wider than {@code width}. Line breaks in the text are kept; a
     * word too long for a line on its own is cut wherever it must be.
     */
    public static List<String> lines(TextMeasurer measurer, String text, int width) {
        var lines = new ArrayList<String>();
        if (width <= 0) {
            return lines;
        }
        for (String paragraph : text.split("\n", -1)) {
            var line = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (measurer.width(candidate) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (measurer.width(rest) > width) {
                    int cut = fittingPrefix(measurer, rest, width);
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** The length of the longest start of {@code s} that fits, at least one character. */
    private static int fittingPrefix(TextMeasurer measurer, String s, int width) {
        int lo = 1;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (measurer.width(s.substring(0, mid)) <= width) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
