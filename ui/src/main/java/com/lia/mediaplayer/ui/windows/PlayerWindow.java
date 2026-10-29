package com.lia.mediaplayer.ui.windows;

/** A window that plays: the video window and the audio bar, which the shortcuts and hotkeys drive alike. */
public interface PlayerWindow {

    PlaybackControl control();

    boolean isQueueOpen();

    void setQueueOpen(boolean open);

    /** Mutes, or brings back the volume from before. */
    void toggleMute();
}
