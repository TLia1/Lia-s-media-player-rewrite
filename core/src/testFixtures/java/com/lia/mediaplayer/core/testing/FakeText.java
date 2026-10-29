package com.lia.mediaplayer.core.testing;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import java.util.stream.Collectors;

/**
 * A monospaced font, {@value #CHAR_WIDTH} pixels a character, that "translates" a key into
 * itself followed by its arguments in brackets — so a test can read what was meant from what
 * was drawn.
 */
public final class FakeText implements TextMeasurer {

    public static final int CHAR_WIDTH = 6;
    public static final int LINE_HEIGHT = 8;

    @Override
    public int width(Text text) {
        return resolve(text).length() * CHAR_WIDTH;
    }

    @Override
    public int lineHeight() {
        return LINE_HEIGHT;
    }

    @Override
    public String resolve(Text text) {
        return switch (text) {
            case Text.Literal literal -> literal.text();
            case Text.Translatable translatable ->
                translatable.args().isEmpty()
                        ? translatable.key()
                        : translatable.key()
                                + translatable.args().stream()
                                        .map(arg -> arg instanceof Text t ? resolve(t) : String.valueOf(arg))
                                        .collect(Collectors.joining(",", "[", "]"));
        };
    }
}
