package com.lia.mediaplayer.ui.windows;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.diag.PlaybackError;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.text.Keys;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.theme.Role;
import com.lia.mediaplayer.ui.widget.Constraints;
import com.lia.mediaplayer.ui.widget.Container;
import com.lia.mediaplayer.ui.widget.RenderContext;
import com.lia.mediaplayer.ui.widget.Size;
import com.lia.mediaplayer.ui.widget.TextButton;
import com.lia.mediaplayer.ui.widget.TextWrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Why a playback failed, over its picture: the cause, the advice for it, and Retry. The tools'
 * own message is in the tooltip, for the curious and for bug reports.
 */
final class ErrorPanel extends Container {

    private static final int PAD = 5;
    private static final int MAX_DETAIL_LINES = 8;
    private static final int MAX_DETAIL_CHARS = 90;

    private final TextButton retry;
    private @Nullable PlaybackError error;
    private List<String> hintLines = List.of();
    private int wrappedFor = -1;

    ErrorPanel(Runnable onRetry) {
        retry = addChild(new TextButton(Text.translatable(Keys.RETRY), onRetry));
    }

    void show(PlaybackError newError) {
        if (!newError.equals(error)) {
            error = newError;
            wrappedFor = -1;
        }
    }

    @Override
    public Size measure(Constraints constraints) {
        return new Size(constraints.maxWidth(), constraints.maxHeight());
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
        PlaybackError shown = error;
        if (shown == null) {
            return;
        }
        Rect b = bounds();
        canvas.fill(b, ctx.color(Role.PANEL_BG));
        TextMeasurer measurer = canvas.measurer();
        int line = measurer.lineHeight() + 1;
        int width = b.width() - 2 * PAD;
        if (wrappedFor != width) {
            hintLines = TextWrap.lines(
                    measurer, measurer.resolve(Text.translatable(shown.cause().hintKey())), width);
            wrappedFor = width;
        }
        int y = b.y() + PAD;
        int bottom = retry.bounds().y() - 2;
        Text title = Text.translatable(shown.cause().messageKey());
        canvas.text(title, b.x() + (b.width() - measurer.width(title)) / 2, y, ctx.color(Role.DANGER), true);
        y += line + 2;
        for (String hint : hintLines) {
            if (y + line > bottom) {
                break;
            }
            canvas.text(Text.literal(hint), b.x() + PAD, y, ctx.color(Role.TEXT_SUBTLE), false);
            y += line;
        }
        super.render(canvas, ctx);
    }

    @Override
    public Optional<List<Text>> tooltip(double x, double y) {
        PlaybackError shown = error;
        if (shown == null || shown.detail().isBlank()) {
            return Optional.empty();
        }
        var lines = new ArrayList<Text>();
        shown.detail()
                .lines()
                .limit(MAX_DETAIL_LINES)
                .forEach(l -> lines.add(
                        Text.literal(l.length() > MAX_DETAIL_CHARS ? l.substring(0, MAX_DETAIL_CHARS) + "…" : l)));
        return Optional.of(lines);
    }
}
