package com.lia.mediaplayer.benchmarks;

import com.lia.mediaplayer.core.audio.PcmFormat;
import com.lia.mediaplayer.core.audio.PcmRing;
import com.lia.mediaplayer.core.video.FramePool;
import com.lia.mediaplayer.core.video.FrameRing;
import com.lia.mediaplayer.core.video.VideoFrame;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * The rings between ffmpeg and the outputs: what one 20 ms chunk of sound and one frame of
 * picture cost to pass through, single-threaded, so the number is the rings' own overhead.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RingBenchmark {

    private static final int CHUNK = PcmFormat.STEREO_48K.bytesPerSecond() / 50;

    private PcmRing pcm;
    private ByteArrayInputStream source;
    private FramePool pool;
    private FrameRing frames;
    private long pts;

    @Setup
    public void setUp() {
        pcm = PcmRing.forDuration(PcmFormat.STEREO_48K, 2);
        source = new ByteArrayInputStream(new byte[CHUNK]);
        pool = new FramePool(8);
        frames = new FrameRing(30, pool);
    }

    /** A socket read into the ring, then the pump's read out of it. */
    @Benchmark
    public int soundChunkThroughThePcmRing() throws IOException {
        source.reset();
        int read = 0;
        while (read < CHUNK) {
            read += pcm.fillFrom(source);
        }
        int moved = 0;
        while (moved < CHUNK) {
            int n = pcm.readableContiguous();
            pcm.consume(n);
            moved += n;
        }
        return moved;
    }

    /** A 640×360 frame from the pool, into the ring, out as the due frame, back to the pool. */
    @Benchmark
    public long frameThroughTheFrameRing() throws InterruptedException {
        VideoFrame frame = pool.acquire(640, 360);
        frame.stamp(1, pts);
        frames.put(frame);
        VideoFrame due = frames.takeDue(pts);
        pts += 33_333;
        if (due != null) {
            pool.release(due);
            return due.ptsMicros();
        }
        return -1;
    }
}
