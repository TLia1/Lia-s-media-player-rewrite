package com.lia.mediaplayer.ui.overlay;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.playback.PlayerStats;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.window.Window;
import com.lia.mediaplayer.ui.windows.AudioBar;
import com.lia.mediaplayer.ui.windows.PlayerWindow;
import com.lia.mediaplayer.ui.windows.VideoWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * The debug overlay (a setting, off by default): in the top-left corner, what the windows cost
 * the client thread each frame and the caches hold, and for each player its decoded size and
 * rate, how full its rings are, the frames it dropped, how late its picture is behind the sound,
 * what an upload takes and how often the sound or the picture ran dry. The counters live in the
 * engine and cost nothing to keep; the text is rebuilt four times a second, not every frame.
 */
public final class DebugOverlay {

    static final long REFRESH_NANOS = 250_000_000L;
    private static final int MARGIN = 4;
    private static final int PAD = 3;
    /** How much of each new frame's time goes into the average shown. */
    private static final double SMOOTHING = 0.05;

    private final LongSupplier imageBytes;
    private final LongSupplier diskBytes;
    private double windowsNanos;
    private boolean measured;
    private List<Text> lines = List.of();
    private long builtAt = Long.MIN_VALUE;

    /**
     * @param imageBytes texture bytes the pictures hold
     * @param diskBytes bytes of thumbnails on disk
     */
    public DebugOverlay(LongSupplier imageBytes, LongSupplier diskBytes) {
        this.imageBytes = imageBytes;
        this.diskBytes = diskBytes;
    }

    /** What drawing the windows took this frame, uploads included. */
    public void recordWindows(long nanos) {
        windowsNanos = measured ? windowsNanos + (nanos - windowsNanos) * SMOOTHING : nanos;
        measured = true;
    }

    /** The average the overlay shows, in nanoseconds a frame. */
    public long windowsNanos() {
        return Math.round(windowsNanos);
    }

    public void render(Canvas canvas, RenderContext ctx, List<Window> windows) {
        if (ctx.nanos() - builtAt >= REFRESH_NANOS || builtAt == Long.MIN_VALUE) {
            lines = describe(windows);
            builtAt = ctx.nanos();
        }
        TextMeasurer measurer = canvas.measurer();
        int lineHeight = measurer.lineHeight() + 1;
        int width = 0;
        for (Text line : lines) {
            width = Math.max(width, measurer.width(line));
        }
        canvas.nextLayer();
        canvas.fill(
                new Rect(MARGIN, MARGIN, width + 2 * PAD, lines.size() * lineHeight + 2 * PAD - 1),
                ctx.color(Role.BANNER_BG));
        int y = MARGIN + PAD;
        for (int i = 0; i < lines.size(); i++) {
            canvas.text(lines.get(i), MARGIN + PAD, y, ctx.color(i == 0 ? Role.TEXT : Role.TEXT_SUBTLE), false);
            y += lineHeight;
        }
    }

    /** The lines of the overlay, for tests and for the render. */
    List<Text> describe(List<Window> windows) {
        var out = new ArrayList<Text>();
        out.add(Text.translatable(
                Keys.DEBUG_SUMMARY,
                millis(windowsNanos()),
                megabytes(imageBytes.getAsLong()),
                megabytes(diskBytes.getAsLong())));
        int number = 0;
        for (Window window : windows) {
            if (!(window instanceof PlayerWindow player)) {
                continue;
            }
            number++;
            PlayerStats stats = player.control().stats();
            String kind = window instanceof AudioBar ? "audio" : "video";
            out.add(Text.translatable(
                    Keys.DEBUG_PLAYER,
                    number,
                    kind,
                    stats.sessions(),
                    window.isHidden() ? Text.translatable(Keys.DEBUG_HIDDEN) : Text.literal("")));
            if (stats.decodeHeight() > 0) {
                long upload = window instanceof VideoWindow video ? video.uploadNanos() : 0;
                out.add(Text.translatable(
                        Keys.DEBUG_PICTURE,
                        stats.decodeHeight(),
                        format("%.0f", stats.fps()),
                        stats.framesBuffered() + "/" + stats.frameCapacity(),
                        stats.droppedFrames(),
                        format("%+.0f", stats.lateMicros() / 1000.0),
                        millis(upload),
                        stats.pictureUnderruns()));
            } else if (window instanceof VideoWindow) {
                out.add(Text.translatable(Keys.DEBUG_NO_PICTURE));
            }
            if (stats.soundCapacityMillis() > 0) {
                out.add(Text.translatable(
                        Keys.DEBUG_SOUND,
                        stats.soundBufferedMillis() + "/" + stats.soundCapacityMillis(),
                        stats.soundUnderruns()));
            }
        }
        return out;
    }

    private static String millis(long nanos) {
        return format("%.2f", nanos / 1e6);
    }

    private static String megabytes(long bytes) {
        return format("%.1f", bytes / (1024.0 * 1024.0));
    }

    private static String format(String pattern, Object value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}
