package com.lia.mediaplayer.core.addon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AddonGuardTest {

    private final AddonGuard guard = new AddonGuard(3);

    @Test
    void passesAnswersThroughAndContainsFailures() {
        assertThat(guard.call("myaddon:source", () -> "found", "none")).isEqualTo("found");
        assertThat(guard.call("myaddon:source", () -> null, "none")).isEqualTo("none");
        assertThat(guard.call(
                        "myaddon:source",
                        () -> {
                            throw new IllegalStateException("bug");
                        },
                        "none"))
                .isEqualTo("none");
        assertThat(guard.failures("myaddon:source")).isEqualTo(1);
        assertThat(guard.isDisabled("myaddon:source")).isFalse();

        assertThat(guard.run("myaddon:button", () -> {})).isTrue();
        assertThat(guard.run("myaddon:button", () -> {
                    throw new NoClassDefFoundError("com/example/Missing");
                }))
                .isFalse();
        assertThat(guard.failures("myaddon:button")).isEqualTo(1);
        assertThat(guard.failures("myaddon:other")).isZero();
    }

    @Test
    void aRegistrationThatKeepsFailingIsNeverCalledAgain() {
        var calls = new AtomicInteger();
        Runnable broken = () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("bug");
        };
        for (int i = 0; i < 5; i++) {
            var unused = guard.run("myaddon:broken", broken);
        }
        assertThat(calls).hasValue(3);
        assertThat(guard.isDisabled("myaddon:broken")).isTrue();
        assertThat(guard.call("myaddon:broken", () -> "x", "fallback")).isEqualTo("fallback");
        // Failures of one registration leave the others alone.
        assertThat(guard.run("myaddon:fine", () -> {})).isTrue();
    }

    @Test
    void theLimitIsAtLeastOne() {
        assertThatThrownBy(() -> new AddonGuard(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new AddonGuard().failures("x")).isZero();
    }
}
