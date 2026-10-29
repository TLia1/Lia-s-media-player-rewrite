package com.lia.mediaplayer.core.tools;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Where the tools stand. Published on the event bus at every change, so the settings
 * screen and error panels show progress instead of polling.
 */
public sealed interface ToolState {

    /** Looking for installed copies. */
    record Locating() implements ToolState {}

    /**
     * Fetching a tool.
     *
     * @param progress from 0 to 1, or -1 when the server did not say how large it is
     */
    record Downloading(Tool tool, double progress) implements ToolState {}

    /** Every tool is usable. */
    record Ready(Map<Tool, LocatedTool> tools) implements ToolState {
        public Ready {
            tools = Map.copyOf(tools);
        }
    }

    /**
     * At least one tool could not be found or downloaded.
     *
     * @param found the tools that are usable
     * @param checked for each missing tool, every place it was looked for
     */
    record Missing(Map<Tool, LocatedTool> found, Map<Tool, List<Path>> checked) implements ToolState {
        public Missing {
            found = Map.copyOf(found);
            checked = Map.copyOf(checked);
        }
    }
}
