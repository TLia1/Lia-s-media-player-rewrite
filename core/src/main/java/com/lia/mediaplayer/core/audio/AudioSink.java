package com.lia.mediaplayer.core.audio;

/**
 * Where a player's sound goes. {@link JavaSoundSink} in the game; a fake that keeps time in
 * tests.
 *
 * <p>One thread, the audio pump, calls {@link #write}; {@link #playedFrames} is called from
 * any thread; the rest from the player's actor. PCM arrives as {@code byte[]} because that is
 * what Java Sound takes, and the pump writes straight out of its ring's array.
 */
public interface AudioSink extends AutoCloseable {

    /** Opens the line. The sink stays stopped until {@link #start}. */
    void open(PcmFormat format) throws AudioException;

    /**
     * Queues whole frames for playback and returns how many bytes were taken. Blocks while the
     * device buffer is full; returns early, with fewer bytes, if the sink is stopped or
     * flushed meanwhile.
     */
    int write(byte[] pcm, int offset, int length);

    void start();

    void stop();

    /** Discards what is queued and not yet played. */
    void flush();

    /** Frames that have reached the speaker since the line opened. The master clock. */
    long playedFrames();

    /** Linear gain, 0 to 1. Applied by the pump's next write. */
    void setGain(float linear);

    /** -1 (left) to 1 (right). Ignored where the line has no pan. */
    void setPan(float pan);

    @Override
    void close();
}
