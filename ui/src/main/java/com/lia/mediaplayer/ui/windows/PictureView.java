package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.image.ImageTextures;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.Container;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.TextButton;
import java.util.List;
import java.util.Optional;

/**
 * A picture from the image cache, letterboxed in the view: the current frame once every frame
 * is on the GPU, a spinner before, and the failure with Retry after. The cache owns the
 * textures, so the view owns nothing.
 */
final class PictureView extends Container {

    private static final long SPINNER_DELAY_NANOS = 200_000_000L;
    private static final int SPINNER_RADIUS = 6;
    private static final int PAD = 4;

    private final TextButton retry;
    private AsyncCache.Entry<ImageTextures> entry = new AsyncCache.Entry.Loading<>();
    private long loadingSince = -1;

    PictureView(Runnable onRetry) {
        retry = addChild(new TextButton(Text.translatable(Keys.RETRY), onRetry));
        retry.setVisible(false);
    }

    void update(AsyncCache.Entry<ImageTextures> newEntry) {
        entry = newEntry;
        retry.setVisible(newEntry instanceof AsyncCache.Entry.Failed);
    }

    /** The picture, once it can be drawn. */
    Optional<ImageTextures> image() {
        return entry instanceof AsyncCache.Entry.Ready<ImageTextures> ready
                ? Optional.of(ready.value())
                : Optional.empty();
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(32, 32);
    }

    @Override
    public void layout(Rect newBounds, Constraints constraints) {
        super.layout(newBounds, constraints);
        Size size = retry.measure(constraints);
        retry.layout(
                new Rect(
                        newBounds.centerX() - size.width() / 2,
                        newBounds.bottom() - PAD - size.height(),
                        size.width(),
                        size.height()),
                constraints);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        renderPicture(canvas, ctx);
        Rect b = bounds();
        if (entry instanceof AsyncCache.Entry.Loading<ImageTextures>) {
            if (loadingSince < 0) {
                loadingSince = ctx.nanos();
            }
            if (ctx.nanos() - loadingSince >= SPINNER_DELAY_NANOS) {
                Glyphs.spinner(canvas, b.centerX(), b.centerY(), SPINNER_RADIUS, ctx.nanos(), ctx.color(Role.SPINNER));
            }
        } else {
            loadingSince = -1;
        }
        if (entry instanceof AsyncCache.Entry.Failed<ImageTextures>) {
            TextMeasurer measurer = canvas.measurer();
            Text message = Text.translatable(Keys.IMAGE_FAILED);
            // Centred in the room above Retry.
            int y = b.y() + Math.max(0, (retry.bounds().y() - b.y() - measurer.lineHeight()) / 2);
            canvas.text(message, b.x() + (b.width() - measurer.width(message)) / 2, y, ctx.color(Role.DANGER), true);
        }
        super.render(canvas, ctx);
    }

    /** The picture alone: what the HUD shows. */
    void renderPicture(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        canvas.fill(b, ctx.color(Role.PLACEHOLDER));
        if (entry instanceof AsyncCache.Entry.Ready<ImageTextures> ready) {
            ImageTextures image = ready.value();
            TextureRef frame = image.frameAt(ctx.nanos());
            if (frame != null) {
                Rect picture = b.fit(image.width(), image.height());
                canvas.texture(frame, picture.x(), picture.y(), picture.width(), picture.height());
            }
        }
    }

    @Override
    public Optional<List<Text>> tooltip(double x, double y) {
        if (entry instanceof AsyncCache.Entry.Failed<ImageTextures> failed) {
            String reason = String.valueOf(failed.cause().getMessage());
            return Optional.of(List.of(Text.translatable(Keys.IMAGE_FAILED), Text.literal(reason)));
        }
        return Optional.empty();
    }
}
