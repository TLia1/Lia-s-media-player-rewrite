package com.lia.mediaplayer.core.playback;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.api.media.RepeatMode;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayQueueTest {

    private final PlayQueue<String> queue = new PlayQueue<>(RandomGenerator.of("L32X64MixRandom"));

    @Test
    void editsTheWaitingEntries() {
        int version = queue.version();
        queue.add("a");
        queue.addAll(List.of("b", "c"));
        queue.insert(0, "z");
        queue.insert(99, "end");
        assertThat(queue.upcoming()).containsExactly("z", "a", "b", "c", "end");
        assertThat(queue.version()).isGreaterThan(version);

        queue.move(0, 3);
        assertThat(queue.upcoming()).containsExactly("a", "b", "c", "z", "end");
        queue.move(-1, 2);
        queue.move(1, 1);
        assertThat(queue.upcoming()).containsExactly("a", "b", "c", "z", "end");

        assertThat(queue.remove(4)).isEqualTo("end");
        assertThat(queue.take(1)).contains("b");
        assertThat(queue.take(9)).isEmpty();
        assertThat(queue.get(0)).isEqualTo("a");
        assertThat(queue.size()).isEqualTo(3);

        queue.clear();
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void withoutRepeatWalksTheQueueThenStops() {
        queue.addAll(List.of("b", "c"));
        assertThat(queue.next("a")).contains("b");
        assertThat(queue.next("b")).contains("c");
        assertThat(queue.hasNext()).isFalse();
        assertThat(queue.next("c")).isEmpty();
    }

    @Test
    void repeatOneReplaysTheCurrentTrackAndLeavesTheQueueAlone() {
        queue.add("b");
        queue.setRepeat(RepeatMode.ONE);
        assertThat(queue.next("a")).contains("a");
        assertThat(queue.next("a")).contains("a");
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.hasPrevious()).isFalse();
        assertThat(queue.hasNext()).isTrue();
    }

    @Test
    void repeatAllReplaysTheWholeRoundInOrderWithoutDuplicates() {
        queue.addAll(List.of("b", "c"));
        queue.setRepeat(RepeatMode.ALL);
        var played = new ArrayList<String>();
        String current = "a";
        for (int i = 0; i < 6; i++) {
            current = queue.next(current).orElseThrow();
            played.add(current);
        }
        assertThat(played).containsExactly("b", "c", "a", "b", "c", "a");
    }

    @Test
    void repeatAllWithASingleTrackLoopsIt() {
        queue.setRepeat(RepeatMode.ALL);
        assertThat(queue.next("a")).contains("a");
        assertThat(queue.next("a")).contains("a");
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void aShuffledRoundNeverStartsWithTheTrackJustHeard(long seed) {
        var shuffled = new PlayQueue<String>(new SplittableRandom(seed));
        shuffled.addAll(List.of("b", "c", "d"));
        shuffled.setRepeat(RepeatMode.ALL);
        shuffled.setShuffle(true);
        String current = "a";
        for (int i = 0; i < 3; i++) {
            current = shuffled.next(current).orElseThrow();
        }
        // The round ends with `current`; the next round must not start with it.
        String lastOfRound = current;
        String firstOfNext = shuffled.next(lastOfRound).orElseThrow();
        assertThat(firstOfNext).isNotEqualTo(lastOfRound);
        var round = new ArrayList<String>(List.of(firstOfNext));
        round.addAll(shuffled.upcoming());
        assertThat(round).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    void shuffleKeepsEveryQueuedTrackAndToggles() {
        queue.addAll(List.of("a", "b", "c", "d", "e"));
        assertThat(queue.toggleShuffle()).isTrue();
        assertThat(queue.shuffle()).isTrue();
        assertThat(queue.upcoming()).containsExactlyInAnyOrder("a", "b", "c", "d", "e");
        assertThat(queue.toggleShuffle()).isFalse();
    }

    @Test
    void previousReturnsToTheLastTrackAndRequeuesTheCurrentOne() {
        queue.addAll(List.of("b", "c"));
        assertThat(queue.previous("a")).isEmpty();
        queue.next("a");
        assertThat(queue.hasPrevious()).isTrue();
        assertThat(queue.previous("b")).contains("a");
        assertThat(queue.upcoming()).containsExactly("b", "c");
    }

    @Test
    void historyIsBounded() {
        queue.setRepeat(RepeatMode.OFF);
        for (int i = 0; i < PlayQueue.MAX_HISTORY + 10; i++) {
            queue.add("t" + (i + 1));
            queue.next("t" + i);
        }
        int back = 0;
        String current = "x";
        while (queue.hasPrevious()) {
            current = queue.previous(current).orElseThrow();
            back++;
        }
        assertThat(back).isEqualTo(PlayQueue.MAX_HISTORY);
    }

    @Test
    void theLoopButtonCyclesThroughTheModes() {
        assertThat(queue.repeat()).isEqualTo(RepeatMode.OFF);
        assertThat(queue.cycleRepeat()).isEqualTo(RepeatMode.ALL);
        assertThat(queue.cycleRepeat()).isEqualTo(RepeatMode.ONE);
        assertThat(queue.cycleRepeat()).isEqualTo(RepeatMode.OFF);
    }
}
