package com.lia.mediaplayer.api.text;

import java.util.List;

/**
 * A piece of text to show to the player, without naming the game's own text type.
 *
 * <p>The platform layer converts it to and from Minecraft's {@code Component}. Styling
 * (colour, bold, underline) joins when chat rewriting needs it.
 */
public sealed interface Text {

    /** Text shown as written. */
    record Literal(String text) implements Text {}

    /**
     * A translation key, resolved in the player's language when shown.
     *
     * @param args substituted for the key's {@code %s} placeholders: strings, numbers or
     *     other {@code Text}
     */
    record Translatable(String key, List<Object> args) implements Text {
        public Translatable {
            if (key.isBlank()) {
                throw new IllegalArgumentException("Translation key must not be blank");
            }
            args = List.copyOf(args);
            for (Object arg : args) {
                if (!(arg instanceof String || arg instanceof Number || arg instanceof Text)) {
                    throw new IllegalArgumentException("Translation arguments must be strings, numbers or Text, not "
                            + arg.getClass().getName());
                }
            }
        }
    }

    static Text literal(String text) {
        return new Literal(text);
    }

    static Text translatable(String key, Object... args) {
        return new Translatable(key, List.of(args));
    }
}
