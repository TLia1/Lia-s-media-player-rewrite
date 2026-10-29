package com.lia.mediaplayer.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One JSON file of the mod, held in memory as an immutable value of type {@code T}.
 *
 * <ul>
 *   <li>The file is read once, on the first {@link #get}. A file that cannot be read keeps the
 *       defaults and is renamed {@code <name>.broken-<time>.json} rather than overwritten, so
 *       whatever the user had can still be recovered by hand.
 *   <li>Every file carries a top-level {@code "schema": n}. A file of an older schema is
 *       brought up to date by the {@link Migration}s, one step at a time, before it is decoded.
 *   <li>{@link #update} changes the value at once and writes it later, on the IO executor,
 *       once changes have stopped for the debounce delay: dragging a window writes once, not
 *       sixty times a second. {@link #flush} writes now, and is what shutdown calls.
 *   <li>Writes go to a sibling temp file, then an atomic move over the real one, so a crash
 *       mid-write leaves the previous version whole.
 * </ul>
 *
 * <p>Safe from any thread: the value is published whole, and updates are serialised.
 *
 * @param <T> the file's contents; a record or another immutable value, since {@link #get}
 *     hands out the live one
 */
public final class JsonStore<T> implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonStore.class);
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter BROKEN_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** How a value is read from and written to JSON. */
    public interface Codec<T> {

        /** The value of a file that does not exist yet. */
        T defaults();

        /**
         * Reads a file of the current schema. Skips entries it cannot read rather than
         * failing the lot; throws only when nothing can be made of the file.
         */
        T decode(JsonObject json);

        /** Writes the value; {@code "schema"} is added by the store. */
        JsonObject encode(T value);
    }

    /**
     * Brings a file of schema {@code from} to schema {@code from + 1}.
     *
     * @param step may change the object it is given and return it
     */
    public record Migration(int from, UnaryOperator<JsonObject> step) {}

    private final Path file;
    private final Codec<T> codec;
    private final int schema;
    private final List<Migration> migrations;
    private final Executor io;
    private final ScheduledExecutorService timer;
    private final Duration debounce;
    private final Clock clock;
    private final Object writeLock = new Object();
    private volatile @Nullable T value;
    private @Nullable ScheduledFuture<?> scheduled;
    private boolean dirty;

    public JsonStore(
            Path file,
            Codec<T> codec,
            int schema,
            List<Migration> migrations,
            Executor io,
            ScheduledExecutorService timer,
            Duration debounce) {
        this(file, codec, schema, migrations, io, timer, debounce, Clock.system(ZoneId.systemDefault()));
    }

    JsonStore(
            Path file,
            Codec<T> codec,
            int schema,
            List<Migration> migrations,
            Executor io,
            ScheduledExecutorService timer,
            Duration debounce,
            Clock clock) {
        if (schema < 1) {
            throw new IllegalArgumentException("Schemas start at 1");
        }
        this.file = file;
        this.codec = codec;
        this.schema = schema;
        this.migrations = List.copyOf(migrations);
        this.io = io;
        this.timer = timer;
        this.debounce = debounce;
        this.clock = clock;
    }

    public Path file() {
        return file;
    }

    /** The current value, reading the file the first time. */
    public T get() {
        T current = value;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (value == null) {
                value = load();
            }
            return Objects.requireNonNull(value);
        }
    }

    /** Replaces the value with {@code change} of it, and schedules a write if it changed. */
    public void update(UnaryOperator<T> change) {
        synchronized (this) {
            T before = get();
            T after = change.apply(before);
            if (after.equals(before)) {
                return;
            }
            value = after;
        }
        scheduleWrite();
    }

    /** Replaces the value, and schedules a write if it changed. */
    public void set(T newValue) {
        update(unused -> newValue);
    }

    /** Writes now if anything changed since the last write. Blocks for the file IO. */
    public void flush() {
        synchronized (writeLock) {
            ScheduledFuture<?> pending = scheduled;
            scheduled = null;
            if (pending != null) {
                pending.cancel(false);
            }
            if (!dirty) {
                return;
            }
            dirty = false;
            write(get());
        }
    }

    @Override
    public void close() {
        flush();
    }

    private void scheduleWrite() {
        synchronized (writeLock) {
            dirty = true;
            ScheduledFuture<?> pending = scheduled;
            if (pending != null) {
                pending.cancel(false);
            }
            scheduled = timer.schedule(() -> io.execute(this::flush), debounce.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    // ---- Reading ------------------------------------------------------------

    private T load() {
        if (!Files.isRegularFile(file)) {
            return codec.defaults();
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("not a JSON object");
            }
            return codec.decode(migrate(parsed.getAsJsonObject()));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {} ({}); starting from defaults", file, e.toString());
            setAside();
            return codec.defaults();
        }
    }

    private JsonObject migrate(JsonObject json) {
        int found = json.has("schema") ? json.get("schema").getAsInt() : 1;
        if (found > schema) {
            LOGGER.warn("{} was written by a newer version (schema {}); reading what this one knows", file, found);
            return json;
        }
        JsonObject current = json;
        for (int at = found; at < schema; at++) {
            final int from = at;
            Migration migration = migrations.stream()
                    .filter(m -> m.from() == from)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no migration from schema " + from));
            current = migration.step().apply(current);
        }
        return current;
    }

    /** Moves an unreadable file out of the way, so the next write does not destroy it. */
    private void setAside() {
        String name = file.getFileName().toString();
        String stem = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        Path aside =
                file.resolveSibling(stem + ".broken-" + LocalDateTime.now(clock).format(BROKEN_SUFFIX) + ".json");
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Kept the unreadable file as {}", aside);
        } catch (IOException e) {
            LOGGER.warn("Could not set {} aside: {}", file, e.toString());
        }
    }

    // ---- Writing ------------------------------------------------------------

    private void write(T snapshot) {
        try {
            writeFile(file, schema, codec.encode(snapshot));
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }

    /**
     * Writes {@code body} as a file of schema {@code schema}, the way a store does: to a temp
     * file, then moved over the real one. For whoever makes a store's file without a store.
     */
    public static void writeFile(Path target, int schema, JsonObject body) throws IOException {
        var json = new JsonObject();
        json.addProperty("schema", schema);
        body.entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
        writeAtomically(target, GSON.toJson(json) + "\n");
    }

    /** Writes {@code content} to a sibling temp file and moves it over {@code target}. */
    static void writeAtomically(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
