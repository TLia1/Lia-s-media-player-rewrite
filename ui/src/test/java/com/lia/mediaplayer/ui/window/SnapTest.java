package com.lia.mediaplayer.ui.window;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.ui.canvas.Rect;
import com.lia.mediaplayer.ui.widget.Size;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The pull a dragged window feels, and where new windows open. Pure geometry. */
class SnapTest {

    private static final int T = Snap.THRESHOLD;

    @Test
    void leavesACoordinateAloneWhenNothingIsNear() {
        assertThat(Snap.axis(100, 50, new int[] {0, 400}, T)).isEqualTo(100);
        assertThat(Snap.axis(100, 50, new int[0], T)).isEqualTo(100);
    }

    @Test
    void pullsTheLeadingEdgeTrailingEdgeOrCentreOntoAGuide() {
        assertThat(Snap.axis(4, 50, new int[] {2}, T)).isEqualTo(2);
        assertThat(Snap.axis(100, 50, new int[] {152}, T)).isEqualTo(102);
        assertThat(Snap.axis(100, 50, new int[] {127}, T)).isEqualTo(102);
    }

    @Test
    void takesAGuideAtTheThresholdButNotBeyond() {
        assertThat(Snap.axis(100, 50, new int[] {100 + T}, T)).isEqualTo(100 + T);
        assertThat(Snap.axis(100, 50, new int[] {100 + T + 1}, T)).isEqualTo(100);
    }

    @Test
    void prefersTheNearestGuideAndAnEdgeOverACentreAtEqualDistance() {
        assertThat(Snap.axis(100, 50, new int[] {95, 102}, T)).isEqualTo(102);
        assertThat(Snap.axis(100, 50, new int[] {97}, T)).isEqualTo(97);
        assertThat(Snap.axis(238, 60, new int[] {300, 400, 350}, T)).isEqualTo(240);
        assertThat(Snap.axis(100, 50, new int[] {103, 128}, T)).isEqualTo(103);
    }

    @Test
    void newWindowsOpenTopRightAndCascade() {
        var screen = new Rect(0, 0, 400, 300);
        var size = new Size(100, 60);
        Rect first = Placement.initial(screen, size, List.of());
        assertThat(first).isEqualTo(new Rect(294, 6, 100, 60));
        Rect second = Placement.initial(screen, size, List.of(first));
        assertThat(second).isEqualTo(new Rect(280, 20, 100, 60));
        Rect third = Placement.initial(screen, size, List.of(first, second));
        assertThat(third).isEqualTo(new Rect(266, 34, 100, 60));
        // Elsewhere on the screen does not count as taken.
        assertThat(Placement.initial(screen, size, List.of(new Rect(0, 200, 100, 60))))
                .isEqualTo(first);
    }

    @Test
    void aWindowBiggerThanTheScreenIsShrunkToFit() {
        Rect placed = Placement.initial(new Rect(0, 0, 100, 80), new Size(300, 300), List.of());
        assertThat(placed).isEqualTo(new Rect(6, 6, 88, 68));
    }
}
