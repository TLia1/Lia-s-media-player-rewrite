package com.lia.mediaplayer.core.tools;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The machine the game runs on, as far as finding and downloading tools is concerned.
 * A value rather than calls to {@code System.getProperty}, so the per-OS rules can be tested
 * on any OS.
 *
 * @param properties JVM system properties
 * @param environment environment variables
 */
public record HostSystem(
        Os os,
        boolean aarch64,
        Function<String, @Nullable String> properties,
        Function<String, @Nullable String> environment,
        Path home) {

    public enum Os {
        WINDOWS,
        MAC,
        LINUX
    }

    /** The machine this JVM runs on. */
    public static HostSystem current() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Os os = name.contains("win") ? Os.WINDOWS : name.contains("mac") || name.contains("darwin") ? Os.MAC : Os.LINUX;
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return new HostSystem(
                os,
                arch.contains("aarch64") || arch.contains("arm64"),
                System::getProperty,
                System::getenv,
                Path.of(System.getProperty("user.home", ".")));
    }

    /** {@code ".exe"} on Windows, nothing elsewhere. */
    public String exeSuffix() {
        return os == Os.WINDOWS ? ".exe" : "";
    }

    @Nullable
    String property(String name) {
        return properties.apply(name);
    }

    @Nullable
    String env(String name) {
        return environment.apply(name);
    }
}
