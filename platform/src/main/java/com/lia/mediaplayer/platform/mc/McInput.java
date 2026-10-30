package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
//? if <26.3
import org.lwjgl.glfw.GLFW;

/**
 * The game's key codes and modifier bits, as the UI's {@link Key} and {@link Mods}.
 *
 * <p>Codes are read through the game's {@link InputConstants}, never the windowing library's:
 * up to 26.2 the game ran on GLFW and its codes were GLFW's, and 26.3 moved it to SDL, whose
 * codes are scancodes ({@code A} is 4, not 65) and whose modifier bits are pairs (left and
 * right). Only what {@code InputConstants} did not name on every version is spelled out below.
 */
public final class McInput {

    /** Whether Cmd plays the part of Ctrl. Read from the game's own idea of the platform. */
    private static final boolean MAC = Util.getPlatform() == Util.OS.OSX;

    //? if <26.3 {
    private static final int MOD_SHIFT = GLFW.GLFW_MOD_SHIFT;
    private static final int MOD_CONTROL = GLFW.GLFW_MOD_CONTROL;
    private static final int MOD_ALT = GLFW.GLFW_MOD_ALT;
    private static final int MOD_SUPER = GLFW.GLFW_MOD_SUPER;
    private static final int KEY_LEFT_SUPER = GLFW.GLFW_KEY_LEFT_SUPER;
    private static final int KEY_RIGHT_SUPER = GLFW.GLFW_KEY_RIGHT_SUPER;
    //?} else {
    /*private static final int MOD_SHIFT = InputConstants.MOD_SHIFT;
    private static final int MOD_CONTROL = InputConstants.MOD_CONTROL;
    private static final int MOD_ALT = InputConstants.MOD_ALT;
    private static final int MOD_SUPER = InputConstants.MOD_SUPER;
    private static final int KEY_LEFT_SUPER = InputConstants.KEY_LGUI;
    private static final int KEY_RIGHT_SUPER = InputConstants.KEY_RGUI;
    *///?}

    private McInput() {}

    /** The modifiers in a modifier bit set, as input events carry it. */
    public static Mods mods(int bits) {
        return new Mods(
                (bits & MOD_SHIFT) != 0, (bits & MOD_CONTROL) != 0, (bits & MOD_ALT) != 0, (bits & MOD_SUPER) != 0, MAC);
    }

    /** The modifiers held right now, for an event that does not carry them (a press, a drag). */
    public static Mods currentMods() {
        return new Mods(
                down(InputConstants.KEY_LSHIFT) || down(InputConstants.KEY_RSHIFT),
                down(InputConstants.KEY_LCONTROL) || down(InputConstants.KEY_RCONTROL),
                down(InputConstants.KEY_LALT) || down(InputConstants.KEY_RALT),
                down(KEY_LEFT_SUPER) || down(KEY_RIGHT_SUPER),
                MAC);
    }

    /**
     * Whether {@code key} is held. The game's window handle went from a {@code long} to its
     * {@code Window} (1.21.11; unbisected), then out of the call altogether (26.3).
     */
    private static boolean down(int key) {
        //? if <1.21.11 {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
        //?} elif <26.3 {
        /*return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
        *///?} else
        /*return InputConstants.isKeyDown(key);*/
    }

    public static InputEvent.PointerDown press(double x, double y, int button) {
        return new InputEvent.PointerDown(x, y, button, currentMods(), 1);
    }

    public static InputEvent.PointerUp release(double x, double y, int button) {
        return new InputEvent.PointerUp(x, y, button, currentMods());
    }

    public static InputEvent.Scroll scroll(double x, double y, double delta) {
        return new InputEvent.Scroll(x, y, delta, currentMods());
    }

    public static InputEvent.KeyDown key(int keyCode, int modifiers) {
        return new InputEvent.KeyDown(key(keyCode), mods(modifiers));
    }

    /** Letters run A to Z and digits 1 to 9 in both GLFW's and SDL's codes; 0 sits after 9 in SDL's. */
    static Key key(int keyCode) {
        if (keyCode >= InputConstants.KEY_A && keyCode <= InputConstants.KEY_Z) {
            return Key.letter((char) ('A' + (keyCode - InputConstants.KEY_A)));
        }
        if (keyCode >= InputConstants.KEY_1 && keyCode <= InputConstants.KEY_9) {
            return Key.digit(1 + keyCode - InputConstants.KEY_1);
        }
        return switch (keyCode) {
            case InputConstants.KEY_0 -> Key.digit(0);
            case InputConstants.KEY_SPACE -> Key.SPACE;
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> Key.ENTER;
            case InputConstants.KEY_ESCAPE -> Key.ESCAPE;
            case InputConstants.KEY_TAB -> Key.TAB;
            case InputConstants.KEY_BACKSPACE -> Key.BACKSPACE;
            case InputConstants.KEY_DELETE -> Key.DELETE;
            case InputConstants.KEY_LEFT -> Key.LEFT;
            case InputConstants.KEY_RIGHT -> Key.RIGHT;
            case InputConstants.KEY_UP -> Key.UP;
            case InputConstants.KEY_DOWN -> Key.DOWN;
            case InputConstants.KEY_HOME -> Key.HOME;
            case InputConstants.KEY_END -> Key.END;
            case InputConstants.KEY_PAGEUP -> Key.PAGE_UP;
            case InputConstants.KEY_PAGEDOWN -> Key.PAGE_DOWN;
            default -> Key.OTHER;
        };
    }
}
