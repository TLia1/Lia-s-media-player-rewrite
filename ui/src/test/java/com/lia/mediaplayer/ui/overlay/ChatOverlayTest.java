package com.lia.mediaplayer.ui.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.ui.canvas.Rect;
import org.junit.jupiter.api.Test;

class ChatOverlayTest {

    @Test
    void aPreviewSitsAboveAndRightOfTheCursor() {
        Rect area = ChatOverlay.previewArea(640, 360, 100, 50, 200, 300);
        assertThat(area)
                .isEqualTo(new Rect(200 + ChatOverlay.CURSOR_OFFSET, 300 - 50 - ChatOverlay.CURSOR_OFFSET, 100, 50));
    }

    @Test
    void aLargePictureIsScaledDownToHalfTheScreenNeverUp() {
        Rect big = ChatOverlay.previewArea(640, 360, 1280, 720, 10, 350);
        assertThat(big.width()).isEqualTo(320);
        assertThat(big.height()).isEqualTo(180);
        Rect tall = ChatOverlay.previewArea(640, 360, 100, 900, 10, 350);
        assertThat(tall.height()).isEqualTo(180);
        assertThat(tall.width()).isEqualTo(20);
    }

    @Test
    void thePreviewStaysOnScreen() {
        Rect corner = ChatOverlay.previewArea(640, 360, 200, 100, 630, 20);
        assertThat(corner.right()).isLessThanOrEqualTo(638);
        assertThat(corner.y()).isGreaterThanOrEqualTo(2);
    }
}
