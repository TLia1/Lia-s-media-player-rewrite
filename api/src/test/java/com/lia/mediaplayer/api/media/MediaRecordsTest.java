package com.lia.mediaplayer.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lia.mediaplayer.api.library.PlaylistView;
import com.lia.mediaplayer.api.policy.Interceptor;
import com.lia.mediaplayer.api.policy.PlayOrigin;
import com.lia.mediaplayer.api.queue.QueueEntry;
import com.lia.mediaplayer.api.source.MediaRef;
import com.lia.mediaplayer.api.text.Text;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MediaRecordsTest {

    @Test
    void aRequestIsForAWebLink() {
        var request = MediaRequest.of("https://example.com/v.mp4");
        assertThat(request.audioOnly()).isFalse();
        assertThat(request.newWindow()).isFalse();
        var changed = request.withAudioOnly(true).withNewWindow(true);
        assertThat(changed).isEqualTo(new MediaRequest("https://example.com/v.mp4", true, true));
        assertThatThrownBy(() -> MediaRequest.of("file:///etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QueueEntry("-x", MediaKind.VIDEO, "a:b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSnapshotNeverGoesBeforeTheStart() {
        var snapshot = new PlaybackSnapshot(
                PlaybackState.PLAYING, Duration.ofSeconds(-1), Optional.empty(), Optional.empty(), true, true);
        assertThat(snapshot.position()).isZero();
        assertThat(PlaybackSnapshot.IDLE.state()).isEqualTo(PlaybackState.IDLE);
    }

    @Test
    void activeStatesAreTheOnesOnTheWayToPlayingOrPlaying() {
        assertThat(List.of(PlaybackState.values()).stream().filter(PlaybackState::isActive))
                .containsExactly(
                        PlaybackState.RESOLVING,
                        PlaybackState.BUFFERING,
                        PlaybackState.PLAYING,
                        PlaybackState.PAUSED,
                        PlaybackState.SEEKING);
    }

    @Test
    void anInterceptorAllowsWhatItDoesNotOverride() {
        Interceptor nothing = () -> "myaddon:nothing";
        var ref = new MediaRef("https://example.com/v.mp4", MediaKind.VIDEO, "a:b", Text.literal("[v]"));
        assertThat(nothing.allowChatLink(ref, Optional.empty())).isTrue();
        assertThat(nothing.allowPlay(ref, PlayOrigin.API)).isTrue();
    }

    @Test
    void aPlaylistViewIsACopy() {
        var urls = new ArrayList<>(List.of("https://example.com/a"));
        var view = new PlaylistView("id", "Mix", urls);
        urls.clear();
        assertThat(view.urls()).hasSize(1);
    }
}
