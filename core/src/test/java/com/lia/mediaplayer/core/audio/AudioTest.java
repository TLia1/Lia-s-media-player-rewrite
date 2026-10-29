package com.lia.mediaplayer.core.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.lia.mediaplayer.core.testing.FakeAudioSink;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class AudioTest {

    @Test
    void theRingWrapsAroundAndReportsTheEnd() throws IOException {
        var ring = new PcmRing(10);
        assertThat(ring.capacity()).isEqualTo(16);
        var in = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});

        assertThat(ring.fillFrom(in)).isEqualTo(12);
        assertThat(ring.readableContiguous()).isEqualTo(12);
        ring.consume(8);
        assertThat(ring.available()).isEqualTo(4);

        var more = new ByteArrayInputStream(new byte[] {13, 14, 15, 16, 17, 18, 19, 20});
        // Only the four bytes up to the end of the array fit in one read.
        assertThat(ring.fillFrom(more)).isEqualTo(4);
        assertThat(ring.fillFrom(more)).isEqualTo(4);
        assertThat(ring.available()).isEqualTo(12);
        assertThat(ring.readIndex()).isEqualTo(8);
        assertThat(ring.readableContiguous()).isEqualTo(8);
        assertThat(ring.array()[ring.readIndex()]).isEqualTo((byte) 9);
        ring.consume(8);
        assertThat(ring.array()[ring.readIndex()]).isEqualTo((byte) 17);

        assertThat(ring.isDrained()).isFalse();
        assertThat(ring.fillFrom(more)).isEqualTo(-1);
        assertThat(ring.isEndOfStream()).isTrue();
        ring.consume(4);
        assertThat(ring.isDrained()).isTrue();
        assertThatThrownBy(() -> ring.consume(1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aClosedRingStopsAWaitingProducer() throws Exception {
        var ring = new PcmRing(4);
        ring.fillFrom(new ByteArrayInputStream(new byte[4]));
        var result = new java.util.concurrent.CompletableFuture<Integer>();
        Thread producer = Thread.ofVirtual().start(() -> {
            try {
                result.complete(ring.fillFrom(InputStream.nullInputStream()));
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
        });
        Thread.sleep(50);
        ring.close();
        producer.join(1000);
        assertThat(result.get()).isEqualTo(-1);
        assertThatThrownBy(() -> new PcmRing(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(PcmRing.forDuration(PcmFormat.STEREO_48K, 2).capacity()).isEqualTo(1 << 19);
    }

    @Test
    void formatsKnowTheirSizes() {
        assertThat(PcmFormat.STEREO_48K.frameBytes()).isEqualTo(4);
        assertThat(PcmFormat.STEREO_48K.bytesPerSecond()).isEqualTo(192_000);
        assertThat(PcmFormat.STEREO_48K.framesToMicros(48_000)).isEqualTo(1_000_000);
        assertThatThrownBy(() -> new PcmFormat(1000, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void softwareGainScalesSigned16BitSamples() {
        byte[] src = {(byte) 0xE8, 0x03, 0x18, (byte) 0xFC, (byte) 0xFF, 0x7F}; // 1000, -1000, 32767
        byte[] dst = new byte[6];
        JavaSoundSink.scale(src, 0, dst, 6, 0.5f);
        assertThat(sample(dst, 0)).isEqualTo(500);
        assertThat(sample(dst, 2)).isEqualTo(-500);
        assertThat(sample(dst, 4)).isEqualTo(16384);
    }

    private static int sample(byte[] pcm, int offset) {
        return (short) ((pcm[offset] & 0xFF) | (pcm[offset + 1] << 8));
    }

    /** The pump plays a feed in real time, keeps the clock from the feed's start, and reports the end. */
    @Test
    void thePumpPlaysAFeedAndKeepsTheClock() throws Exception {
        var sink = new FakeAudioSink();
        sink.open(PcmFormat.STEREO_48K);
        var drained = new CopyOnWriteArrayList<Long>();
        try (var pump = new AudioPump(sink, PcmFormat.STEREO_48K, "test-pump", drained::add)) {
            var ring = PcmRing.forDuration(PcmFormat.STEREO_48K, 1);
            // 300 ms of silence.
            ring.fillFrom(new ByteArrayInputStream(new byte[192_000 * 3 / 10]));
            ring.markEndOfStream();
            pump.switchTo(ring, 7, 5_000_000);
            assertThat(pump.positionMicros()).isEqualTo(5_000_000);
            pump.setGain(0.25f);
            assertThat(sink.gain()).isEqualTo(0.25f);

            pump.play();
            assertThat(pump.isPlaying()).isTrue();
            Thread.sleep(150);
            assertThat(pump.positionMicros()).isBetween(5_050_000L, 5_250_000L);

            long deadline = System.nanoTime() + 2_000_000_000L;
            while (drained.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(drained).containsExactly(7L);
            assertThat(pump.positionMicros()).isCloseTo(5_300_000L, within(5_000L));

            pump.pause();
            assertThat(sink.isStarted()).isFalse();

            // A seek: the new feed's clock starts at its own position, whatever came before.
            var next = PcmRing.forDuration(PcmFormat.STEREO_48K, 1);
            pump.switchTo(next, 8, 60_000_000);
            assertThat(pump.positionMicros()).isEqualTo(60_000_000);
            Thread.sleep(30);
            assertThat(pump.positionMicros()).isEqualTo(60_000_000);
        }
        assertThat(sink.isOpen()).isFalse();
    }
}
