package com.lia.mediaplayer.core.diag.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** From starting ffmpeg to the first picture or sound it produced: start, seek and resume latency. */
@Name("liasmediaplayer.SessionStart")
@Label("Session Start")
@Category(Events.CATEGORY)
@Description("ffmpeg started, until its first frame or sound")
public final class SessionStartEvent extends Event {

    @Label("Player")
    long player;

    @Label("Session")
    long session;

    @Label("Seek")
    boolean seek;

    @Label("Picture")
    boolean video;

    @Label("Sound")
    boolean audio;

    @Label("Decoded height")
    int height;

    @Label("Produced data")
    boolean succeeded;

    public static SessionStartEvent start(long player, long session, boolean seek, boolean video, boolean audio) {
        var event = new SessionStartEvent();
        if (event.isEnabled()) {
            event.player = player;
            event.session = session;
            event.seek = seek;
            event.video = video;
            event.audio = audio;
            event.begin();
        }
        return event;
    }

    /** The session produced its first data, or gave up; {@code height} is 0 for sound only. */
    public void finish(int decodedHeight, boolean ok) {
        if (shouldCommit()) {
            height = decodedHeight;
            succeeded = ok;
            commit();
        }
    }
}
