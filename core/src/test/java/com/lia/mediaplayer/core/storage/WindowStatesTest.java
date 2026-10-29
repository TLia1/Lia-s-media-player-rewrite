package com.lia.mediaplayer.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.lia.mediaplayer.api.media.RepeatMode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindowStatesTest {

    @Test
    void statesSurviveTheFile() {
        var codec = WindowStates.codec();
        var states = Map.of(
                "video",
                new WindowState(true, 10, 20, 300, 180, RepeatMode.ALL, true, true, 0.6f),
                "audio",
                WindowState.DEFAULT.withFrame(1, 2, 260, 0));
        assertThat(codec.decode(codec.encode(states))).isEqualTo(states);
    }

    @Test
    void aHandEditedEntryKeepsWhatItCan() {
        var json = JsonParser.parseString(
                        "{\"kinds\": {\"video\": {\"x\": \"left\", \"y\": 7, \"repeat\": \"SOMETIMES\"}, \"bad\": 3}}")
                .getAsJsonObject();
        var states = WindowStates.codec().decode(json);
        assertThat(states).containsOnlyKeys("video");
        assertThat(states.get("video").x()).isZero();
        assertThat(states.get("video").y()).isEqualTo(7);
        assertThat(states.get("video").repeat()).isEqualTo(RepeatMode.OFF);
        // A file from before the volume was kept plays at full volume.
        assertThat(states.get("video").volume()).isEqualTo(1f);
    }

    @Test
    void sizesAreNeverNegative() {
        var state = WindowState.DEFAULT.withFrame(0, 0, -5, -1);
        assertThat(state.sized()).isFalse();
        assertThat(state.height()).isZero();
        assertThat(state.withModes(RepeatMode.ONE, true).withQueueOpen(true))
                .isEqualTo(new WindowState(true, 0, 0, 0, 0, RepeatMode.ONE, true, true, 1));
        assertThat(state.withVolume(3).volume()).isEqualTo(1f);
        assertThat(state.withVolume(-1).volume()).isZero();
    }
}
