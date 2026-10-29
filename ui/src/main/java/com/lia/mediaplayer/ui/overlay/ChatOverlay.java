package com.lia.mediaplayer.ui.overlay;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.cache.AsyncCache;
import com.lia.mediaplayer.core.image.ImageCache;
import com.lia.mediaplayer.core.image.ImageTextures;
import com.lia.mediaplayer.core.link.ChatRewriter;
import com.lia.mediaplayer.core.link.SourceRegistry;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.UiHost;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.RenderContext;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What the mod adds to the chat itself: the preview of a picture label under the cursor, and
 * what a click on a label does. Only labels the {@link ChatRewriter} made count — a link the
 * player's filter refused, or a server's own clickable text, is left to the game.
 *
 * <p>A click plays the link: a picture opens a pinned window, a video joins the front video
 * window's queue, a sound the front audio bar's. Shift opens a window of its own instead, Alt
 * plays a video as sound only.
 */
public final class ChatOverlay {

    /** Gap between the cursor and the preview. */
    static final int CURSOR_OFFSET = 8;

    private static final int FRAME = 2;
    private static final int EDGE = 2;

    /** What a click on a label does. */
    @FunctionalInterface
    public interface Target {
        void play(MediaRef ref, boolean audioOnly, boolean newWindow);
    }

    private final UiHost host;
    private final ChatRewriter chat;
    private final SourceRegistry sources;
    private final ImageCache images;
    private final Target target;

    public ChatOverlay(UiHost host, ChatRewriter chat, SourceRegistry sources, ImageCache images, Target target) {
        this.host = host;
        this.chat = chat;
        this.sources = sources;
        this.images = images;
        this.target = target;
    }

    /** The label under {@code (x, y)}, if it is one the mod made. */
    public Optional<MediaRef> labelAt(double x, double y) {
        return host.chatLinkAt(x, y).filter(chat::isLabelled).flatMap(sources::match);
    }

    /**
     * Draws the preview of the picture label under the cursor, above everything, unless that
     * picture is already pinned (the window is its preview).
     *
     * @param pinned whether a window already shows a link
     */
    public void render(Canvas canvas, RenderContext ctx, Predicate<String> pinned) {
        double px = ctx.pointerX();
        double py = ctx.pointerY();
        if (Double.isNaN(px)) {
            return;
        }
        Optional<MediaRef> label = labelAt(px, py).filter(ref -> ref.kind() == MediaKind.IMAGE);
        if (label.isEmpty() || pinned.test(label.get().url())) {
            return;
        }
        int x = (int) px;
        int y = (int) py;
        switch (images.get(label.get())) {
            case AsyncCache.Entry.Ready<ImageTextures> ready -> drawPreview(canvas, ctx, ready.value(), x, y);
            case AsyncCache.Entry.Failed<ImageTextures> failed ->
                canvas.tooltip(List.of(Text.translatable(Keys.IMAGE_FAILED)), x, y);
            case AsyncCache.Entry.Loading<ImageTextures> loading ->
                canvas.tooltip(List.of(Text.translatable(Keys.IMAGE_LOADING)), x, y);
        }
    }

    private static void drawPreview(Canvas canvas, RenderContext ctx, ImageTextures image, int x, int y) {
        TextureRef frame = image.frameAt(ctx.nanos());
        if (frame == null) {
            return;
        }
        Rect area = previewArea(canvas.width(), canvas.height(), image.width(), image.height(), x, y);
        canvas.nextLayer();
        canvas.fill(area.inset(-FRAME), ctx.color(Role.PANEL_BG));
        canvas.texture(frame, area.x(), area.y(), area.width(), area.height());
    }

    /**
     * Where the preview of a {@code w × h} picture goes for a cursor at {@code (x, y)}: scaled
     * down, never up, to half the screen, above and right of the cursor, kept on screen.
     */
    static Rect previewArea(int screenWidth, int screenHeight, int w, int h, int x, int y) {
        double scale = Math.min(
                1, Math.min(Math.max(32, screenWidth / 2) / (double) w, Math.max(32, screenHeight / 2) / (double) h));
        int width = Math.max(1, (int) Math.round(w * scale));
        int height = Math.max(1, (int) Math.round(h * scale));
        int px = clamp(x + CURSOR_OFFSET, EDGE, screenWidth - width - EDGE);
        int py = clamp(y - height - CURSOR_OFFSET, EDGE, screenHeight - height - EDGE);
        return new Rect(px, py, width, height);
    }

    /**
     * A press on the chat that no window took.
     *
     * @return whether it was on a label, which then plays; the chat must not see it
     */
    public boolean press(InputEvent.PointerDown down) {
        if (down.button() != InputEvent.PRIMARY_BUTTON) {
            return false;
        }
        Optional<MediaRef> label = labelAt(down.x(), down.y());
        if (label.isEmpty()) {
            return false;
        }
        target.play(label.get(), down.mods().alt(), down.mods().shift());
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
