package com.lia.mediaplayer.ui.canvas;

/**
 * An axis-aligned rectangle in GUI pixels: {@code [x, x + width) × [y, y + height)}. A negative
 * size is clamped to zero.
 */
public record Rect(int x, int y, int width, int height) {

    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public Rect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public static Rect ofEdges(int x0, int y0, int x1, int y1) {
        return new Rect(x0, y0, x1 - x0, y1 - y0);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public Rect inset(int amount) {
        return inset(amount, amount, amount, amount);
    }

    public Rect inset(int left, int top, int right, int bottom) {
        return new Rect(x + left, y + top, width - left - right, height - top - bottom);
    }

    public Rect withX(int newX) {
        return new Rect(newX, y, width, height);
    }

    public Rect withY(int newY) {
        return new Rect(x, newY, width, height);
    }

    public Rect withSize(int newWidth, int newHeight) {
        return new Rect(x, y, newWidth, newHeight);
    }

    public Rect translate(int dx, int dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    public Rect intersect(Rect other) {
        int x0 = Math.max(x, other.x);
        int y0 = Math.max(y, other.y);
        int x1 = Math.min(right(), other.right());
        int y1 = Math.min(bottom(), other.bottom());
        return x1 <= x0 || y1 <= y0 ? new Rect(x0, y0, 0, 0) : ofEdges(x0, y0, x1, y1);
    }

    public boolean intersects(Rect other) {
        return !intersect(other).isEmpty();
    }

    /**
     * The largest rectangle of aspect {@code aspectWidth : aspectHeight} that fits inside this
     * one, centred: where a picture goes, letterboxed.
     */
    public Rect fit(int aspectWidth, int aspectHeight) {
        if (aspectWidth <= 0 || aspectHeight <= 0 || isEmpty()) {
            return this;
        }
        long byWidth = (long) width * aspectHeight;
        long byHeight = (long) height * aspectWidth;
        int w;
        int h;
        if (byWidth <= byHeight) {
            w = width;
            h = (int) Math.max(1, Math.round((double) width * aspectHeight / aspectWidth));
        } else {
            h = height;
            w = (int) Math.max(1, Math.round((double) height * aspectWidth / aspectHeight));
        }
        return new Rect(x + (width - w) / 2, y + (height - h) / 2, w, h);
    }

    /** This rectangle moved, not resized, so that it lies inside {@code area} as far as it can. */
    public Rect clampInto(Rect area) {
        int nx = Math.max(area.x, Math.min(x, area.right() - width));
        int ny = Math.max(area.y, Math.min(y, area.bottom() - height));
        return new Rect(nx, ny, width, height);
    }
}
