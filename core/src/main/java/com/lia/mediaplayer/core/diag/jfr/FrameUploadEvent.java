package com.lia.mediaplayer.core.diag.jfr;

import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** One video frame copied to its texture, on the client thread. */
@Name("liasmediaplayer.FrameUpload")
@Label("Frame Upload")
@Category(Events.CATEGORY)
@Description("A video frame uploaded to the GPU")
public final class FrameUploadEvent extends Event {

    @Label("Width")
    int width;

    @Label("Height")
    int height;

    @Label("Size")
    @DataAmount
    long bytes;

    public static FrameUploadEvent start(int width, int height) {
        var event = new FrameUploadEvent();
        if (event.isEnabled()) {
            event.width = width;
            event.height = height;
            event.bytes = 4L * width * height;
            event.begin();
        }
        return event;
    }

    public void finish() {
        if (shouldCommit()) {
            commit();
        }
    }
}
