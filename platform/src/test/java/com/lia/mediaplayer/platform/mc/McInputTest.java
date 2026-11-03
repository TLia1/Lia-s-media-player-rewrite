package com.lia.mediaplayer.platform.mc;

import static org.assertj.core.api.Assertions.assertThat;

import com.lia.mediaplayer.ui.input.InputEvent;
import org.junit.jupiter.api.Test;

class McInputTest {

    @Test
    void mouseButtonsMatchTheUiConvention() {
        //? if <26.3 {
        assertThat(McMouseButtons.normalize(0)).isEqualTo(InputEvent.PRIMARY_BUTTON);
        assertThat(McMouseButtons.normalize(1)).isEqualTo(1);
        assertThat(McMouseButtons.normalize(2)).isEqualTo(2);
        assertThat(McMouseButtons.normalize(3)).isEqualTo(3);
        assertThat(McMouseButtons.normalize(7)).isEqualTo(7);
        //?} else {
        /*assertThat(McMouseButtons.normalize(1)).isEqualTo(InputEvent.PRIMARY_BUTTON);
        assertThat(McMouseButtons.normalize(3)).isEqualTo(1);
        assertThat(McMouseButtons.normalize(2)).isEqualTo(2);
        assertThat(McMouseButtons.normalize(4)).isEqualTo(3);
        assertThat(McMouseButtons.normalize(8)).isEqualTo(7);
        *///?}
    }
}
