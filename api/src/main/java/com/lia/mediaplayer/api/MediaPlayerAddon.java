package com.lia.mediaplayer.api;

/**
 * The one interface an addon implements. The mod calls {@link #register} once, at startup,
 * before the game's key mappings are settled — on both loaders at the same moment:
 *
 * <ul>
 *   <li><b>Fabric</b>: declare the class as a {@code "liasmediaplayer"} entrypoint in {@code
 *       fabric.mod.json}. No initializer of your own is needed.
 *   <li><b>NeoForge</b>: call {@link LiasMediaPlayerApi#registerAddon} from your {@code @Mod}
 *       constructor.
 * </ul>
 *
 * <p>An addon that only uses this API is plain Java: the same class serves both loaders and
 * every Minecraft version the mod is built for.
 *
 * @since 4.0.0
 */
@FunctionalInterface
public interface MediaPlayerAddon {

    /**
     * Registers what the addon adds. Runs on the client thread. The registries close when it
     * returns; anything that needs the running mod goes in {@link AddonContext#onReady}.
     *
     * <p>An exception thrown here is logged with the addon's id; what was registered before it
     * stays.
     */
    void register(AddonContext context);
}
