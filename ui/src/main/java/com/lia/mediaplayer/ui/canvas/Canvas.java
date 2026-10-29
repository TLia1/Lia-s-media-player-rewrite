package com.lia.mediaplayer.ui.canvas;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.core.platform.TextMeasurer;
import com.lia.mediaplayer.core.platform.TextureRef;
import java.util.List;

/**
 * Where the UI draws, in GUI pixels with the origin at the top left. The platform implements
 * it over the game's own GUI drawing for one frame; tests record the calls.
 *
 * <p>Everything the toolkit shows is built from these few calls — rounded corners, icons and
 * bars are rectangles — so nothing above the platform needs a version guard.
 */
public interface Canvas {

    int width();

    int height();

    /** Fills {@code [x0, x1) × [y0, y1)}. Nothing is drawn for an empty rectangle. */
    void fill(int x0, int y0, int x1, int y1, int argb);

    /** Draws the whole of {@code texture} stretched into the rectangle at {@code (x, y)}. */
    void texture(TextureRef texture, int x, int y, int w, int h);

    /** Draws {@code text} on one line with its top-left corner at {@code (x, y)}. */
    void text(Text text, int x, int y, int argb, boolean shadow);

    /** Measures text in the font {@link #text} draws with. */
    TextMeasurer measurer();

    /** Restricts drawing to the rectangle, intersected with the current clip, until {@link #popClip}. */
    void pushClip(int x0, int y0, int x1, int y1);

    void popClip();

    /** Everything drawn after this sits above everything drawn before it, text included. */
    void nextLayer();

    /** A tooltip at the cursor, drawn above everything else in the frame. */
    void tooltip(List<Text> lines, int x, int y);

    default void fill(Rect rect, int argb) {
        fill(rect.x(), rect.y(), rect.right(), rect.bottom(), argb);
    }

    /** A one-pixel outline just inside {@code rect}. */
    default void outline(Rect rect, int argb) {
        int x0 = rect.x();
        int y0 = rect.y();
        int x1 = rect.right();
        int y1 = rect.bottom();
        fill(x0, y0, x1, y0 + 1, argb);
        fill(x0, y1 - 1, x1, y1, argb);
        fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
        fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }

    default void pushClip(Rect rect) {
        pushClip(rect.x(), rect.y(), rect.right(), rect.bottom());
    }
}
