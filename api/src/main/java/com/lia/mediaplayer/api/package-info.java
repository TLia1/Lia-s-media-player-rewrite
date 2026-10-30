/**
 * The public API of Lia's Media Player, version 4.
 *
 * <p>An addon implements {@link com.lia.mediaplayer.api.MediaPlayerAddon}, registers what it
 * adds through the {@link com.lia.mediaplayer.api.AddonContext} it is handed, and talks to the
 * running mod through {@link com.lia.mediaplayer.api.MediaPlayer}.
 *
 * <p>Nothing in this package names a Minecraft or mod-loader type, so one API jar serves
 * every Minecraft version and loader the mod is built for, and an addon that only uses it
 * compiles once.
 */
@NullMarked
package com.lia.mediaplayer.api;

import org.jspecify.annotations.NullMarked;
