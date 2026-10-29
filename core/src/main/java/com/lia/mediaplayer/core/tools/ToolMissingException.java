package com.lia.mediaplayer.core.tools;

import java.io.IOException;

/** A tool needed for playback is not installed and could not be downloaded. */
public final class ToolMissingException extends IOException {

    private static final long serialVersionUID = 1L;

    private final Tool tool;

    public ToolMissingException(Tool tool) {
        // "is required to play" is what PlaybackError reads as TOOL_MISSING.
        super(tool.baseName() + " is required to play this. It could not be found, and downloading it into the "
                + "game folder failed (no internet access?). Install it and add it to PATH, or start the game with "
                + "-D" + tool.overrideProperty() + "=<path>.");
        this.tool = tool;
    }

    public Tool tool() {
        return tool;
    }
}
