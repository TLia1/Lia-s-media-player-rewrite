package com.lia.mediaplayer.ui.anim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class AnimationTest {

    @Test
    void movesTowardsItsTargetOverItsDuration() {
        var a = new Animation(0, 100, Easing.LINEAR);
        a.animateTo(1, 1000);
        assertThat(a.value(1000)).isZero();
        assertThat(a.value(1050)).isCloseTo(0.5f, within(1e-6f));
        assertThat(a.isDone(1050)).isFalse();
        assertThat(a.value(1100)).isEqualTo(1f);
        assertThat(a.isDone(2000)).isTrue();
        assertThat(a.target()).isEqualTo(1f);
    }

    @Test
    void retargetingStartsFromWhereItIs() {
        var a = new Animation(0, 100, Easing.LINEAR);
        a.animateTo(1, 0);
        a.animateTo(0, 50);
        assertThat(a.value(50)).isCloseTo(0.5f, within(1e-6f));
        assertThat(a.value(100)).isCloseTo(0.25f, within(1e-6f));
        a.set(3);
        assertThat(a.value(0)).isEqualTo(3f);
    }

    @Test
    void easingsStartAtZeroAndLandOnOne() {
        for (Easing e : new Easing[] {Easing.LINEAR, Easing.OUT_CUBIC, Easing.IN_CUBIC}) {
            assertThat(e.apply(0)).isZero();
            assertThat(e.apply(1)).isEqualTo(1f);
        }
        assertThat(Easing.OUT_CUBIC.apply(0.5f)).isGreaterThan(0.5f);
        assertThat(Easing.IN_CUBIC.apply(0.5f)).isLessThan(0.5f);
        assertThatThrownBy(() -> new Animation(0, 0, Easing.LINEAR)).isInstanceOf(IllegalArgumentException.class);
    }
}
