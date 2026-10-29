package com.lia.mediaplayer.core.tools;

import java.nio.file.Path;

/**
 * A usable copy of a tool.
 *
 * @param path what to run: an absolute path, or the bare command name when the tool was
 *     found only through the launcher's {@code PATH}
 * @param version the first line the program printed when asked for its version
 */
public record LocatedTool(Tool tool, Path path, String version) {

    /** The path as the first element of a command line. */
    public String command() {
        return path.toString();
    }
}
