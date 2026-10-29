package com.lia.mediaplayer.core.tools;

/**
 * How a program run by {@link ProcessRunner#run} ended.
 *
 * @param stdout everything it wrote to stdout, as UTF-8
 * @param stderr the last lines it wrote to stderr
 */
public record ProcessResult(int exitCode, String stdout, String stderr) {

    public boolean succeeded() {
        return exitCode == 0;
    }
}
