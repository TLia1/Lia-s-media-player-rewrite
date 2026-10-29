package com.lia.mediaplayer.ui.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PointerTrackerTest {

    private final PointerTracker tracker = new PointerTracker();

    @Test
    void theFirstPositionIsOnlyRemembered() {
        assertThat(tracker.moveTo(10, 10, Mods.NONE)).isNull();
        assertThat(tracker.moveTo(10, 10, Mods.NONE)).isNull();
        assertThat(tracker.moveTo(12, 10, Mods.NONE)).isEqualTo(new InputEvent.PointerMove(12, 10));
    }

    @Test
    void aHeldButtonTurnsMovesIntoDragsUntilReleased() {
        tracker.moveTo(0, 0, Mods.NONE);
        tracker.press(5, 5, 0, Mods.NONE, 0);
        assertThat(tracker.isHeld()).isTrue();
        assertThat(tracker.moveTo(8, 9, Mods.shiftOnly()))
                .isEqualTo(new InputEvent.PointerDrag(8, 9, 0, 3, 4, Mods.shiftOnly()));
        tracker.release(8, 9, 0, Mods.NONE);
        assertThat(tracker.isHeld()).isFalse();
        assertThat(tracker.moveTo(9, 9, Mods.NONE)).isInstanceOf(InputEvent.PointerMove.class);
    }

    @Test
    void countsQuickPressesInPlaceAsOneMultipleClick() {
        assertThat(tracker.press(5, 5, 0, Mods.NONE, 0).clicks()).isEqualTo(1);
        tracker.release(5, 5, 0, Mods.NONE);
        assertThat(tracker.press(6, 5, 0, Mods.NONE, 100_000_000L).clicks()).isEqualTo(2);
        // Too late, too far, or another button: a new first click.
        assertThat(tracker.press(6, 5, 0, Mods.NONE, 900_000_000L).clicks()).isEqualTo(1);
        assertThat(tracker.press(20, 5, 0, Mods.NONE, 950_000_000L).clicks()).isEqualTo(1);
        assertThat(tracker.press(20, 5, 1, Mods.NONE, 960_000_000L).clicks()).isEqualTo(1);
    }

    @Test
    void aNewScreenLetsGoOfEverything() {
        tracker.press(5, 5, 0, Mods.NONE, 0);
        tracker.reset();
        assertThat(tracker.isHeld()).isFalse();
        assertThat(tracker.press(5, 5, 0, Mods.NONE, 1).clicks()).isEqualTo(1);
    }

    @Test
    void modsKnowThePrimaryModifierPerPlatform() {
        assertThat(new Mods(false, true, false, false, false).primary()).isTrue();
        assertThat(new Mods(false, true, false, false, true).primary()).isFalse();
        assertThat(new Mods(false, false, false, true, true).primary()).isTrue();
        assertThat(Mods.NONE.none()).isTrue();
        assertThat(Mods.ctrlOnly().none()).isFalse();
    }

    @Test
    void keysByLetterAndDigit() {
        assertThat(Key.letter('q')).isEqualTo(Key.Q);
        assertThat(Key.letter('!')).isEqualTo(Key.OTHER);
        assertThat(Key.digit(7)).isEqualTo(Key.DIGIT_7);
        assertThat(Key.digit(12)).isEqualTo(Key.OTHER);
    }
}
