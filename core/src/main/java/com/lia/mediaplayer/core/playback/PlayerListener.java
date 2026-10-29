package com.lia.mediaplayer.core.playback;

/** Told about every state change of a player, on the client thread, in order. */
@FunctionalInterface
public interface PlayerListener {

    PlayerListener NONE = (player, state) -> {};

    void stateChanged(Player player, PlayerState state);
}
