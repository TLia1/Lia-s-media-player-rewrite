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
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
