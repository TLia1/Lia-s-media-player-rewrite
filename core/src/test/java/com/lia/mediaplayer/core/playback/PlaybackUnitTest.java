package com.lia.mediaplayer.core.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.source.ResolveOptions;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlaybackUnitTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final WallClock clock = new WallClock(nanos::get);

    @Test
    void theWallClockRunsOnlyWhileResumed() {
        clock.reset(5_000_000);
        nanos.addAndGet(1_000_000_000L);
        assertThat(clock.nowMicros()).isEqualTo(5_000_000);

        clock.resume();
        clock.resume();
        nanos.addAndGet(250_000_000L);
        assertThat(clock.nowMicros()).isEqualTo(5_250_000);

        clock.pause();
        clock.pause();
        nanos.addAndGet(1_000_000_000L);
        assertThat(clock.nowMicros()).isEqualTo(5_250_000);

        clock.resume();
        nanos.addAndGet(100_000_000L);
        assertThat(clock.nowMicros()).isEqualTo(5_350_000);
        clock.reset(0);
        assertThat(clock.nowMicros()).isZero();
    }

    @Test
    void optionsClampTheVolumeAndRefuseNonsense() {
        PlayerOptions options = PlayerOptions.DEFAULT
                .withVolume(3f)
                .withStartAt(Duration.ofSeconds(4))
                .withLongPause(Duration.ofSeconds(1))
                .withResolve(new ResolveOptions(1080, true));
        assertThat(options.volume()).isEqualTo(1f);
        assertThat(options.startAt()).isEqualTo(Duration.ofSeconds(4));
        assertThat(options.longPause()).isEqualTo(Duration.ofSeconds(1));
        assertThat(options.resolve().maxHeight()).isEqualTo(1080);
        assertThat(PlayerOptions.DEFAULT.withVolume(-1f).volume()).isZero();
        assertThatThrownBy(() -> PlayerOptions.DEFAULT.withStartAt(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerOptions(
                        ResolveOptions.DEFAULT,
                        Duration.ZERO,
                        false,
                        60,
                        Duration.ZERO,
                        Duration.ZERO,
                        Duration.ofSeconds(1),
                        1,
                        0,
                        true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePictureIsDecodedAtTheSmallestStepThatCoversTheView() {
        assertThat(PlayerOptions.decodeHeight(0, 720)).as("size unknown").isEqualTo(720);
        assertThat(PlayerOptions.decodeHeight(200, 720)).isEqualTo(360);
        assertThat(PlayerOptions.decodeHeight(361, 720)).isEqualTo(480);
        assertThat(PlayerOptions.decodeHeight(700, 720)).isEqualTo(720);
        assertThat(PlayerOptions.decodeHeight(900, 720))
                .as("never above the setting")
                .isEqualTo(720);
        assertThat(PlayerOptions.decodeHeight(900, 1080)).isEqualTo(1080);
        assertThat(PlayerOptions.decodeHeight(2000, 1080)).isEqualTo(1080);
        assertThat(PlayerOptions.decodeHeight(400, 360)).isEqualTo(360);
        assertThat(PlayerOptions.DEFAULT.fpsFor(480)).isEqualTo(30);
        assertThat(PlayerOptions.DEFAULT.fpsFor(720)).isEqualTo(60);
        assertThat(PlayerOptions.DEFAULT.withViewHeight(-5).viewHeight()).isZero();
        assertThat(PlayerOptions.DEFAULT.withVideo(false).withViewHeight(300)).satisfies(options -> {
            assertThat(options.video()).isFalse();
            assertThat(options.viewHeight()).isEqualTo(300);
        });
    }
}
