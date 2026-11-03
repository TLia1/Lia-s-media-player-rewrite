package com.lia.mediaplayer.core.diag.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** One value made for a cache: a picture, a title, a thumbnail. Timed from the ask to the answer. */
@Name("liasmediaplayer.CacheLoad")
@Label("Cache Load")
@Category(Events.CATEGORY)
@Description("A value loaded into one of the mod's caches")
public final class CacheLoadEvent extends Event {

    @Label("Cache")
    String cache = "";

    @Label("Key")
    String key = "";

    @Label("Weight")
    long weight;

    @Label("Succeeded")
    boolean succeeded;

    public static CacheLoadEvent start(String cache, Object key) {
        var event = new CacheLoadEvent();
        if (event.isEnabled()) {
            event.cache = cache;
            event.key = String.valueOf(key);
            event.begin();
        }
        return event;
    }

    public void finish(long valueWeight, boolean ok) {
        if (shouldCommit()) {
            weight = valueWeight;
            succeeded = ok;
            commit();
        }
    }
}
