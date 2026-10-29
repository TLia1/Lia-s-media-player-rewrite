package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.platform.Platform;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.window.Window;
import java.util.List;
import java.util.Optional;

/**
 * Sound without a picture, in a compact bar: the title strip and the transport. It keeps its
 * height; only its width can change. A failure shows in the title, with the advice in its
 * tooltip, and the play button turns into Retry.
 */
public final class AudioBar extends Window {

    public static final String KIND = "audio";

    static final int DEFAULT_WIDTH = 260;

    private final PlaybackControl control;
    private final TransportBar transport;
    private PlayerSnapshot snapshot = PlayerSnapshot.initial();

    public AudioBar(PlaybackControl control, Platform platform) {
        super(Text.literal(""));
        this.control = control;
        addTitleButton(new IconButton(Glyphs::copy, () -> control.currentUrl().ifPresent(platform.clipboard()::set))
                .tooltip(Text.translatable(Keys.COPY_LINK)));
        addTitleButton(
                new IconButton(Glyphs::externalLink, () -> control.currentUrl().ifPresent(platform::openInBrowser))
                        .tooltip(Text.translatable(Keys.OPEN_IN_BROWSER)));
        addTitleButton(new IconButton(Glyphs::close, this::close).dangerous().tooltip(Text.translatable(Keys.CLOSE)));
        transport = setBody(new TransportBar(control));
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
        return new Size(DEFAULT_WIDTH, height());
    }

    @Override
    public Size minimumSize() {
        return new Size(180, height());
    }

    private static int height() {
        return 2 + TITLE_BAR + TransportBar.HEIGHT;
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
        // Only the width can change; a drag on the grip that also pulled the height is undone.
        if (frame().height() != height()) {
            setFrame(frame().withSize(frame().width(), height()));
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

    @Override
    protected void onClosed() {
        control.close();
    }
}
