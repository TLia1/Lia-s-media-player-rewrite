package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Column;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.window.Window;
import java.util.List;

/**
 * A playback with its picture: the title strip with copy-link, open-in-browser and close, the
 * picture, and the transport bar. On the HUD it shows the picture only.
 *
 * <p>When the first frame arrives the window takes the picture's shape once, keeping its
 * width, unless it was resized by then; after that the picture is letterboxed.
 */
public final class VideoWindow extends Window {

    public static final String KIND = "video";

    static final int DEFAULT_PICTURE_WIDTH = 256;
    static final int DEFAULT_PICTURE_HEIGHT = 144;

    private final PlaybackControl control;
    private final VideoView view;
    private final TransportBar transport;
    private final IconButton copy;
    private final IconButton browser;
    private final IconButton closer;
    private boolean shaped;
    private Rect openedAt = Rect.EMPTY;

    public VideoWindow(PlaybackControl control, Platform platform) {
        super(Text.literal(""));
        this.control = control;
        copy = addTitleButton(
                new IconButton(Glyphs::copy, () -> control.currentUrl().ifPresent(platform.clipboard()::set))
                        .tooltip(Text.translatable(Keys.COPY_LINK)));
        browser = addTitleButton(
                new IconButton(Glyphs::externalLink, () -> control.currentUrl().ifPresent(platform::openInBrowser))
                        .tooltip(Text.translatable(Keys.OPEN_IN_BROWSER)));
        closer = addTitleButton(
                new IconButton(Glyphs::close, this::close).dangerous().tooltip(Text.translatable(Keys.CLOSE)));
        var body = setBody(new Column());
        view = body.add(new VideoView(control, platform.textures()), 1);
        transport = body.add(new TransportBar(control), 0, true);
    }

    public PlaybackControl control() {
        return control;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Size preferredSize() {
        return new Size(DEFAULT_PICTURE_WIDTH + 2, DEFAULT_PICTURE_HEIGHT + chromeHeight());
    }

    @Override
    public Size minimumSize() {
        return new Size(140, chromeHeight() + 40);
    }

    /** Everything that is not picture: the edges, the title strip and the transport bar. */
    private static int chromeHeight() {
        return 2 + TITLE_BAR + TransportBar.HEIGHT;
    }

    @Override
    public void tick() {
        // The engine closes every playback when the world is left; the window follows.
        if (control.isClosed()) {
            close();
        }
    }

    @Override
    protected void beforeLayout() {
        PlayerSnapshot snapshot = control.snapshot();
        setTitle(Text.literal(snapshot.title().or(control::currentUrl).orElse("")));
        view.update(snapshot);
        transport.update(snapshot);
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
        TextureRef picture = view.texture();
        if (shaped || picture == null) {
            return;
        }
        shaped = true;
        if (!frame().equals(openedAt)) {
            return;
        }
        int pictureWidth = frame().width() - 2;
        int pictureHeight = (int) Math.round((double) pictureWidth * picture.height() / picture.width());
        setFrame(frame().withSize(frame().width(), pictureHeight + chromeHeight()));
    }

    /** Frames uploaded so far, for tests. */
    int uploads() {
        return view.uploads();
    }

    // The parts, for tests.

    VideoView view() {
        return view;
    }

    TransportBar transport() {
        return transport;
    }

    List<IconButton> titleButtons() {
        return List.of(copy, browser, closer);
    }

    @Override
    protected void onClosed() {
        control.close();
        view.dispose();
    }
}
