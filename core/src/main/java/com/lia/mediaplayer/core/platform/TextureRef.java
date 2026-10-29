package com.lia.mediaplayer.core.platform;

/**
 * A GPU texture the platform created, named without the game's own types. The platform keeps
 * the map from {@code id} to its texture object; everything else only passes this around.
 *
 * @param id unique for the game session; never reused, so a stale reference can never draw a
 *     texture that took its place
 * @param width in pixels
 * @param height in pixels
 */
public record TextureRef(long id, int width, int height) {

    public TextureRef {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Texture size must be positive: " + width + "x" + height);
        }
    }

    public boolean hasSize(int w, int h) {
        return width == w && height == h;
    }
}
