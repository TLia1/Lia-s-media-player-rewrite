package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.image.ImageCache;
import com.lia.mediaplayer.core.image.ImageTextures;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.window.Window;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A pinned picture or GIF: the title strip with the heart, copy-link, open-in-browser, hide and
 * close, and the picture. There are no controls, so the wheel alone zooms. On the HUD it shows the picture
 * only.
 *
 * <p>Once the picture has loaded the window takes its shape once — at its own size, but no
 * more than half the screen either way — unless it was moved or resized by then. The pixels
 * live in the image cache, shared with the chat's hover preview, so the window owns nothing.
 */
public final class ImageWindow extends Window {

    public static final String KIND = "image";

    static final int DEFAULT_WIDTH = 200;
    static final int DEFAULT_HEIGHT = 112;

    private final MediaRef ref;
    private final ImageCache images;
    private final PictureView view;
    private boolean shaped;
    private Rect openedAt = Rect.EMPTY;

    public ImageWindow(MediaRef ref, ImageCache images, WindowServices services) {
        super(Text.literal(titleOf(ref.url())));
        this.ref = ref;
        this.images = images;
        Supplier<Optional<MediaRef>> current = () -> Optional.of(ref);
        addTitleButton(CornerButtons.favorite(current, MediaKind.IMAGE, services));
        addTitleButton(CornerButtons.copy(current, () -> Duration.ZERO, services));
        addTitleButton(CornerButtons.browser(current, services));
        addTitleButton(CornerButtons.hide(this::hide));
        addTitleButton(CornerButtons.close(this::close));
        view = setBody(new PictureView(() -> images.retry(ref)));
    }

    public MediaRef ref() {
        return ref;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Size preferredSize() {
        return new Size(DEFAULT_WIDTH + 2, DEFAULT_HEIGHT + chromeHeight());
    }

    @Override
    public Size minimumSize() {
        return new Size(60, chromeHeight() + 24);
    }

    @Override
    protected boolean wheelZooms() {
        return true;
    }

    private static int chromeHeight() {
        return 2 + TITLE_BAR;
    }

    @Override
    protected void beforeLayout() {
        view.update(images.get(ref));
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        super.render(canvas, ctx);
        takeShapeOnce();
    }

    @Override
    public void renderHud(Canvas canvas, RenderContext ctx) {
        view.renderPicture(canvas, ctx);
        canvas.outline(view.bounds(), ctx.color(Role.BORDER));
        takeShapeOnce();
    }

    private void takeShapeOnce() {
        if (openedAt.isEmpty()) {
            openedAt = frame();
        }
        Optional<ImageTextures> image = view.image();
        if (shaped || image.isEmpty()) {
            return;
        }
        shaped = true;
        Rect screen = screen();
        if (!frame().equals(openedAt) || screen.isEmpty()) {
            return;
        }
        int w = image.get().width();
        int h = image.get().height();
        double scale = Math.min(1, Math.min(screen.width() / 2.0 / w, screen.height() / 2.0 / h));
        int width = Math.max(1, (int) Math.round(w * scale)) + 2;
        int height = Math.max(1, (int) Math.round(h * scale)) + chromeHeight();
        // Grow away from the nearest side of the screen, so a window in a corner stays in it.
        int x = frame().centerX() > screen.centerX() ? frame().right() - width : frame().x();
        int y = frame().centerY() > screen.centerY() ? frame().bottom() - height : frame().y();
        Rect shapedFrame = new Rect(x, y, width, height);
        setFrame(shapedFrame.clampInto(screen));
    }

    /** The last segment of the link's path, or the host: what a picture is usually called. */
    static String titleOf(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path != null) {
                List<String> segments = Arrays.stream(path.split("/"))
                        .filter(segment -> !segment.isBlank())
                        .toList();
                if (!segments.isEmpty()) {
                    return segments.getLast();
                }
            }
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (URISyntaxException e) {
            return url;
        }
    }

    // For tests.

    PictureView view() {
        return view;
    }
}
