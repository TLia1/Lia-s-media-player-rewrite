package com.lia.mediaplayer.core.tools;

import java.util.Map;

/** Hands out the tools playback needs. */
@FunctionalInterface
public interface ToolProvider {

    /**
     * The usable copy of {@code tool}, waiting for the startup install if it is still running.
     * Blocking; call it from a background thread.
     *
     * @throws ToolMissingException if there is none
     */
    LocatedTool require(Tool tool) throws ToolMissingException;

    /** A provider that knows exactly these tools, for tests and harnesses. */
    static ToolProvider fixed(Map<Tool, LocatedTool> tools) {
        var copy = Map.copyOf(tools);
        return tool -> {
            LocatedTool located = copy.get(tool);
            if (located == null) {
                throw new ToolMissingException(tool);
            }
            return located;
        };
    }
}
