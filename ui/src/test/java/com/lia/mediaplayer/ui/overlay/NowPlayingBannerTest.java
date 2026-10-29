package com.lia.mediaplayer.ui.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.text.Text;
import com.lia.mediaplayer.ui.testing.RecordingCanvas;
import com.lia.mediaplayer.ui.theme.Themes;
import com.lia.mediaplayer.ui.widget.RenderContext;
import org.junit.jupiter.api.Test;

class NowPlayingBannerTest {

    private final NowPlayingBanner banner = new NowPlayingBanner();
    private final RecordingCanvas canvas = new RecordingCanvas(320, 200);

    private void render(long nanos) {
        banner.render(canvas.clear(), new RenderContext(Themes.DARK, nanos, Double.NaN, Double.NaN, false));
    }

    @Test
    void showsForAFewSecondsThenFades() {
        render(0);
        assertThat(canvas.calls()).isEmpty();
        banner.announce(Text.literal("Song"), 1_000);
        assertThat(banner.isShowing(1_000)).isTrue();
        render(1_000);
        assertThat(canvas.strings()).containsExactly("gui.liasmediaplayer.banner.now_playing[Song]");
        assertThat(canvas.texts().getFirst().argb() >>> 24).isEqualTo(0xFF);
        render(1_000 + NowPlayingBanner.SHOWN_NANOS - 100_000_000L);
        assertThat(canvas.texts().getFirst().argb() >>> 24).isLessThan(0xFF);
        render(1_000 + NowPlayingBanner.SHOWN_NANOS);
        assertThat(canvas.calls()).isEmpty();
        assertThat(banner.isShowing(1_000 + NowPlayingBanner.SHOWN_NANOS)).isFalse();
    }
}
