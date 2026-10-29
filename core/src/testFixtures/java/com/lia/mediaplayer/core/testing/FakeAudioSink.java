package com.lia.mediaplayer.core.testing;

import com.lia.mediaplayer.core.audio.AudioException;
import com.lia.mediaplayer.core.audio.AudioSink;
import com.lia.mediaplayer.core.audio.PcmFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A sound card that keeps real time without making a sound: while started, it "plays" queued
 * frames at the format's rate, and a write blocks while its 150 ms buffer is full — the two
 * behaviours of a {@code SourceDataLine} the engine relies on. Records what was asked of it.
 */
public final class FakeAudioSink implements AudioSink {

    private static final int BUFFER_MILLIS = 150;

    private final boolean failToOpen;
    private PcmFormat format = PcmFormat.STEREO_48K;
    private long bufferFrames;
    private long writtenFrames;
    private long playedFrames;
    private long lastUpdateNanos;
    private boolean started;
    private boolean opened;
    private int interruptions;
    private volatile float gain = 1;
    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicInteger flushes = new AtomicInteger();

    public FakeAudioSink() {
        this(false);
    }

    private FakeAudioSink(boolean failToOpen) {
        this.failToOpen = failToOpen;
    }

    /** A sink that has no device, as on a CI machine. */
    public static FakeAudioSink unavailable() {
        return new FakeAudioSink(true);
    }

    @Override
    public synchronized void open(PcmFormat newFormat) throws AudioException {
        if (failToOpen) {
            throw new AudioException("No audio device (fake)");
        }
        format = newFormat;
        bufferFrames = (long) newFormat.sampleRate() * BUFFER_MILLIS / 1000;
        opened = true;
        lastUpdateNanos = System.nanoTime();
    }

    @Override
    public synchronized int write(byte[] pcm, int offset, int length) {
        int frames = length / format.frameBytes();
        int epoch = interruptions;
        int taken = 0;
        while (taken < frames) {
            advance();
            long space = bufferFrames - (writtenFrames - playedFrames);
            if (space > 0) {
                long now = Math.min(space, frames - taken);
                writtenFrames += now;
                taken += (int) now;
                continue;
            }
            if (interruptions != epoch) {
                break;
            }
            try {
                wait(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (interruptions != epoch) {
                break;
            }
        }
        return taken * format.frameBytes();
    }

    @Override
    public synchronized void start() {
        advance();
        started = true;
        starts.incrementAndGet();
        notifyAll();
    }

    @Override
    public synchronized void stop() {
        advance();
        started = false;
        interruptions++;
        notifyAll();
    }

    @Override
    public synchronized void flush() {
        advance();
        writtenFrames = playedFrames;
        interruptions++;
        flushes.incrementAndGet();
        notifyAll();
    }

    @Override
    public synchronized long playedFrames() {
        advance();
        return playedFrames;
    }

    @Override
    public void setGain(float linear) {
        gain = linear;
    }

    @Override
    public void setPan(float pan) {}

    @Override
    public synchronized void close() {
        opened = false;
        started = false;
        interruptions++;
        notifyAll();
    }

    public float gain() {
        return gain;
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized boolean isOpen() {
        return opened;
    }

    public int starts() {
        return starts.get();
    }

    public int flushes() {
        return flushes.get();
    }

    private void advance() {
        long now = System.nanoTime();
        if (started) {
            long elapsed = (now - lastUpdateNanos) * format.sampleRate() / 1_000_000_000L;
            playedFrames += Math.min(elapsed, writtenFrames - playedFrames);
        }
        lastUpdateNanos = now;
    }
}
