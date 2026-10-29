package com.lia.mediaplayer.core.playback;

import com.lia.mediaplayer.api.media.RepeatMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * What plays after the current track: the entries waiting, the ones already played, and the
 * two modes that use both. Pure — no IO, no threads — and confined to its owner's thread.
 *
 * <p>The current track is not stored here; {@link #next} and {@link #previous} take it, as in
 * v2, so the queue holds exactly what the queue panel lists.
 *
 * <ul>
 *   <li>{@link RepeatMode#ONE} makes {@code next} return the current track again.
 *   <li>{@link RepeatMode#ALL} refills the queue from the history once it runs dry, so the
 *       whole round loops.
 *   <li>Shuffle is a sticky flag rather than a one-off reorder: turning it on shuffles what is
 *       queued, and each {@code ALL} round is reshuffled — never starting with the track just
 *       heard, which would sound like a stuck player.
 * </ul>
 *
 * @param <T> what an entry is: a {@code MediaRef} in the engine, strings in tests
 */
public final class PlayQueue<T> {

    /** Played tracks remembered: bounds both "previous" and one loop round. */
    static final int MAX_HISTORY = 256;

    private final RandomGenerator random;
    private final List<T> upcoming = new ArrayList<>();
    private final List<T> history = new ArrayList<>();
    private RepeatMode repeat = RepeatMode.OFF;
    private boolean shuffle;
    private int version;

    public PlayQueue(RandomGenerator random) {
        this.random = random;
    }

    /** Bumped by every change to the entries or their order; a view redraws when it moves. */
    public int version() {
        return version;
    }

    public boolean isEmpty() {
        return upcoming.isEmpty();
    }

    public int size() {
        return upcoming.size();
    }

    public T get(int index) {
        return upcoming.get(index);
    }

    /** The entries waiting, in play order. */
    public List<T> upcoming() {
        return List.copyOf(upcoming);
    }

    public void add(T entry) {
        upcoming.add(entry);
        version++;
    }

    public void addAll(Collection<? extends T> entries) {
        upcoming.addAll(entries);
        version++;
    }

    /** Inserts at {@code index}, clamped into the list. */
    public void insert(int index, T entry) {
        upcoming.add(Math.max(0, Math.min(upcoming.size(), index)), entry);
        version++;
    }

    /** Moves the entry at {@code from} to {@code to}, both read against the list as it is now. */
    public void move(int from, int to) {
        if (from < 0 || from >= upcoming.size() || to < 0 || to >= upcoming.size() || from == to) {
            return;
        }
        upcoming.add(to, upcoming.remove(from));
        version++;
    }

    public T remove(int index) {
        version++;
        return upcoming.remove(index);
    }

    /** Takes the entry at {@code index} out to play it now; the others keep their order. */
    public Optional<T> take(int index) {
        if (index < 0 || index >= upcoming.size()) {
            return Optional.empty();
        }
        return Optional.of(remove(index));
    }

    public void clear() {
        upcoming.clear();
        history.clear();
        version++;
    }

    public RepeatMode repeat() {
        return repeat;
    }

    public void setRepeat(RepeatMode mode) {
        repeat = mode;
    }

    /** The loop button: steps to the next mode and returns it. */
    public RepeatMode cycleRepeat() {
        repeat = repeat.next();
        return repeat;
    }

    public boolean shuffle() {
        return shuffle;
    }

    /** Turning shuffle on also shuffles what is already queued, so it takes effect at once. */
    public void setShuffle(boolean on) {
        shuffle = on;
        if (on) {
            Collections.shuffle(upcoming, random);
            version++;
        }
    }

    public boolean toggleShuffle() {
        setShuffle(!shuffle);
        return shuffle;
    }

    /** Whether {@link #next} would return something: whether to advance rather than stop. */
    public boolean hasNext() {
        return !upcoming.isEmpty() || repeat != RepeatMode.OFF;
    }

    public boolean hasPrevious() {
        return !history.isEmpty();
    }

    /**
     * What plays after {@code current}, which joins the history; empty when playback is over.
     * {@link RepeatMode#ONE} returns {@code current} itself.
     */
    public Optional<T> next(T current) {
        if (repeat == RepeatMode.ONE) {
            return Optional.of(current);
        }
        if (upcoming.isEmpty()) {
            if (repeat != RepeatMode.ALL) {
                return Optional.empty();
            }
            // The finished track closes the round before the round is recycled, so it takes
            // its place in the next one exactly once.
            pushHistory(current);
            if (!startNextRound(current)) {
                return Optional.empty();
            }
            return Optional.of(remove(0));
        }
        pushHistory(current);
        return Optional.of(remove(0));
    }

    /**
     * The track played before {@code current}, which goes back to the front of the queue so
     * "next" returns to it; empty when nothing has played yet.
     */
    public Optional<T> previous(T current) {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        insert(0, current);
        return Optional.of(history.remove(history.size() - 1));
    }

    private boolean startNextRound(T justPlayed) {
        var round = new ArrayList<>(history);
        history.clear();
        if (round.isEmpty()) {
            return false;
        }
        if (shuffle) {
            Collections.shuffle(round, random);
            if (round.size() > 1 && round.getFirst().equals(justPlayed)) {
                Collections.swap(round, 0, 1 + random.nextInt(round.size() - 1));
            }
        }
        upcoming.addAll(round);
        version++;
        return true;
    }

    private void pushHistory(T entry) {
        history.add(entry);
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }
}
