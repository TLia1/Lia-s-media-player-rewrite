package com.lia.mediaplayer.core.image;

import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.platform.TextureService;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A decoded picture on its way to the GPU, then on it: one texture per frame. Frames are
 * uploaded a few at a time ({@link #upload}) so a long GIF never stalls a frame, and the
 * pixels on the heap are dropped as soon as they are uploaded. Client thread only, apart from
 * construction.
 */
public final class ImageTextures {

    private final int width;
    private final int height;
    private final long bytes;
    private final int[] frameEnds;
    private final int totalMillis;
    private @Nullable List<DecodedImage.Frame> pending;
    private final List<TextureRef> textures = new ArrayList<>();
    private boolean released;

    public ImageTextures(DecodedImage image) {
        this.width = image.width();
        this.height = image.height();
        this.bytes = image.bytes();
        this.pending = new ArrayList<>(image.frames());
        int frames = image.frames().size();
        this.frameEnds = new int[frames];
        int end = 0;
        for (int i = 0; i < frames; i++) {
            end += Math.max(0, image.frames().get(i).delayMillis());
            frameEnds[i] = end;
        }
        this.totalMillis = end;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** What the picture costs, in bytes of texture. */
    public long bytes() {
        return bytes;
    }

    public int frameCount() {
        return frameEnds.length;
    }

    /** Whether every frame is on the GPU, so the picture can be drawn. */
    public boolean isComplete() {
        return !released && pending == null;
    }

    /**
     * Uploads frames until about {@code budgetBytes} have gone, at least one.
     *
     * @return the bytes uploaded
     */
    public long upload(TextureService service, long budgetBytes) {
        List<DecodedImage.Frame> left = pending;
        if (left == null || released) {
            return 0;
        }
        long frameBytes = (long) width * height * 4;
        long spent = 0;
        while (!left.isEmpty() && (spent == 0 || spent + frameBytes <= budgetBytes)) {
            DecodedImage.Frame frame = left.removeFirst();
            TextureRef texture = service.create("image", width, height);
            service.upload(texture, frame.rgba());
            textures.add(texture);
            spent += frameBytes;
        }
        if (left.isEmpty()) {
            pending = null;
        }
        return spent;
    }

    /**
     * The frame to show at {@code nanos} on a wall clock: animations are timed from a shared
     * origin, so every view of the same GIF shows the same frame. {@code null} until complete.
     */
    public @Nullable TextureRef frameAt(long nanos) {
        if (!isComplete() || textures.isEmpty()) {
            return null;
        }
        if (textures.size() == 1 || totalMillis <= 0) {
            return textures.getFirst();
        }
        long t = Math.floorMod(nanos / 1_000_000L, (long) totalMillis);
        for (int i = 0; i < frameEnds.length; i++) {
            if (t < frameEnds[i]) {
                return textures.get(i);
            }
        }
        return textures.getLast();
    }

    /** Releases the textures; the picture cannot be drawn after this. */
    public void release(TextureService service) {
        released = true;
        pending = null;
        for (TextureRef texture : textures) {
            service.release(texture);
        }
        textures.clear();
    }
}
