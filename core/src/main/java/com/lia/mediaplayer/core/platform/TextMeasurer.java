package com.lia.mediaplayer.core.platform;

import com.lia.mediaplayer.api.text.Text;

/** Sizes of text in the game's font, in GUI pixels. Client thread only. */
public interface TextMeasurer {

    /** The width of {@code text} on one line, translated into the player's language. */
    int width(Text text);

    /** The width of a plain string. */
    default int width(String text) {
        return width(Text.literal(text));
    }

    /** The height of one line, glyphs only, without the gap to the next line. */
    int lineHeight();

    /** {@code text} translated into the player's language, as it would be drawn. */
    String resolve(Text text);
}
