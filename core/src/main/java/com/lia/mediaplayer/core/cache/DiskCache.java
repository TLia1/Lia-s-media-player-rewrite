package com.lia.mediaplayer.core.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small files kept from one game session to the next in one folder of their own, capped by
 * bytes, least recently used first out: a thumbnail read touches its file, and pruning deletes
 * by oldest modification time. Pruned at start ({@link #prune}) and whenever a write takes it
 * past the cap. A cap of zero keeps nothing.
 *
 * <p>Keys are hashed into file names, so any string is a key and nothing in a key reaches the
 * file system. Every file is written to a temporary sibling and moved into place, so a crash
 * leaves either the old file or the new one; an unreadable file is simply a miss.
 *
 * <p>Blocking file IO: call it from the IO executor, never the client thread. Safe from several
 * threads at once.
 */
public final class DiskCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiskCache.class);
    private static final String TEMP_SUFFIX = ".tmp";

    private final Path dir;
    private final String suffix;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long maxBytes;
    /** Bytes of the files in the folder, as of the last scan plus the writes since. */
    private volatile long bytes;

    /**
     * @param suffix the extension of every file, e.g. {@code ".jpg"}
     */
    public DiskCache(Path dir, String suffix, long maxBytes) {
        this.dir = dir;
        this.suffix = suffix;
        this.maxBytes = Math.max(0, maxBytes);
    }

    public Path dir() {
        return dir;
    }

    /** The file of {@code key}'s value, touched as a use, or empty on a miss. */
    public Optional<byte[]> read(String key) {
        Path file = dir.resolve(fileName(key));
        try {
            byte[] data = Files.readAllBytes(file);
            try {
                Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
            } catch (IOException e) {
                // A read-only cache still serves.
            }
            return Optional.of(data);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            LOGGER.debug("Cannot read cached {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    /** Keeps {@code data} as {@code key}'s value, then prunes if the folder grew past the cap. */
    public void write(String key, byte[] data) {
        if (maxBytes <= 0 || data.length > maxBytes) {
            return;
        }
        Path file = dir.resolve(fileName(key));
        lock.lock();
        try {
            Files.createDirectories(dir);
            long replaced = Files.isRegularFile(file) ? Files.size(file) : 0;
            Path temp = Files.createTempFile(dir, "write-", TEMP_SUFFIX);
            try {
                Files.write(temp, data);
                move(temp, file);
            } finally {
                Files.deleteIfExists(temp);
            }
            bytes = Math.max(0, bytes - replaced) + data.length;
        } catch (IOException e) {
            LOGGER.debug("Cannot cache {}: {}", file, e.toString());
            return;
        } finally {
            lock.unlock();
        }
        if (bytes > maxBytes) {
            prune();
        }
    }

    /** Changes the cap. Takes effect at the next {@link #prune} or write. */
    public void setMaxBytes(long newMax) {
        maxBytes = Math.max(0, newMax);
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** What the folder holds, as of the last prune and the writes since. Any thread. */
    public long bytes() {
        return bytes;
    }

    /** Deletes the least recently used files until the folder is within the cap, and leftover temporary files. */
    public void prune() {
        lock.lock();
        try {
            List<Item> items = scan();
            long total = 0;
            for (Item item : items) {
                total += item.size();
            }
            items.sort(Comparator.comparing(Item::modified));
            for (Item item : items) {
                if (total <= maxBytes) {
                    break;
                }
                if (delete(item.file())) {
                    total -= item.size();
                }
            }
            bytes = total;
        } finally {
            lock.unlock();
        }
    }

    /** Deletes every file. */
    public void clear() {
        lock.lock();
        try {
            long left = 0;
            for (Item item : scan()) {
                if (!delete(item.file())) {
                    left += item.size();
                }
            }
            bytes = left;
        } finally {
            lock.unlock();
        }
    }

    /** The files of the folder, temporary leftovers deleted on the way. Lock held. */
    private List<Item> scan() {
        var items = new ArrayList<Item>();
        if (!Files.isDirectory(dir)) {
            return items;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.endsWith(TEMP_SUFFIX)) {
                    delete(file);
                } else if (name.endsWith(suffix)) {
                    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attributes.isRegularFile()) {
                        items.add(new Item(file, attributes.size(), attributes.lastModifiedTime()));
                    }
                }
            }
        } catch (IOException | UncheckedIOException e) {
            LOGGER.debug("Cannot list {}: {}", dir, e.toString());
        }
        return items;
    }

    private static boolean delete(Path file) {
        try {
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            LOGGER.debug("Cannot delete {}: {}", file, e.toString());
            return false;
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The file name of {@code key}: a hash, so nothing in the key reaches the file system. */
    String fileName(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16) + suffix;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM has SHA-256", e);
        }
    }

    private record Item(Path file, long size, FileTime modified) {}
}
