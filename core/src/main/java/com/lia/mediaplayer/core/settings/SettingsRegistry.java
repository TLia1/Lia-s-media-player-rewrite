package com.lia.mediaplayer.core.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lia.mediaplayer.api.settings.BoolSetting;
import com.lia.mediaplayer.api.settings.ChoiceSetting;
import com.lia.mediaplayer.api.settings.EnumSetting;
import com.lia.mediaplayer.api.settings.IntSetting;
import com.lia.mediaplayer.api.settings.SettingSpec;
import com.lia.mediaplayer.api.settings.StringListSetting;
import com.lia.mediaplayer.api.settings.StringSetting;
import com.lia.mediaplayer.core.event.EventBus;
import com.lia.mediaplayer.core.storage.Json;
import com.lia.mediaplayer.core.storage.JsonStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every setting's spec and current value, the mod's and the addons'. Services read a value
 * when they need it, or {@linkplain #onChange subscribe} to apply a change the moment it is
 * made; nothing polls.
 *
 * <p>Values are stored in {@code settings.json} by id. An id nobody registered — an addon
 * not installed today — is kept in the file as it was, so its settings come back with it.
 * Every value passes through its spec's {@code normalize} on the way in, from the file or
 * from {@link #set}.
 *
 * <p>Reads are safe from any thread. {@link #set} and {@link #register} belong to the client
 * thread, where listeners run.
 */
public final class SettingsRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsRegistry.class);

    private final JsonStore<Map<String, JsonElement>> store;
    private final Map<String, SettingSpec<?>> specs = new LinkedHashMap<>();
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final List<Listener<?>> listeners = new CopyOnWriteArrayList<>();

    public SettingsRegistry(JsonStore<Map<String, JsonElement>> store) {
        this.store = store;
    }

    /** The codec of {@code settings.json}: a map from id to the stored JSON value. */
    public static JsonStore.Codec<Map<String, JsonElement>> codec() {
        return new JsonStore.Codec<>() {
            @Override
            public Map<String, JsonElement> defaults() {
                return Map.of();
            }

            @Override
            public Map<String, JsonElement> decode(JsonObject json) {
                var map = new LinkedHashMap<String, JsonElement>();
                Json.object(json, "values").entrySet().forEach(e -> map.put(e.getKey(), e.getValue()));
                return Map.copyOf(map);
            }

            @Override
            public JsonObject encode(Map<String, JsonElement> value) {
                var sorted = new JsonObject();
                value.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> sorted.add(entry.getKey(), entry.getValue()));
                var json = new JsonObject();
                json.add("values", sorted);
                return json;
            }
        };
    }

    /**
     * Adds a setting and reads its stored value.
     *
     * @throws IllegalArgumentException if a setting of that id is already registered
     */
    public synchronized <T> SettingSpec<T> register(SettingSpec<T> spec) {
        if (specs.containsKey(spec.id())) {
            throw new IllegalArgumentException("Setting already registered: " + spec.id());
        }
        specs.put(spec.id(), spec);
        JsonElement stored = store.get().get(spec.id());
        T value = spec.defaultValue();
        if (stored != null) {
            try {
                value = spec.normalize(Objects.requireNonNull(decode(spec, stored)));
            } catch (RuntimeException e) {
                LOGGER.warn("Ignoring the stored value of {}: {}", spec.id(), e.toString());
            }
        }
        values.put(spec.id(), value);
        return spec;
    }

    /** Every registered setting, in the order registered. */
    public synchronized List<SettingSpec<?>> specs() {
        return List.copyOf(specs.values());
    }

    /** The groups of the registered settings, in the order their first setting was registered. */
    public synchronized List<String> groups() {
        var groups = new ArrayList<String>();
        for (SettingSpec<?> spec : specs.values()) {
            if (!groups.contains(spec.group())) {
                groups.add(spec.group());
            }
        }
        return groups;
    }

    public synchronized Optional<SettingSpec<?>> spec(String id) {
        return Optional.ofNullable(specs.get(id));
    }

    /**
     * The current value of {@code spec}.
     *
     * @throws IllegalArgumentException if it is not registered
     */
    public <T> T get(SettingSpec<T> spec) {
        Object value = values.get(spec.id());
        if (value == null) {
            throw new IllegalArgumentException("Setting not registered: " + spec.id());
        }
        @SuppressWarnings("unchecked") // put there by register or set, for this very spec
        T typed = (T) value;
        return typed;
    }

    /** Changes a setting, tells its listeners, and schedules the file to be written. */
    public <T> void set(SettingSpec<T> spec, T value) {
        T before = get(spec);
        T after = spec.normalize(value);
        if (after.equals(before)) {
            return;
        }
        values.put(spec.id(), after);
        JsonElement encoded = encode(spec, after);
        store.update(map -> {
            var copy = new LinkedHashMap<>(map);
            copy.put(spec.id(), encoded);
            return Map.copyOf(copy);
        });
        for (Listener<?> listener : listeners) {
            listener.offer(spec, after);
        }
    }

    public <T> void reset(SettingSpec<T> spec) {
        set(spec, spec.defaultValue());
    }

    public boolean isDefault(SettingSpec<?> spec) {
        return get(spec).equals(spec.defaultValue());
    }

    /**
     * Calls {@code listener} with each new value of {@code spec}, on the thread that set it.
     *
     * @return closing it ends the subscription
     */
    public <T> EventBus.Subscription onChange(SettingSpec<T> spec, Consumer<? super T> listener) {
        var subscription = new Listener<>(spec, listener);
        listeners.add(subscription);
        return () -> listeners.remove(subscription);
    }

    /** {@link #onChange} that also calls {@code listener} once now, with the current value. */
    public <T> EventBus.Subscription apply(SettingSpec<T> spec, Consumer<? super T> listener) {
        listener.accept(get(spec));
        return onChange(spec, listener);
    }

    /** Runs {@code action} now, and again whenever any of {@code specs} changes. */
    public void applyAll(List<? extends SettingSpec<?>> specs, Runnable action) {
        action.run();
        for (SettingSpec<?> spec : specs) {
            var unused = onChange(spec, value -> action.run());
        }
    }

    // ---- JSON ---------------------------------------------------------------

    static <T> @Nullable T decode(SettingSpec<T> spec, JsonElement json) {
        Object value = switch (spec) {
            case BoolSetting bool -> json.getAsBoolean();
            case IntSetting integer -> json.getAsInt();
            case EnumSetting<?> choice -> enumConstant(choice, json.getAsString());
            case ChoiceSetting choice -> json.getAsString();
            case StringSetting string -> json.getAsString();
            case StringListSetting list -> {
                var entries = new ArrayList<String>();
                for (JsonElement element : json.getAsJsonArray()) {
                    entries.add(element.getAsString());
                }
                yield entries;
            }
        };
        @SuppressWarnings("unchecked") // each case reads the type its spec holds
        T typed = (T) value;
        return typed;
    }

    static JsonElement encode(SettingSpec<?> spec, Object value) {
        return switch (spec) {
            case BoolSetting bool -> new JsonPrimitive((Boolean) value);
            case IntSetting integer -> new JsonPrimitive((Integer) value);
            case EnumSetting<?> choice -> new JsonPrimitive(((Enum<?>) value).name());
            case ChoiceSetting choice -> new JsonPrimitive((String) value);
            case StringSetting string -> new JsonPrimitive((String) value);
            case StringListSetting list -> {
                var array = new JsonArray();
                for (Object entry : (List<?>) value) {
                    array.add(String.valueOf(entry));
                }
                yield array;
            }
        };
    }

    private static <E extends Enum<E>> E enumConstant(EnumSetting<E> spec, String name) {
        return Enum.valueOf(spec.type(), name);
    }

    private record Listener<T>(SettingSpec<T> spec, Consumer<? super T> listener) {
        void offer(SettingSpec<?> changed, Object value) {
            if (!changed.id().equals(spec.id())) {
                return;
            }
            try {
                @SuppressWarnings("unchecked") // the same spec, so the same type
                T typed = (T) value;
                listener.accept(typed);
            } catch (RuntimeException e) {
                LOGGER.error("Listener of setting {} failed", spec.id(), e);
            }
        }
    }
}
