package com.lia.mediaplayer.core;

import com.lia.mediaplayer.core.audio.AudioSink;
import com.lia.mediaplayer.core.audio.JavaSoundSink;
import com.lia.mediaplayer.core.tools.DownloadSources;
import com.lia.mediaplayer.core.tools.HostSystem;
import com.lia.mediaplayer.core.tools.ToolManager;
import java.util.function.Supplier;

/**
 * How the engine is built. The game uses {@link #defaults()}; tests and harnesses swap the
 * sound card, the download servers or the startup install.
 *
 * @param installTools find and download the tools as soon as the engine starts
 * @param audioSinks a new sound line for each player
 */
public record CoreOptions(
        boolean installTools,
        ToolManager.Policy toolPolicy,
        HostSystem host,
        DownloadSources downloads,
        Supplier<AudioSink> audioSinks) {

    public static CoreOptions defaults() {
        return new CoreOptions(
                true, ToolManager.Policy.DEFAULT, HostSystem.current(), DownloadSources.official(), JavaSoundSink::new);
    }

    public CoreOptions withInstallTools(boolean install) {
        return new CoreOptions(install, toolPolicy, host, downloads, audioSinks);
    }

    public CoreOptions withAudioSinks(Supplier<AudioSink> sinks) {
        return new CoreOptions(installTools, toolPolicy, host, downloads, sinks);
    }
}
