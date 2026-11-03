package com.lia.mediaplayer.core.playback;

/**
 * A player's counters, for the debug overlay. Read from any thread; each field is current, the
 * whole not necessarily from one instant.
 *
 * @param decodeHeight the height frames are decoded at, 0 when the picture is not decoded
 * @param fps the rate frames are decoded at, 0 when the picture is not decoded
 * @param framesBuffered decoded frames waiting for their time
 * @param frameCapacity how many the ring holds
 * @param soundBufferedMillis decoded sound waiting to be played
 * @param soundCapacityMillis how much the sound ring holds
 * @param droppedFrames frames decoded and never shown, since the player started
 * @param lateMicros how far behind the clock the last frame taken for display was: the picture's
 *     lag behind the sound, at most a frame's interval when all is well
 * @param soundUnderruns times the sound ran dry while playing
 * @param pictureUnderruns times the picture ran dry while playing
 * @param sessions ffmpeg processes started: one, plus one per seek, long pause or resize
 */
public record PlayerStats(
        int decodeHeight,
        double fps,
        int framesBuffered,
        int frameCapacity,
        long soundBufferedMillis,
        long soundCapacityMillis,
        long droppedFrames,
        long lateMicros,
        long soundUnderruns,
        long pictureUnderruns,
        long sessions) {

    /** Nothing decoded, nothing counted. */
    public static final PlayerStats NONE = new PlayerStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
}
