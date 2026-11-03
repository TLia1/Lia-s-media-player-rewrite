package com.lia.mediaplayer.core.diag.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** One link resolved to the streams ffmpeg plays: yt-dlp, a page fetch, or the cache. */
@Name("liasmediaplayer.Resolve")
@Label("Resolve")
@Category(Events.CATEGORY)
@Description("A link resolved to streams")
public final class ResolveEvent extends Event {

    @Label("Link")
    String url = "";

    @Label("From the cache")
    boolean cached;

    @Label("Succeeded")
    boolean succeeded;

    /** Starts timing the resolution of {@code url}. */
    public static ResolveEvent start(String url) {
        var event = new ResolveEvent();
        if (event.isEnabled()) {
            event.url = url;
            event.begin();
        }
        return event;
    }

    /** Records how it ended, if a recording asks for it. */
    public void finish(boolean fromCache, boolean ok) {
        if (shouldCommit()) {
            cached = fromCache;
            succeeded = ok;
            commit();
        }
    }
}
