package com.lia.mediaplayer.ui.window;

/**
 * The pull a window feels while it is dragged: one axis at a time, one pure function.
 *
 * <p>A drag follows the raw cursor and lands a pixel or two off the edge it was aimed at.
 * {@link #axis} takes the coordinate the drag produced and the guides it could have meant —
 * the screen edges and centre, and the edges and centres of the other windows — and returns
 * the nearest within the threshold. Three lines of the moving box are candidates for each
 * guide (its leading edge, trailing edge and centre), which gives all three alignments people
 * expect: flush against an edge, butted against a neighbour, centred on it.
 */
public final class Snap {

    /** How near a guide must be, in GUI pixels, to attract the box. Holding Shift disables it. */
    public static final int THRESHOLD = 6;

    private Snap() {}

    /**
     * Aligns one axis of a box to the nearest guide.
     *
     * @param start the leading coordinate the drag produced
     * @param length the box's extent along this axis
     * @param guides coordinates worth aligning to
     * @return the leading coordinate to use: {@code start} when no guide is in range
     */
    public static int axis(int start, int length, int[] guides, int threshold) {
        int bestDelta = 0;
        int bestDistance = threshold + 1;
        // In preference order: an edge beats a centre at equal distance, because "flush with"
        // is the more common intent than "centred on".
        int[] lines = {start, start + length, start + length / 2};
        for (int line : lines) {
            for (int guide : guides) {
                int delta = guide - line;
                int distance = Math.abs(delta);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestDelta = delta;
                }
            }
        }
        return bestDistance <= threshold ? start + bestDelta : start;
    }
}
