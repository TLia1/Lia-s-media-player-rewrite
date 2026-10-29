package com.lia.mediaplayer.ui.input;

/**
 * The keys the UI tells apart, without the game's key codes. Letters and digits are by their
 * position on a US layout, like the game's own key codes; anything else is {@link #OTHER}.
 */
public enum Key {
    SPACE,
    ENTER,
    ESCAPE,
    TAB,
    BACKSPACE,
    DELETE,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,
    A,
    B,
    C,
    D,
    E,
    F,
    G,
    H,
    I,
    J,
    K,
    L,
    M,
    N,
    O,
    P,
    Q,
    R,
    S,
    T,
    U,
    V,
    W,
    X,
    Y,
    Z,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,
    OTHER;

    /** The letter key for {@code c}, case ignored, or {@link #OTHER}. */
    public static Key letter(char c) {
        char upper = Character.toUpperCase(c);
        return upper >= 'A' && upper <= 'Z' ? valueOf(String.valueOf(upper)) : OTHER;
    }

    /** The digit key for {@code d}, 0 to 9, or {@link #OTHER}. */
    public static Key digit(int d) {
        return d >= 0 && d <= 9 ? valueOf("DIGIT_" + d) : OTHER;
    }
}
