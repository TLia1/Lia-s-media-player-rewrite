package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.media.RepeatMode;
import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.playback.PlayerSnapshot;
import com.lia.mediaplayer.core.playback.PlayerState;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.icon.Glyphs;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.IconButton;
import com.lia.mediaplayer.ui.widget.Label;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Row;
import com.lia.mediaplayer.ui.widget.SeekBar;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.Slider;
import java.time.Duration;

/**
 * The controls under a player, shared by the video window and the audio bar: play/pause (or
 * retry after a failure), next, the time, the seek bar, mute and volume, loop and shuffle.
 *
 * <p>The window hands it a fresh snapshot each frame through {@link #update}; the controls
 * read that, so one frame never shows two different states.
 */
final class TransportBar extends Row {

    static final int HEIGHT = 16;
    private static final int VOLUME_WIDTH = 30;

    private final PlaybackControl control;
    private final Label time;
    private PlayerSnapshot snapshot = PlayerSnapshot.initial();
    private float unmutedVolume = 1;
    private long shownSecond = -1;
    private long shownDuration = -2;
    private int shownQueued = -1;
    private boolean shownLive;
    private boolean shownLoading;
    /** The muted cross has a colour of its own, read from the theme of the frame being drawn. */
    private int mutedColor;

    TransportBar(PlaybackControl control) {
        this.control = control;
        padding(3, 0).gap(2);
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
        add(new IconButton(
                        (c, x, y, color) -> Glyphs.speaker(c, x, y, control.volume() <= 0, color, mutedColor),
                        this::toggleMute)
                .tooltip(() -> Text.translatable(control.volume() <= 0 ? Keys.UNMUTE : Keys.MUTE)));
        add(new Slider(
                VOLUME_WIDTH,
                control::volume,
                value -> control.setVolume((float) value),
                value -> Text.translatable(Keys.VOLUME, Math.round(value * 100))));
        add(new IconButton(
                        (c, x, y, color) -> Glyphs.loop(c, x, y, control.repeat() == RepeatMode.ONE, color),
                        control::cycleRepeat)
                .activeWhen(() -> control.repeat() != RepeatMode.OFF)
                .tooltip(() -> Text.translatable(
                        switch (control.repeat()) {
                            case OFF -> Keys.LOOP_OFF;
                            case ALL -> Keys.LOOP_ALL;
                            case ONE -> Keys.LOOP_ONE;
                        })));
        add(new IconButton(Glyphs::shuffle, control::toggleShuffle)
                .activeWhen(control::shuffle)
                .tooltip(() -> Text.translatable(control.shuffle() ? Keys.SHUFFLE_ON : Keys.SHUFFLE_OFF)));
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
    public void render(Canvas canvas, RenderContext ctx) {
        mutedColor = ctx.color(Role.MUTED);
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

    private void toggleMute() {
        float volume = control.volume();
        if (volume > 0) {
            unmutedVolume = volume;
            control.setVolume(0);
        } else {
            control.setVolume(unmutedVolume > 0 ? unmutedVolume : 1);
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
