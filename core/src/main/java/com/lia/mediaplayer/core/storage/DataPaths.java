package com.lia.mediaplayer.core.storage;

import com.lia.mediaplayer.core.ModInfo;
import java.nio.file.Path;

/**
 * Where the mod keeps its files: one folder under the game directory.
 *
 * <pre>
 * &lt;gamedir&gt;/liasmediaplayer/
 *   settings.json   library.json   windows.json
 *   cache/          bin/           logs/
 * </pre>
 *
 * @param root the mod's folder, normally {@code <gamedir>/liasmediaplayer}
 */
public record DataPaths(Path root) {

    public DataPaths {
        root = root.toAbsolutePath().normalize();
    }

    /** The mod's folder under {@code gameDirectory}. */
    public static DataPaths under(Path gameDirectory) {
        return new DataPaths(gameDirectory.resolve(ModInfo.MOD_ID));
    }

    public Path settings() {
        return root.resolve("settings.json");
    }

    public Path library() {
        return root.resolve("library.json");
    }

    public Path windows() {
        return root.resolve("windows.json");
    }

    /** Thumbnails and titles; safe for the user to delete. */
    public Path cache() {
        return root.resolve("cache");
    }

    /** The titles of links and where their pictures are, in one file of the cache. */
    public Path titles() {
        return cache().resolve("titles.json");
    }

    /** Small JPEGs of the links in lists, under the cache. */
    public Path thumbnails() {
        return cache().resolve("thumbnails");
    }

    /** Downloaded ffmpeg, ffprobe and yt-dlp. */
    public Path bin() {
        return root.resolve("bin");
    }

    public Path logs() {
        return root.resolve("logs");
    }
}
