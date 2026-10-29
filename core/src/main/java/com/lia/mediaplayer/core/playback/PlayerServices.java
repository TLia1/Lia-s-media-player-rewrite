package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.core.audio.AudioSink;
import com.lia.mediaplayer.core.ffmpeg.ProbeCache;
import com.lia.mediaplayer.core.resolve.ResolverChain;
import com.lia.mediaplayer.core.tools.ProcessRunner;
import com.lia.mediaplayer.core.tools.ToolProvider;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * What a player needs from the engine, handed to it whole.
 *
 * @param clientThread where state changes are reported
 * @param audioSinks a new, unopened sink for each player that has sound
 * @param nanoTime the monotonic clock; {@code System::nanoTime} outside tests
 */
public record PlayerServices(
        ResolverChain resolver,
        ProbeCache probes,
        ToolProvider tools,
        ProcessRunner processes,
        Supplier<AudioSink> audioSinks,
        Executor io,
        Executor clientThread,
        LongSupplier nanoTime) {}
