package com.lia.mediaplayer.api.source;

/**
 * What the player wants from a resolver.
 *
 * @param maxHeight the tallest picture worth fetching, in lines
 * @param audioOnly the picture will not be shown, so a resolver should prefer a sound-only
 *     stream
 */
public record ResolveOptions(int maxHeight, boolean audioOnly) {

    public static final ResolveOptions DEFAULT = new ResolveOptions(720, false);

    public ResolveOptions {
        if (maxHeight < 144) {
            throw new IllegalArgumentException("maxHeight must be at least 144, was " + maxHeight);
        }
    }
}
