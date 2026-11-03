package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.ui.input.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;

/** Translates native mouse button codes into the UI convention. */
final class McMouseButtons {

    private McMouseButtons() {}

    /** The UI uses left=0, right=1, middle=2; SDL (26.3+) uses left=1, middle=2, right=3. */
    static int normalize(int button) {
        //? if <26.3 {
        return button;
        //?} else {
        /*return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> InputEvent.PRIMARY_BUTTON;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button - 1;
        };
        *///?}
    }

}
