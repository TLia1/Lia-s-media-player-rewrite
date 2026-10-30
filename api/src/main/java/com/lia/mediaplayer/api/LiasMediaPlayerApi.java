package com.lia.mediaplayer.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a NeoForge addon hands its {@link MediaPlayerAddon} to the mod, from its {@code @Mod}
 * constructor:
 *
 * <pre>{@code
 * @Mod(value = "myaddon", dist = Dist.CLIENT)
 * public final class MyAddonMod {
 *     public MyAddonMod() {
 *         LiasMediaPlayerApi.registerAddon("myaddon", new MyAddon());
 *     }
 * }
 * }</pre>
 *
 * <p>The mod takes the addons handed over once every mod is constructed, and registers them
 * all then, whichever mod was constructed first. Fabric addons use the {@code
 * "liasmediaplayer"} entrypoint instead (see {@link MediaPlayerAddon}).
 *
 * <p>This class holds the only state of the API: the addons waiting to be taken.
 *
 * @since 4.0.0
 */
public final class LiasMediaPlayerApi {

    private static final Object LOCK = new Object();
    private static final List<PendingAddon> PENDING = new ArrayList<>();
    private static boolean taken;

    private LiasMediaPlayerApi() {}

    /** An addon handed over, with the id it registers under. */
    public record PendingAddon(String addonId, MediaPlayerAddon addon) {
        public PendingAddon {
            if (!Ids.isOwner(addonId)) {
                throw new IllegalArgumentException(
                        "Addon id must be lower case letters, digits, '_', '.' or '-': " + addonId);
            }
        }
    }

    /**
     * Hands {@code addon} to the mod, to be registered under {@code addonId} (your mod id).
     *
     * @throws IllegalStateException if called after the mod has registered the addons — from
     *     anywhere but a mod constructor
     */
    public static void registerAddon(String addonId, MediaPlayerAddon addon) {
        var pending = new PendingAddon(addonId, addon);
        synchronized (LOCK) {
            if (taken) {
                throw new IllegalStateException("Lia's Media Player has already registered its addons; call"
                        + " registerAddon from your mod's constructor (" + addonId + ")");
            }
            PENDING.add(pending);
        }
    }

    /**
     * The addons handed over so far; from then on {@link #registerAddon} refuses. Called once by
     * the mod itself: an addon calling it would take the other addons' place.
     */
    public static List<PendingAddon> takePending() {
        synchronized (LOCK) {
            taken = true;
            var all = List.copyOf(PENDING);
            PENDING.clear();
            return all;
        }
    }

    /** Opens the door again. Tests only: the mod takes the addons once per game. */
    static void resetForTests() {
        synchronized (LOCK) {
            taken = false;
            PENDING.clear();
        }
    }
}
