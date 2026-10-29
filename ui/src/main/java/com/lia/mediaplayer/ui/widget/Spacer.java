package com.lia.mediaplayer.ui.widget;

import com.lia.mediaplayer.ui.canvas.Canvas;

/** Empty room: a fixed gap, or added with a flex weight, a push to the far end. */
public final class Spacer extends Widget {

    private final Size size;

    public Spacer() {
        this(0, 0);
    }

    public Spacer(int width, int height) {
        this.size = new Size(width, height);
    }

    @Override
    public Size measure(Constraints constraints) {
        return size;
    }

    @Override
    public void render(Canvas canvas, RenderContext ctx) {}
}
