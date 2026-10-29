package com.lia.mediaplayer.core.platform;

import java.nio.ByteBuffer;

/**
 * Creates and fills GPU textures. Client thread only.
 *
 * <p>This is where the texture lifetime rule lives. From Minecraft 26.1 a GUI frame is
 * extracted into commands and executed later, so a texture freed while a frame is being drawn
 * can still be named by a command of that frame. {@link #release} therefore never frees at
 * once: it queues, and the platform frees between frames. Callers drop their reference when
 * they release and need not think about it further.
 */
public interface TextureService {

    /** A new RGBA texture, transparent until the first {@link #upload}. */
    TextureRef create(String debugName, int width, int height);

    /**
     * Replaces the whole texture with {@code rgba}: {@code width × height × 4} bytes, row by
     * row, read from position 0 whatever the buffer's position. A buffer of the wrong size is
     * refused and the texture keeps its previous contents.
     *
     * @return whether the pixels reached the texture
     */
    boolean upload(TextureRef texture, ByteBuffer rgba);

    /** Frees the texture once no frame can still be drawing it. Releasing twice is harmless. */
    void release(TextureRef texture);
}
