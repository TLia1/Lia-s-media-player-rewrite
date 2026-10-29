package com.lia.mediaplayer.core.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import org.jspecify.annotations.Nullable;

/**
 * Plays through a Java Sound {@link SourceDataLine}, as v2 did: a device buffer of about
 * {@value #DEVICE_BUFFER_MILLIS} ms (the jitter buffer is the pump's {@link PcmRing}, not the
 * line), gain through the line's master-gain control in decibels with a linear-volume
 * fallback, and pan with a balance fallback. A line with neither gain control is scaled in
 * software.
 *
 * <p>Gain and pan are stored by any thread and applied by the pump at its next write, so the
 * audio thread never waits for the caller.
 */
public final class JavaSoundSink implements AudioSink {

    static final int DEVICE_BUFFER_MILLIS = 150;
    private static final int SCRATCH_BYTES = 16 * 1024;

    private @Nullable SourceDataLine line;
    private @Nullable FloatControl masterGain;
    private @Nullable FloatControl volume;
    private @Nullable FloatControl pan;
    private boolean softwareGain;
    private final byte[] scratch = new byte[SCRATCH_BYTES];

    private volatile float gain = 1;
    private volatile float panValue;
    private float appliedGain = Float.NaN;
    private float appliedPan = Float.NaN;

    @Override
    public void open(PcmFormat format) throws AudioException {
        var audioFormat = new AudioFormat(format.sampleRate(), 16, format.channels(), true, false);
        var info = new DataLine.Info(SourceDataLine.class, audioFormat);
        if (!AudioSystem.isLineSupported(info)) {
            throw new AudioException(
                    "No audio device plays " + format.sampleRate() + " Hz, " + format.channels() + " ch");
        }
        try {
            var opened = (SourceDataLine) AudioSystem.getLine(info);
            int bufferBytes = format.bytesPerSecond() * DEVICE_BUFFER_MILLIS / 1000;
            opened.open(audioFormat, bufferBytes - bufferBytes % format.frameBytes());
            masterGain = control(opened, FloatControl.Type.MASTER_GAIN);
            volume = masterGain == null ? control(opened, FloatControl.Type.VOLUME) : null;
            softwareGain = masterGain == null && volume == null;
            pan = control(opened, FloatControl.Type.PAN);
            if (pan == null) {
                pan = control(opened, FloatControl.Type.BALANCE);
            }
            line = opened;
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            throw new AudioException("Audio line unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public int write(byte[] pcm, int offset, int length) {
        SourceDataLine target = requireLine();
        applyControls();
        if (!softwareGain || gain >= 0.999f) {
            return target.write(pcm, offset, length);
        }
        int written = 0;
        while (written < length) {
            int chunk = Math.min(SCRATCH_BYTES, length - written);
            scale(pcm, offset + written, scratch, chunk, gain);
            int done = target.write(scratch, 0, chunk);
            written += done;
            if (done < chunk) {
                break;
            }
        }
        return written;
    }

    @Override
    public void start() {
        requireLine().start();
    }

    @Override
    public void stop() {
        requireLine().stop();
    }

    @Override
    public void flush() {
        requireLine().flush();
    }

    @Override
    public long playedFrames() {
        SourceDataLine current = line;
        return current == null ? 0 : current.getLongFramePosition();
    }

    @Override
    public void setGain(float linear) {
        gain = Math.max(0, Math.min(1, linear));
    }

    @Override
    public void setPan(float value) {
        panValue = Math.max(-1, Math.min(1, value));
    }

    @Override
    public void close() {
        SourceDataLine current = line;
        line = null;
        if (current != null) {
            current.stop();
            current.flush();
            current.close();
        }
    }

    private void applyControls() {
        float g = gain;
        if (g != appliedGain) {
            appliedGain = g;
            if (masterGain != null) {
                // Decibels; silence is the control's minimum, not -infinity.
                float db = g <= 0.0001f ? masterGain.getMinimum() : (float) (20 * Math.log10(g));
                masterGain.setValue(Math.max(masterGain.getMinimum(), Math.min(masterGain.getMaximum(), db)));
            } else if (volume != null) {
                volume.setValue(volume.getMinimum() + g * (volume.getMaximum() - volume.getMinimum()));
            }
        }
        float p = panValue;
        if (p != appliedPan && pan != null) {
            appliedPan = p;
            pan.setValue(p);
        }
    }

    /** Copies 16-bit little-endian samples from {@code src} to {@code dst}, scaled by {@code gain}. */
    static void scale(byte[] src, int offset, byte[] dst, int length, float gain) {
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((src[offset + i] & 0xFF) | (src[offset + i + 1] << 8));
            int scaled = Math.round(sample * gain);
            dst[i] = (byte) scaled;
            dst[i + 1] = (byte) (scaled >> 8);
        }
    }

    private SourceDataLine requireLine() {
        SourceDataLine current = line;
        if (current == null) {
            throw new IllegalStateException("Sound line is not open");
        }
        return current;
    }

    private static @Nullable FloatControl control(SourceDataLine line, FloatControl.Type type) {
        return line.isControlSupported(type) ? (FloatControl) line.getControl(type) : null;
    }
}
