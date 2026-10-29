package com.lia.mediaplayer.platform.mc;

import com.lia.mediaplayer.ui.input.InputEvent;
import com.lia.mediaplayer.ui.input.Key;
import com.lia.mediaplayer.ui.input.Mods;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** The game's key codes and modifier bits, as the UI's {@link Key} and {@link Mods}. */
public final class McInput {

    private McInput() {}

    /** The modifiers in a GLFW modifier bit set, as input events carry it. */
    public static Mods mods(int glfwMods) {
        return new Mods(
                (glfwMods & GLFW.GLFW_MOD_SHIFT) != 0,
                (glfwMods & GLFW.GLFW_MOD_CONTROL) != 0,
                (glfwMods & GLFW.GLFW_MOD_ALT) != 0,
                (glfwMods & GLFW.GLFW_MOD_SUPER) != 0,
                Minecraft.ON_OSX);
    }

    /** The modifiers held right now, for an event that does not carry them (a press, a drag). */
    public static Mods currentMods() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return new Mods(
                down(window, GLFW.GLFW_KEY_LEFT_SHIFT) || down(window, GLFW.GLFW_KEY_RIGHT_SHIFT),
                down(window, GLFW.GLFW_KEY_LEFT_CONTROL) || down(window, GLFW.GLFW_KEY_RIGHT_CONTROL),
                down(window, GLFW.GLFW_KEY_LEFT_ALT) || down(window, GLFW.GLFW_KEY_RIGHT_ALT),
                down(window, GLFW.GLFW_KEY_LEFT_SUPER) || down(window, GLFW.GLFW_KEY_RIGHT_SUPER),
                Minecraft.ON_OSX);
    }

    private static boolean down(long window, int key) {
        return InputConstants.isKeyDown(window, key);
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

    public static InputEvent.KeyDown key(int glfwKey, int glfwMods) {
        return new InputEvent.KeyDown(key(glfwKey), mods(glfwMods));
    }

    static Key key(int glfwKey) {
        if (glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z) {
            return Key.letter((char) ('A' + (glfwKey - GLFW.GLFW_KEY_A)));
        }
        if (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9) {
            return Key.digit(glfwKey - GLFW.GLFW_KEY_0);
        }
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_SPACE -> Key.SPACE;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> Key.ENTER;
            case GLFW.GLFW_KEY_ESCAPE -> Key.ESCAPE;
            case GLFW.GLFW_KEY_TAB -> Key.TAB;
            case GLFW.GLFW_KEY_BACKSPACE -> Key.BACKSPACE;
            case GLFW.GLFW_KEY_DELETE -> Key.DELETE;
            case GLFW.GLFW_KEY_LEFT -> Key.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> Key.RIGHT;
            case GLFW.GLFW_KEY_UP -> Key.UP;
            case GLFW.GLFW_KEY_DOWN -> Key.DOWN;
            case GLFW.GLFW_KEY_HOME -> Key.HOME;
            case GLFW.GLFW_KEY_END -> Key.END;
            case GLFW.GLFW_KEY_PAGE_UP -> Key.PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> Key.PAGE_DOWN;
            default -> Key.OTHER;
        };
    }
}
