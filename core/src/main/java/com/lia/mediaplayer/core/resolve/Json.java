package com.lia.mediaplayer.core.resolve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Optional;

/**
 * Lenient reads from the JSON yt-dlp and ffprobe print. Their fields come and go between
 * extractors and versions, so a missing or mistyped field reads as absent rather than failing.
 */
public final class Json {

    private Json() {}

    /** Parses a top-level object. */
    public static JsonObject object(String text) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                throw new IOException("Expected a JSON object");
            }
            return root.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Malformed JSON: " + e.getMessage(), e);
        }
    }

    public static Optional<String> string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = element.getAsString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** A number, or a string holding one (ffprobe quotes some numbers). */
    public static Optional<Double> number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            double value = element.getAsJsonPrimitive().isNumber()
                    ? element.getAsDouble()
                    : Double.parseDouble(element.getAsString().strip());
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static int integer(JsonObject object, String key) {
        return number(object, key)
                .map(d -> (int) Math.max(0, Math.min(Integer.MAX_VALUE, d)))
                .orElse(0);
    }

    public static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean();
    }

    public static Optional<JsonObject> child(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
    }

    public static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }
}
