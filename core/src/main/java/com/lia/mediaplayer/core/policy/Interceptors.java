package com.lia.mediaplayer.core.policy;

import com.lia.mediaplayer.api.policy.Interceptor;
import com.lia.mediaplayer.api.policy.PlayOrigin;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.core.addon.AddonGuard;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * The addons' interceptors, asked after the player's own {@link LinkFilter}: the first that
 * refuses wins. One that throws is taken to allow, through the {@link AddonGuard}. With none
 * registered, the common case, every question is one empty loop. Safe from any thread.
 */
public final class Interceptors {

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public void add(Interceptor interceptor, AddonGuard guard) {
        entries.add(new Entry(interceptor, guard));
    }

    /** Whether a chat link of {@code sender} (null for a server message) becomes a label. */
    public boolean allowChatLink(MediaRef ref, @Nullable String sender) {
        Optional<String> from = Optional.ofNullable(sender);
        for (Entry entry : entries) {
            if (!entry.guard().call(entry.id(), () -> entry.interceptor().allowChatLink(ref, from), true)) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code ref} may play, asked by {@code origin}. */
    public boolean allowPlay(MediaRef ref, PlayOrigin origin) {
        for (Entry entry : entries) {
            if (!entry.guard().call(entry.id(), () -> entry.interceptor().allowPlay(ref, origin), true)) {
                return false;
            }
        }
        return true;
    }

    private record Entry(Interceptor interceptor, AddonGuard guard, String id) {
        Entry(Interceptor interceptor, AddonGuard guard) {
            // Read once: the id names the registration in the guard's counts.
            this(interceptor, guard, interceptor.id());
        }
    }
}
