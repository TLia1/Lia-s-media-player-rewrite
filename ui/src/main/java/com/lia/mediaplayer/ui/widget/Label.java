package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.ui.canvas.Canvas;
import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.theme.Role;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One line of text. Too long for its bounds, it is cut with an ellipsis; the cut is worked out
 * once per text and width, not every frame.
 */
public final class Label extends Widget {

    /** Where the text sits across the label's width. */
    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    private static final String ELLIPSIS = "…";

    private Text text;
    private Role color;
    private Align align = Align.LEFT;
    private boolean shadow;
    private int measuredWidth = -1;
    private @Nullable Text fitted;
    private int fittedFor = -1;
    private int fittedWidth;

    public Label(Text text, Role color) {
        this.text = text;
        this.color = color;
    }

    public Text text() {
        return text;
    }

    /** Changes the text; asks for a new layout only if its width changed. */
    public void setText(Text newText) {
        if (newText.equals(text)) {
            return;
        }
        text = newText;
        fitted = null;
        measuredWidth = -1;
        invalidate();
    }

    public Label color(Role newColor) {
        color = newColor;
        return this;
    }

    public Label align(Align newAlign) {
        align = newAlign;
        return this;
    }

    public Label shadow(boolean newShadow) {
        shadow = newShadow;
        return this;
    }

    @Override
    public Size measure(Constraints constraints) {
        if (measuredWidth < 0) {
            measuredWidth = constraints.text().width(text);
        }
        return new Size(
                Math.min(measuredWidth, constraints.maxWidth()),
                constraints.text().lineHeight());
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {
        Rect b = bounds();
        if (b.width() <= 0) {
            return;
        }
        TextMeasurer measurer = canvas.measurer();
        Text shown = fit(measurer, b.width());
        int x = switch (align) {
            case LEFT -> b.x();
            case CENTER -> b.x() + (b.width() - fittedWidth) / 2;
            case RIGHT -> b.right() - fittedWidth;
        };
        int y = b.y() + (b.height() - measurer.lineHeight() + 1) / 2;
        canvas.text(shown, x, y, ctx.color(color), shadow);
    }

    private Text fit(TextMeasurer measurer, int width) {
        Text cached = fitted;
        if (cached != null && fittedFor == width) {
            return cached;
        }
        int full = measurer.width(text);
        Text result;
        if (full <= width) {
            result = text;
            fittedWidth = full;
        } else {
            String resolved = measurer.resolve(text);
            String cut = ellipsize(measurer, resolved, width);
            result = Text.literal(cut);
            fittedWidth = measurer.width(cut);
        }
        fitted = result;
        fittedFor = width;
        return result;
    }

    /** The longest start of {@code s} that fits in {@code width} with an ellipsis after it. */
    static String ellipsize(TextMeasurer measurer, String s, int width) {
        int budget = width - measurer.width(ELLIPSIS);
        if (budget <= 0) {
            return "";
        }
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (measurer.width(s.substring(0, mid)) <= budget) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        // Never cut a surrogate pair in half.
        if (lo > 0 && Character.isHighSurrogate(s.charAt(lo - 1))) {
            lo--;
        }
        return s.substring(0, lo).stripTrailing() + ELLIPSIS;
    }

    @Override
    public String toString() {
        return "Label[" + Objects.toString(text) + "]";
    }
}
