package com.lia.mediaplayer.core.testing;

import com.lia.mediaplayer.core.CoreOptions;
import com.lia.mediaplayer.core.tools.ToolManager;

/** Engine options for tests: no startup install, no download, no sound card. */
public final class TestCores {

    public static final CoreOptions OFFLINE = new CoreOptions(
            false,
            new ToolManager.Policy(false, false),
            CoreOptions.defaults().host(),
            CoreOptions.defaults().downloads(),
            FakeAudioSink::new);

    private TestCores() {}
}
