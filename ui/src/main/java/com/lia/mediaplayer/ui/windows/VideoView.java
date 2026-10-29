package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.platform.TextureService;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.video.VideoFrame;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.Container;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import org.jspecify.annotations.Nullable;

/**
 * The picture of a playback: the newest due frame, uploaded to one texture and drawn
 * letterboxed in the view, with a spinner while loading and the error panel after a failure.
 *
 * <p>At most one upload per frame, on the client thread, during the draw. The texture is
 * created at the frame's size and replaced when the size changes (an adaptive stream, the
 * next track); the old one is released, and the texture service frees it between frames. Until
 * the first frame of a new track arrives the last picture stays.
 */
final class VideoView extends Container {

    /** A spinner for a load shorter than this would only flicker. */
    private static final long SPINNER_DELAY_NANOS = 200_000_000L;

    private static final int SPINNER_RADIUS = 6;

    private final PlaybackControl control;
    private final TextureService textures;
    private final ErrorPanel error;
    private PlayerSnapshot snapshot = PlayerSnapshot.initial();
    private @Nullable TextureRef texture;
    private long busySince = -1;
    private int uploads;

    VideoView(PlaybackControl control, TextureService textures) {
        this.control = control;
        this.textures = textures;
        this.error = addChild(new ErrorPanel(control::retry));
        error.setVisible(false);
    }

    void update(PlayerSnapshot newSnapshot) {
        snapshot = newSnapshot;
        if (newSnapshot.state() instanceof PlayerState.Failed failed) {
            error.show(failed.error());
            error.setVisible(true);
        } else {
            error.setVisible(false);
        }
    }

    /** The size of the picture shown, or {@code null} before the first frame. */
    @Nullable
    TextureRef texture() {
        return texture;
    }

    int uploads() {
        return uploads;
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(64, 36);
    }

    @Override
    public void layout(Rect newBounds, Constraints constraints) {
        super.layout(newBounds, constraints);
        error.layout(newBounds, constraints);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        renderPicture(canvas, ctx);
        if (MediaTime.busy(snapshot)) {
            if (busySince < 0) {
                busySince = ctx.nanos();
            }
            if (ctx.nanos() - busySince >= SPINNER_DELAY_NANOS) {
                Rect b = bounds();
                Glyphs.spinner(canvas, b.centerX(), b.centerY(), SPINNER_RADIUS, ctx.nanos(), ctx.color(Role.SPINNER));
            }
        } else {
            busySince = -1;
        }
        super.render(canvas, ctx);
    }

    /** The picture alone, without spinner or error: what the HUD shows. */
    void renderPicture(Canvas canvas, RenderContext ctx) {
        pullFrame();
        Rect b = bounds();
        canvas.fill(b, ctx.color(Role.PLACEHOLDER));
        TextureRef shown = texture;
        if (shown != null) {
            Rect picture = b.fit(shown.width(), shown.height());
            canvas.texture(shown, picture.x(), picture.y(), picture.width(), picture.height());
        }
    }

    private void pullFrame() {
        VideoFrame frame = control.takeFrame();
        if (frame == null) {
            return;
        }
        try {
            TextureRef current = texture;
            if (current == null || !current.hasSize(frame.width(), frame.height())) {
                if (current != null) {
                    textures.release(current);
                }
                current = textures.create("video", frame.width(), frame.height());
                texture = current;
            }
            if (textures.upload(current, frame.buffer())) {
                uploads++;
            }
        } finally {
            control.releaseFrame(frame);
        }
    }

    /** Releases the texture; the view draws the placeholder until the next frame. */
    void dispose() {
        TextureRef current = texture;
        texture = null;
        if (current != null) {
            textures.release(current);
        }
    }
}
