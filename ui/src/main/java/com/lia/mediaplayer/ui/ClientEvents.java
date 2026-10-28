package com.lia.mediaplayer.ui;

/**
 * The moments the game tells the mod about, called by the loader bridges on the client
 * thread. The reverse direction of {@link com.lia.mediaplayer.core.platform.Platform}.
 *
 * <p>Grows with the milestones: rendering, input, chat and screen hooks are added as the
 * code that handles them is written.
 */
public interface ClientEvents {

    /** End of a client tick. */
    void tick();

    /** The player left a world (disconnect, or back to the title screen). */
    void worldLeft();
}
