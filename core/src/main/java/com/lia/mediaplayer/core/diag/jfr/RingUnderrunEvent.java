package com.lia.mediaplayer.core.diag.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * A playing player ran out of decoded sound or picture before its stream ended: the network
 * or the decoder fell behind. Reported by the player's actor, so the audio pump stays free of
 * allocation.
 */
@Name("liasmediaplayer.RingUnderrun")
@Label("Ring Underrun")
@Category(Events.CATEGORY)
@Description("A player ran out of decoded media while playing")
public final class RingUnderrunEvent extends Event {

    @Label("Player")
    long player;

    @Label("Track")
    String track = "";

    @Label("Count")
    long count;

    /** {@code count} new underruns of {@code track} ("audio" or "video") in player {@code player}. */
    public static void report(long player, String track, long count) {
        var event = new RingUnderrunEvent();
        if (event.shouldCommit()) {
            event.player = player;
            event.track = track;
            event.count = count;
            event.commit();
        }
    }
}
