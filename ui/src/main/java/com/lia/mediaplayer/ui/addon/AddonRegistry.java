package com.lia.mediaplayer.ui.addon;

import com.lia.mediaplayer.api.Ids;
import com.lia.mediaplayer.api.Registry;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One registry of one addon: checks that each entry's id is namespaced by the addon and not
 * taken, then hands it on. Open only while the addon's {@code register} runs.
 */
final class AddonRegistry<T> implements Registry<T> {

    private final String addonId;
    private final String what;
    private final Function<? super T, String> idOf;
    private final Set<String> taken;
    private final BooleanSupplier open;
    private final Consumer<? super T> sink;

    /**
     * @param what what an entry is, for messages: "Theme"
     * @param taken the ids of this kind registered so far, by every addon
     */
    AddonRegistry(
            String addonId,
            String what,
            Function<? super T, String> idOf,
            Set<String> taken,
            BooleanSupplier open,
            Consumer<? super T> sink) {
        this.addonId = addonId;
        this.what = what;
        this.idOf = idOf;
        this.taken = taken;
        this.open = open;
        this.sink = sink;
    }

    @Override
    public T register(T entry) {
        if (!open.getAsBoolean()) {
            throw new IllegalStateException(
                    what + "s are registered from MediaPlayerAddon.register, not after (" + addonId + ")");
        }
        String id = Ids.require(idOf.apply(entry), what + " id");
        if (!Ids.owner(id).equals(addonId)) {
            throw new IllegalArgumentException(
                    what + " id " + id + " must be in the addon's namespace: " + addonId + ":…");
        }
        if (!taken.add(id)) {
            throw new IllegalArgumentException(what + " already registered: " + id);
        }
        sink.accept(entry);
        return entry;
    }
}
