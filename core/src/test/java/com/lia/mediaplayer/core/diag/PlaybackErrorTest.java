package com.lia.mediaplayer.core.diag;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.core.diag.PlaybackError.Cause;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** The wording below is what the tools actually print; v2's samples, kept. */
class PlaybackErrorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ERROR: [youtube] dQw4w9WgXcQ: Unable to extract player response; please report this issue | EXTRACTOR_OUTDATED
            WARNING: [youtube] nsig extraction failed: Some formats may be missing                     | EXTRACTOR_OUTDATED
            ERROR: unable to download API page; please update to the latest version                    | EXTRACTOR_OUTDATED
            Cannot run program "yt-dlp": error=2, No such file or directory                           | TOOL_MISSING
            yt-dlp is required to play this. It could not be found                                     | TOOL_MISSING
            ERROR: [youtube] Sign in to confirm you're not a bot. Use --cookies-from-browser            | SIGN_IN_REQUIRED
            ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate        | AGE_RESTRICTED
            ERROR: [youtube] abc: Private video. Sign in if you've been granted access                 | PRIVATE
            PRIVATE VIDEO                                                                              | PRIVATE
            ERROR: [youtube] abc: Video unavailable. The uploader has not made this video available in your country | GEO_BLOCKED
            ERROR: [youtube] abc: This live event will begin in 3 hours.                              | LIVE_NOT_STARTED
            ERROR: [youtube] abc: Requested format is not available                                   | UNSUPPORTED_FORMAT
            ERROR: [youtube] abc: Video unavailable                                                   | UNAVAILABLE
            https://rr3---sn-xxx.googlevideo.com/videoplayback: Server returned 403 Forbidden         | LINK_EXPIRED
            [http @ 0x1] HTTP error 404 Not Found                                                      | NOT_FOUND
            java.net.UnknownHostException: www.youtube.com                                             | NETWORK
            [tcp @ 0x55f] Connection refused                                                           | NETWORK
            [matroska @ 0x1] Invalid data found when processing input                                  | UNSUPPORTED_FORMAT
            Stream map '0:a:0' matches no streams.                                                     | UNSUPPORTED_FORMAT
            ffprobe could not read the stream                                                          | DECODE_FAILED
            ffmpeg exited with code 1                                                                  | DECODE_FAILED
            yt-dlp timed out after 25 s                                                                | TIMEOUT
            No audio device plays 48000 Hz, 2 ch                                                       | NO_AUDIO_DEVICE
            something nobody has ever printed                                                          | UNKNOWN
            """)
    void readsWhatTheToolsSay(String raw, Cause expected) {
        assertThat(PlaybackError.classify(raw).cause()).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(Cause.class)
    void everyCauseHasStableKeys(Cause cause) {
        assertThat(cause.messageKey()).isEqualTo("error.liasmediaplayer.cause." + cause.id());
        assertThat(cause.hintKey()).isEqualTo(cause.messageKey() + ".hint");
        assertThat(cause.suggestsToolUpdate())
                .isEqualTo(cause == Cause.TOOL_MISSING || cause == Cause.EXTRACTOR_OUTDATED);
    }

    @org.junit.jupiter.api.Test
    void readsTheWholeCauseChainAndKeepsTheRawText() {
        var failure = new CompletionException(new IOException("yt-dlp: ERROR: Private video"));
        PlaybackError error = PlaybackError.of(failure);
        assertThat(error.cause()).isEqualTo(Cause.PRIVATE);
        assertThat(error.detail()).contains("Private video");
        assertThat(error.shortText()).startsWith("private: ");
        assertThat(PlaybackError.of(new IllegalStateException()).cause()).isEqualTo(Cause.UNKNOWN);
    }
}
