package com.lia.mediaplayer.api;

import java.util.Comparator;

/**
 * A semantic version of this API.
 *
 * <p>An addon built against version {@code X.Y.Z} runs on any installed API with the same
 * major version and a minor/patch at least as high — see {@link #satisfies(ApiVersion)}.
 */
public record ApiVersion(int major, int minor, int patch) implements Comparable<ApiVersion> {

    /** The version of the API this jar implements. */
    public static final ApiVersion CURRENT = new ApiVersion(4, 0, 0);

    private static final Comparator<ApiVersion> ORDER = Comparator.comparingInt(ApiVersion::major)
            .thenComparingInt(ApiVersion::minor)
            .thenComparingInt(ApiVersion::patch);

    public ApiVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                    "Version components must not be negative: " + major + "." + minor + "." + patch);
        }
    }

    /**
     * Parses {@code "major.minor.patch"}. A pre-release or build suffix ({@code -beta.1},
     * {@code +build}) is ignored.
     *
     * @throws IllegalArgumentException if the text is not a version
     */
    public static ApiVersion parse(String text) {
        String core = text.strip().split("[-+]", 2)[0];
        String[] parts = core.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Not a major.minor.patch version: '" + text + "'");
        }
        try {
            return new ApiVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a major.minor.patch version: '" + text + "'", e);
        }
    }

    /** Whether code built against {@code required} can run on this version. */
    public boolean satisfies(ApiVersion required) {
        return major == required.major && compareTo(required) >= 0;
    }

    @Override
    public int compareTo(ApiVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
