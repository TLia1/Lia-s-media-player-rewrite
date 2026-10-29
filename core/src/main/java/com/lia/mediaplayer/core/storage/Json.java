package com.lia.mediaplayer.core.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Function;

/**
 * Lenient reads from a JSON object, for files people edit by hand: a field that is missing or
 * of the wrong shape gives the fallback instead of an exception.
 */
public final class Json {

    private Json() {}

    public static boolean bool(JsonObject json, String key, boolean fallback) {
        return read(json, key, JsonElement::getAsBoolean, fallback);
    }

    public static int integer(JsonObject json, String key, int fallback) {
        return read(json, key, JsonElement::getAsInt, fallback);
    }

    public static long longValue(JsonObject json, String key, long fallback) {
        return read(json, key, JsonElement::getAsLong, fallback);
    }

    public static float floatValue(JsonObject json, String key, float fallback) {
        return read(json, key, JsonElement::getAsFloat, fallback);
    }

    public static String string(JsonObject json, String key, String fallback) {
        return read(json, key, JsonElement::getAsString, fallback);
    }

    /** The array at {@code key}, or an empty one. */
    public static JsonArray array(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    /** The object at {@code key}, or an empty one. */
    public static JsonObject object(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    /** {@code name} as a constant of {@code type}, or the fallback. */
    public static <E extends Enum<E>> E enumValue(JsonObject json, String key, E fallback) {
        String name = string(json, key, "");
        for (E constant : fallback.getDeclaringClass().getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        return fallback;
    }

    private static <V> V read(JsonObject json, String key, Function<JsonElement, V> getter, V fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return getter.apply(element);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
