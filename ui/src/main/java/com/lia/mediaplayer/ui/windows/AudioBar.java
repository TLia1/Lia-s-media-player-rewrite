package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.MediaKind;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.widget.Column;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.window.Window;
import java.util.List;
import java.util.Optional;

/**
 * Sound without a picture, in a compact bar: the title strip, the transport and, when asked
 * for, the queue under it. It keeps its height; only its width can change. A failure shows in
 * the title, with the advice in its tooltip, and the play button turns into Retry.
 */
public final class AudioBar extends Window implements PlayerWindow {

    public static final String KIND = "audio";

    static final int DEFAULT_WIDTH = 270;

    private final PlaybackControl control;
    private final TransportBar transport;
    private final QueuePanel queue;
    private PlayerSnapshot snapshot = PlayerSnapshot.initial();

    public AudioBar(PlaybackControl control, WindowServices services) {
        super(Text.literal(""));
        this.control = control;
        addTitleButton(CornerButtons.favorite(control::currentRef, MediaKind.AUDIO, services));
        addTitleButton(CornerButtons.copy(control::currentRef, () -> MediaTime.shown(control.snapshot()), services));
        addTitleButton(CornerButtons.browser(control::currentRef, services));
        addTitleButton(CornerButtons.hide(this::hide));
        addTitleButton(CornerButtons.close(this::close));
        var body = setBody(new Column());
        transport = body.add(new TransportBar(control, this::toggleQueue, this::isQueueOpen), 0, true);
        queue = body.add(new QueuePanel(control, services.titles()), 0, true);
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
        return new Size(DEFAULT_WIDTH, height());
    }

    @Override
    public Size minimumSize() {
        return new Size(200, height());
    }

    private int height() {
        return 2 + TITLE_BAR + TransportBar.HEIGHT + (isQueueOpen() ? QueuePanel.HEIGHT : 0);
    }

    @Override
    public boolean isQueueOpen() {
        return queue.isVisible();
    }

    @Override
    public void setQueueOpen(boolean open) {
        queue.setVisible(open);
    }

    private void toggleQueue() {
        setQueueOpen(!isQueueOpen());
    }

    @Override
    public void toggleMute() {
        transport.toggleMute();
    }

    @Override
    public void tick() {
        if (control.isClosed()) {
            close();
        }
    }

    @Override
    protected void beforeLayout() {
        snapshot = control.snapshot();
        Text title = snapshot.state() instanceof PlayerState.Failed failed
                ? Text.translatable(failed.error().cause().messageKey())
                : Text.literal(snapshot.title().or(control::currentUrl).orElse(""));
        setTitle(title);
        transport.update(snapshot);
        if (isQueueOpen()) {
            queue.update();
        }
        // Only the width can change; a drag on the grip that also pulled the height is undone,
        // and opening the queue grows the bar by its height.
        if (isPlaced() && frame().height() != height()) {
            Rect sized = frame().withSize(frame().width(), height());
            setFrame(screen().isEmpty() ? sized : sized.clampInto(screen()));
        }
    }

    @Override
    public Optional<List<Text>> tooltip(double x, double y) {
        if (snapshot.state() instanceof PlayerState.Failed failed && y < frame().y() + TITLE_BAR) {
            PlaybackError error = failed.error();
            return Optional.of(List.of(
                    Text.translatable(error.cause().messageKey()),
                    Text.translatable(error.cause().hintKey())));
        }
        return Optional.empty();
    }

    QueuePanel queue() {
        return queue;
    }

    @Override
    protected void onClosed() {
        control.close();
    }
}
