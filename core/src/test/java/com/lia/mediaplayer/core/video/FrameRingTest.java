package com.lia.mediaplayer.core.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FrameRingTest {

    private final FramePool pool = new FramePool(8);
    private final FrameRing ring = new FrameRing(4, pool);

    private VideoFrame frame(long ptsMicros) {
        VideoFrame frame = pool.acquire(4, 2);
        frame.stamp(1, ptsMicros);
        return frame;
    }

    @Test
    void handsOutTheNewestDueFrameAndRecyclesTheOnesItSkipped() throws InterruptedException {
        ring.put(frame(0));
        ring.put(frame(33));
        ring.put(frame(66));
        ring.put(frame(100));

        assertThat(ring.takeDue(-1)).isNull();
        VideoFrame due = ring.takeDue(70);
        assertThat(due).isNotNull();
        assertThat(due.ptsMicros()).isEqualTo(66);
        assertThat(due.width()).isEqualTo(4);
        assertThat(due.height()).isEqualTo(2);
        assertThat(due.pixels()).hasSize(4 * 2 * 4);
        assertThat(due.buffer().capacity()).isEqualTo(32);
        assertThat(due.generation()).isEqualTo(1);
        assertThat(ring.size()).isEqualTo(1);
        assertThat(pool.freeCount()).isEqualTo(2);
        pool.release(due);
        assertThat(pool.freeCount()).isEqualTo(3);
    }

    @Test
    void withoutAViewKeepsOnlyTheNewestDueFrame() throws InterruptedException {
        for (long pts : new long[] {0, 10, 20, 30}) {
            ring.put(frame(pts));
        }
        assertThat(ring.allDue(25)).isFalse();
        assertThat(ring.discardSuperseded(25)).isEqualTo(2);
        assertThat(ring.size()).isEqualTo(2);
        assertThat(ring.allDue(30)).isTrue();
        assertThat(ring.discardSuperseded(1000)).isEqualTo(1);
        assertThat(ring.takeDue(1000).ptsMicros()).isEqualTo(30);
        assertThat(ring.allDue(0)).isTrue();
    }

    @Test
    void aFullRingHoldsTheReaderBackUntilAFrameIsTaken() throws Exception {
        for (long pts = 0; pts < 4; pts++) {
            ring.put(frame(pts));
        }
        var blocked = CompletableFuture.supplyAsync(() -> {
            try {
                return ring.put(frame(4));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(100);
        assertThat(blocked).isNotDone();
        pool.release(ring.takeDue(0));
        assertThat(blocked.get(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void closingReleasesEverythingAndUnblocksTheReader() throws Exception {
        for (long pts = 0; pts < 4; pts++) {
            ring.put(frame(pts));
        }
        var blocked = CompletableFuture.supplyAsync(() -> {
            try {
                return ring.put(frame(4));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(50);
        ring.close();
        assertThat(blocked.get(2, TimeUnit.SECONDS)).isFalse();
        assertThat(ring.size()).isZero();
        assertThat(pool.freeCount()).isEqualTo(5);
    }

    @Test
    void thePoolDropsFramesOfAnOldSize() {
        VideoFrame small = pool.acquire(2, 2);
        VideoFrame large = pool.acquire(8, 8);
        pool.release(small);
        assertThat(pool.freeCount()).isZero();
        pool.release(large);
        assertThat(pool.acquire(8, 8)).isSameAs(large);
        assertThatThrownBy(() -> new FrameRing(1, pool)).isInstanceOf(IllegalArgumentException.class);
    }
}
