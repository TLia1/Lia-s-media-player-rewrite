package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.InputContext;
import com.lia.mediaplayer.ui.widget.Label;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Row;
import com.lia.mediaplayer.ui.widget.SeekBar;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Widget;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The controls under a player, shared by the video window and the audio bar: play/pause (or
 * retry after a failure), previous, next, the time, the seek bar, mute, loop, shuffle and the
 * queue panel's switch. Hovering the speaker raises the volume bar above it, as in v2: the
 * window holds that bar as a {@linkplain #volumePopup popup}, so it can reach over the picture
 * or past the top of the audio bar.
 *
 * <p>The window hands it a fresh snapshot each frame through {@link #update}; the controls
 * read that, so one frame never shows two different states.
 *
 * <p>When the bar is too narrow for everything, shuffle, loop, previous and the time give way,
 * in that order, so the rest stays inside the bar with room to seek.
 */
final class TransportBar extends Row {

    static final int HEIGHT = 16;
    private static final int PADDING = 3;
    private static final int GAP = 2;

    private final PlaybackControl control;
    private final Label time;
    private final IconButton speaker;
    private final VolumePopup volumePopup;
    /** The controls that give way when the bar is narrow, first to go first. */
    private final List<Widget> optional;

    private PlayerSnapshot snapshot = PlayerSnapshot.initial();
    private float unmutedVolume = 1;
    private long shownSecond = -1;
    private long shownDuration = -2;
    private int shownQueued = -1;
    private boolean shownLive;
    private boolean shownLoading;
    /** The muted cross has a colour of its own, read from the theme of the frame being drawn. */
    private int mutedColor;

    TransportBar(PlaybackControl control, Runnable toggleQueue, BooleanSupplier queueOpen) {
        this.control = control;
        padding(PADDING, 0).gap(GAP);
        add(new IconButton(
                        (c, x, y, color) -> {
                            if (failed()) {
                                Glyphs.retry(c, x, y, color);
                            } else {
                                Glyphs.playPause(c, x, y, MediaTime.running(snapshot), color);
                            }
                        },
                        this::playOrRetry)
                .tooltip(() -> Text.translatable(
                        failed() ? Keys.RETRY : MediaTime.running(snapshot) ? Keys.PAUSE : Keys.PLAY)));
        var previous = add(new IconButton(Glyphs::previous, control::previous)
                .enabledWhen(control::hasPrevious)
                .tooltip(Text.translatable(Keys.PREVIOUS)));
        add(new IconButton(Glyphs::next, control::next)
                .enabledWhen(control::hasNext)
                .tooltip(Text.translatable(Keys.NEXT)));
        time = add(new Label(Text.literal(""), Role.TEXT_SUBTLE));
        add(
                new SeekBar(
                        () -> MediaTime.fraction(snapshot),
                        () -> MediaTime.seekable(snapshot),
                        this::seekTo,
                        fraction -> Text.literal(MediaTime.format(at(fraction)))),
                1);
        speaker = add(new IconButton(
                        (c, x, y, color) -> Glyphs.speaker(c, x, y, control.volume() <= 0, color, mutedColor),
                        this::toggleMute)
                .tooltip(() -> Text.translatable(control.volume() <= 0 ? Keys.UNMUTE : Keys.MUTE)));
        volumePopup = new VolumePopup(control);
        var loop = add(new IconButton(
                        (c, x, y, color) -> Glyphs.loop(c, x, y, control.repeat() == RepeatMode.ONE, color),
                        control::cycleRepeat)
                .activeWhen(() -> control.repeat() != RepeatMode.OFF)
                .tooltip(() -> Text.translatable(
                        switch (control.repeat()) {
                            case OFF -> Keys.LOOP_OFF;
                            case ALL -> Keys.LOOP_ALL;
                            case ONE -> Keys.LOOP_ONE;
                        })));
        var shuffle = add(new IconButton(Glyphs::shuffle, control::toggleShuffle)
                .activeWhen(control::shuffle)
                .tooltip(() -> Text.translatable(control.shuffle() ? Keys.SHUFFLE_ON : Keys.SHUFFLE_OFF)));
        add(new IconButton(Glyphs::queue, toggleQueue)
                .activeWhen(queueOpen)
                .tooltip(() -> Text.translatable(queueOpen.getAsBoolean() ? Keys.QUEUE_HIDE : Keys.QUEUE_SHOW)));
        optional = List.of(shuffle, loop, previous, time);
    }

    /** The volume bar that rises from the speaker; the window adds it as a popup. */
    VolumePopup volumePopup() {
        return volumePopup;
    }

    /** Mutes, or brings back the volume from before: the button and the shortcut. */
    void toggleMute() {
        float volume = control.volume();
        if (volume > 0) {
            unmutedVolume = volume;
            control.setVolume(0);
        } else {
            control.setVolume(unmutedVolume > 0 ? unmutedVolume : 1);
        }
    }

    /** The state to show this frame. Updates the time text when the second it shows changes. */
    void update(PlayerSnapshot newSnapshot) {
        snapshot = newSnapshot;
        boolean live = newSnapshot.duration().isEmpty();
        long second = MediaTime.shown(newSnapshot).toSeconds();
        long duration = newSnapshot.duration().map(Duration::toSeconds).orElse(-1L);
        int queued = control.queued();
        boolean loading =
                newSnapshot.state() instanceof PlayerState.Idle || newSnapshot.state() instanceof PlayerState.Resolving;
        if (second == shownSecond
                && duration == shownDuration
                && queued == shownQueued
                && live == shownLive
                && loading == shownLoading) {
            return;
        }
        shownSecond = second;
        shownDuration = duration;
        shownQueued = queued;
        shownLive = live;
        shownLoading = loading;
        String suffix = queued > 0 ? "  +" + queued : "";
        if (loading) {
            time.setText(Text.literal("–:––" + suffix));
        } else if (live) {
            time.setText(Text.translatable(Keys.TIME_LIVE, suffix));
        } else {
            time.setText(Text.literal(MediaTime.format(MediaTime.shown(newSnapshot)) + " / "
                    + MediaTime.format(newSnapshot.duration().orElse(Duration.ZERO)) + suffix));
        }
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(super.measure(constraints).width(), HEIGHT);
    }

    @Override
    public void layout(Rect newBounds, Constraints constraints) {
        // Everything at its preferred size, the seek bar at its smallest; then hide controls
        // until that fits. Only a change of visibility asks for another layout.
        int needed = 2 * PADDING - GAP;
        for (Widget child : childList()) {
            needed += child.measure(constraints).width() + GAP;
        }
        for (Widget child : optional) {
            boolean fits = needed <= newBounds.width();
            if (!fits) {
                needed -= child.measure(constraints).width() + GAP;
            }
            child.setVisible(fits);
        }
        super.layout(newBounds, constraints);
        // The volume bar stands on the speaker, centred on it, with no gap the cursor could
        // fall through on its way up. At the top of the screen it hangs below instead.
        Rect button = speaker.bounds();
        int x = button.centerX() - VolumePopup.WIDTH / 2;
        int y = button.y() - VolumePopup.HEIGHT;
        if (y < 0) {
            y = button.bottom();
        }
        volumePopup.layout(new Rect(x, y, VolumePopup.WIDTH, VolumePopup.HEIGHT), constraints);
    }

    @Override
    public boolean onInput(InputEvent event, InputContext ctx) {
        // The wheel on the speaker steps the volume, as it does on the bar above it.
        if (event instanceof InputEvent.Scroll scroll && speaker.bounds().contains(scroll.x(), scroll.y())) {
            return volumePopup.onInput(event, ctx);
        }
        return super.onInput(event, ctx);
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        mutedColor = ctx.color(Role.MUTED);
        // Open while the cursor is on the speaker or the bar, or a drag on the bar goes on. The
        // window draws the bar after this, so it follows the cursor in the same frame.
        volumePopup.setVisible(volumePopup.isDragging()
                || ctx.hovered(speaker.bounds())
                || (volumePopup.isVisible() && ctx.hovered(volumePopup.bounds())));
        canvas.fill(bounds(), ctx.color(Role.CONTROL_BAR_BG));
        super.render(canvas, ctx);
    }

    private boolean failed() {
        return snapshot.state() instanceof PlayerState.Failed;
    }

    private void playOrRetry() {
        if (failed()) {
            control.retry();
        } else {
            control.togglePause();
        }
    }

    private void seekTo(double fraction) {
        control.seek(at(fraction));
    }

    private Duration at(double fraction) {
        long millis = snapshot.duration().map(Duration::toMillis).orElse(0L);
        return Duration.ofMillis(Math.round(millis * fraction));
    }
}
