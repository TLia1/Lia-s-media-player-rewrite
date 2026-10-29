package com.lia.mediaplayer.core.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CoreExecutorsTest {

    @Test
    void runsAtMostEightIoTasksAtOnceOnNamedVirtualThreads() throws InterruptedException {
        try (var executors = new CoreExecutors()) {
            var running = new AtomicInteger();
            var peak = new AtomicInteger();
            var release = new CountDownLatch(1);
            var done = new CountDownLatch(20);
            var names = new java.util.concurrent.ConcurrentLinkedQueue<String>();
            for (int i = 0; i < 20; i++) {
                executors.io().execute(() -> {
                    names.add(Thread.currentThread().getName());
                    peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    running.decrementAndGet();
                    done.countDown();
                });
            }
            Thread.sleep(200);
            assertThat(peak.get()).isEqualTo(CoreExecutors.IO_PERMITS);
            release.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(names).allMatch(name -> name.startsWith("lmp-io-"));
        }
    }

    @Test
    void decodesOnDaemonPlatformThreadsAndStartsNamedThreads() throws Exception {
        try (var executors = new CoreExecutors()) {
            var thread = new java.util.concurrent.CompletableFuture<Thread>();
            executors.decode().execute(() -> thread.complete(Thread.currentThread()));
            Thread decoder = thread.get(5, TimeUnit.SECONDS);
            assertThat(decoder.getName()).startsWith("lmp-decode-");
            assertThat(decoder.isDaemon()).isTrue();
            assertThat(decoder.isVirtual()).isFalse();
        }
        Thread pump = CoreExecutors.platformThread("lmp-test-pump", Thread.MAX_PRIORITY - 1, () -> {});
        pump.join();
        assertThat(pump.getName()).isEqualTo("lmp-test-pump");
        Thread reader = CoreExecutors.virtualThread("lmp-test-reader", () -> {});
        reader.join();
        assertThat(reader.isVirtual()).isTrue();
    }

    @Test
    void closingInterruptsWhatIsStillRunning() throws InterruptedException {
        var interrupted = new CountDownLatch(1);
        var executors = new CoreExecutors();
        var started = new CountDownLatch(1);
        executors.io().execute(() -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        executors.close();
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
