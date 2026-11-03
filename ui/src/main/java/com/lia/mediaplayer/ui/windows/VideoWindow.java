package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextureRef;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.settings.WhenHidden;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Column;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Row;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.window.Placement;
import com.lia.mediaplayer.ui.window.Window;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A playback with its picture: the title strip with the heart, copy-link, open-in-browser,
 * theatre, hide and close; the picture; the transport bar under it; and, when asked for, the
 * queue to the right of both. On the HUD it shows the picture only.
 *
 * <p>When the first frame arrives the window takes the picture's shape once, keeping its
 * width, unless it was resized by then; after that the picture is letterboxed. Theatre mode
 * makes the picture as large as the screen allows, and gives the window back its frame after.
 *
 * <p>Hidden, it keeps decoding by default. With the "sound only" setting, after {@value
 * #HIDDEN_SOUND_ONLY_TICKS} ticks hidden the playback drops its picture; showing the window
 * brings it back at the current position.
 */
public final class VideoWindow extends Window implements PlayerWindow {

    public static final String KIND = "video";

    static final int DEFAULT_PICTURE_WIDTH = 256;
    static final int DEFAULT_PICTURE_HEIGHT = 144;
    /** Five seconds: hiding a window for a moment does not restart its decoding. */
    static final int HIDDEN_SOUND_ONLY_TICKS = 100;

    private final PlaybackControl control;
    private final WindowServices services;
    private final VideoView view;
    private final TransportBar transport;
    private final QueuePanel queue;
    private final List<IconButton> titleButtons;
    private boolean shaped;
    private Rect openedAt = Rect.EMPTY;
    private @Nullable Rect beforeTheater;
    private int hiddenTicks;
    private boolean soundOnly;

    public VideoWindow(PlaybackControl control, WindowServices services) {
        super(Text.literal(""));
        this.control = control;
        this.services = services;
        CornerButtons.addonButtons(
                services.actions(), AddonActions.player(MediaKind.VIDEO, control), this::addTitleButton);
        titleButtons = List.of(
                addTitleButton(CornerButtons.favorite(control::currentRef, MediaKind.VIDEO, services)),
                addTitleButton(
                        CornerButtons.copy(control::currentRef, () -> MediaTime.shown(control.snapshot()), services)),
                addTitleButton(CornerButtons.browser(control::currentRef, services)),
                addTitleButton(new IconButton(Glyphs::theater, this::toggleTheater)
                        .activeWhen(this::inTheater)
                        .tooltip(Text.translatable(Keys.THEATER))),
                addTitleButton(CornerButtons.hide(this::hide)),
                addTitleButton(CornerButtons.close(this::close)));
        var body = setBody(new Row());
        var player = body.add(new Column(), 1);
        view = player.add(new VideoView(control, services.platform().textures()), 1);
        transport = player.add(new TransportBar(control, this::toggleQueue, this::isQueueOpen, true), 0, true);
        addPopup(transport.volumePopup());
        queue = body.add(new QueuePanel(control, services.titles(), true), 0, true);
        queue.setVisible(false);
    }

    @Override
    public PlaybackControl control() {
        return control;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Size preferredSize() {
        return new Size(DEFAULT_PICTURE_WIDTH + chromeWidth(), DEFAULT_PICTURE_HEIGHT + chromeHeight());
    }

    @Override
    public Size minimumSize() {
        return new Size(158 + chromeWidth(), chromeHeight() + 40);
    }

    /** Everything across that is not picture: the edges and the queue. */
    private int chromeWidth() {
        return 2 + (isQueueOpen() ? QueuePanel.WIDTH : 0);
    }

    /** Everything down that is not picture: the edges, the title strip and the transport bar. */
    private int chromeHeight() {
        return 2 + TITLE_BAR + TransportBar.HEIGHT;
    }

    @Override
    public boolean isQueueOpen() {
        return queue.isVisible();
    }

    @Override
    public void setQueueOpen(boolean open) {
        if (open == isQueueOpen()) {
            return;
        }
        queue.setVisible(open);
        if (isPlaced()) {
            int change = open ? QueuePanel.WIDTH : -QueuePanel.WIDTH;
            Rect grown = frame().withSize(frame().width() + change, frame().height());
            Rect area = screen();
            setFrame(area.isEmpty() ? grown : grown.clampInto(area));
        }
    }

    private void toggleQueue() {
        setQueueOpen(!isQueueOpen());
    }

    @Override
    public void toggleMute() {
        transport.toggleMute();
    }

    // ---- Theatre ------------------------------------------------------------

    public boolean inTheater() {
        return beforeTheater != null;
    }

    /**
     * The picture as large as the screen allows, centred; again, the frame from before.
     *
     * @return whether anything changed
     */
    public boolean toggleTheater() {
        Rect previous = beforeTheater;
        if (previous != null) {
            beforeTheater = null;
            setFrame(previous);
            return true;
        }
        Rect area = screen().inset(Placement.MARGIN);
        if (area.isEmpty()) {
            return false;
        }
        beforeTheater = frame();
        TextureRef picture = view.texture();
        int aspectWidth = picture == null ? DEFAULT_PICTURE_WIDTH : picture.width();
        int aspectHeight = picture == null ? DEFAULT_PICTURE_HEIGHT : picture.height();
        int chrome = chromeHeight();
        int side = chromeWidth();
        Rect fitted = area.inset(1, 0, side - 1, chrome).fit(aspectWidth, aspectHeight);
        Rect frame = new Rect(fitted.x() - 1, fitted.y(), fitted.width() + side, fitted.height() + chrome);
        setFrame(frame.clampInto(screen()));
        raise();
        return true;
    }

    @Override
    protected boolean remembersFrame() {
        return !inTheater();
    }

    // ---- Lifecycle ----------------------------------------------------------

    @Override
    public void tick() {
        // The engine closes every playback when the world is left; the window follows.
        if (control.isClosed()) {
            close();
            return;
        }
        hiddenTicks = isHidden() ? hiddenTicks + 1 : 0;
        boolean wantSoundOnly =
                hiddenTicks >= HIDDEN_SOUND_ONLY_TICKS && services.whenHidden().get() == WhenHidden.SOUND_ONLY;
        if (wantSoundOnly != soundOnly) {
            soundOnly = wantSoundOnly;
            control.setVideoEnabled(!wantSoundOnly);
        }
    }

    @Override
    protected void beforeLayout() {
        PlayerSnapshot snapshot = control.snapshot();
        setTitle(Text.literal(snapshot.title().or(control::currentUrl).orElse("")));
        view.update(snapshot);
        transport.update(snapshot);
        if (isQueueOpen()) {
            queue.update();
        }
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
        int pictureWidth = frame().width() - chromeWidth();
        int pictureHeight = (int) Math.round((double) pictureWidth * picture.height() / picture.width());
        setFrame(frame().withSize(frame().width(), pictureHeight + chromeHeight()));
    }

    /** Frames uploaded so far, for tests. */
    int uploads() {
        return view.uploads();
    }

    /** What a frame upload has taken lately, in nanoseconds: the debug overlay's figure. */
    public long uploadNanos() {
        return view.uploadNanos();
    }

    /** Whether the playback plays its sound alone because the window has been hidden a while. */
    public boolean soundOnly() {
        return soundOnly;
    }

    // The parts, for tests.

    VideoView view() {
        return view;
    }

    TransportBar transport() {
        return transport;
    }

    QueuePanel queue() {
        return queue;
    }

    List<IconButton> titleButtons() {
        return titleButtons;
    }

    @Override
    protected void onClosed() {
        control.close();
        view.dispose();
    }
}
