package com.lia.mediaplayer.ui.canvas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeometryTest {

    @Test
    void aRectKnowsItsEdgesAndClampsNegativeSizes() {
        var r = new Rect(10, 20, 30, 40);
        assertThat(r.right()).isEqualTo(40);
        assertThat(r.bottom()).isEqualTo(60);
        assertThat(r.centerX()).isEqualTo(25);
        assertThat(r.centerY()).isEqualTo(40);
        assertThat(r.contains(10, 20)).isTrue();
        assertThat(r.contains(40, 20)).isFalse();
        assertThat(new Rect(0, 0, -5, 3).isEmpty()).isTrue();
        assertThat(Rect.ofEdges(5, 5, 2, 9).width()).isZero();
    }

    @Test
    void insetsTranslatesAndIntersects() {
        var r = new Rect(0, 0, 100, 50);
        assertThat(r.inset(5)).isEqualTo(new Rect(5, 5, 90, 40));
        assertThat(r.inset(1, 2, 3, 4)).isEqualTo(new Rect(1, 2, 96, 44));
        assertThat(r.translate(3, -2)).isEqualTo(new Rect(3, -2, 100, 50));
        assertThat(r.withX(7).withY(8).withSize(1, 2)).isEqualTo(new Rect(7, 8, 1, 2));
        assertThat(r.intersect(new Rect(90, 40, 50, 50))).isEqualTo(new Rect(90, 40, 10, 10));
        assertThat(r.intersects(new Rect(200, 0, 5, 5))).isFalse();
        assertThat(r.intersect(new Rect(200, 0, 5, 5)).isEmpty()).isTrue();
    }

    @Test
    void fitsAPictureLetterboxedAndCentred() {
        var box = new Rect(0, 0, 200, 200);
        assertThat(box.fit(16, 9)).isEqualTo(new Rect(0, 43, 200, 113));
        assertThat(box.fit(9, 16)).isEqualTo(new Rect(43, 0, 113, 200));
        assertThat(box.fit(0, 9)).isEqualTo(box);
    }

    @Test
    void clampsIntoAnAreaByMovingNotResizing() {
        var screen = new Rect(0, 0, 100, 100);
        assertThat(new Rect(90, -5, 30, 30).clampInto(screen)).isEqualTo(new Rect(70, 0, 30, 30));
        assertThat(new Rect(10, 10, 30, 30).clampInto(screen)).isEqualTo(new Rect(10, 10, 30, 30));
    }

    @Test
    void mixesAndFadesColours() {
        assertThat(Argb.alpha(0x80FFFFFF)).isEqualTo(0x80);
        assertThat(Argb.fade(0xFF102030, 0.5f)).isEqualTo(0x80102030);
        assertThat(Argb.fade(0xFF102030, 2f)).isEqualTo(0xFF102030);
        assertThat(Argb.mix(0xFF000000, 0xFFFFFFFF, 0.5f)).isEqualTo(0xFF808080);
        assertThat(Argb.mix(0x00000000, 0xFF0000FF, 1f)).isEqualTo(0xFF0000FF);
    }
}
