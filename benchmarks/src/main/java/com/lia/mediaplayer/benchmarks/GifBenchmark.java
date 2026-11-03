package com.lia.mediaplayer.benchmarks;

import com.lia.mediaplayer.core.image.DecodedImage;
import com.lia.mediaplayer.core.image.ImageDecoders;
import com.lia.mediaplayer.core.testing.TestImages;
import com.lia.mediaplayer.core.testing.TestImages.GifFrame;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Decoding a chat GIF: 48 frames of 320×240, each a patch over the last, as Tenor serves them.
 * Off the client thread, but it decides how soon a hovered GIF shows.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GifBenchmark {

    private byte[] gif;

    @Setup
    public void setUp() {
        var frames = new ArrayList<GifFrame>();
        for (int i = 0; i < 48; i++) {
            int colour = 0xFF000000 | (i * 5) << 16 | (255 - i * 5) << 8;
            frames.add(new GifFrame((i * 7) % 200, (i * 5) % 120, 120, 120, colour, 4, "none"));
        }
        frames.set(0, GifFrame.full(320, 240, 0xFF202020, 4));
        gif = TestImages.gif(320, 240, frames);
    }

    @Benchmark
    public DecodedImage decodeAnimatedGif() throws IOException {
        return ImageDecoders.decode(gif);
    }
}
