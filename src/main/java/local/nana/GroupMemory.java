package local.nana;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded, process-local conversation snippets, isolated by Telegram chat ID. */
public class GroupMemory {
    public record Turn(String role, String text, Instant at) {}
    private final int capacity;
    private final Duration maxAge;
    private final Map<Long, Deque<Turn>> groups = new HashMap<>();

    public GroupMemory(int capacity, Duration maxAge) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.maxAge = maxAge;
    }
    public synchronized void add(long chatId, String role, String text, Instant now) {
        Deque<Turn> turns = groups.computeIfAbsent(chatId, ignored -> new ArrayDeque<>());
        prune(turns, now);
        turns.addLast(new Turn(role, text, now));
        while (turns.size() > capacity) turns.removeFirst();
    }
    public synchronized List<Turn> get(long chatId, Instant now) {
        Deque<Turn> turns = groups.get(chatId);
        if (turns == null) return List.of();
        prune(turns, now);
        if (turns.isEmpty()) groups.remove(chatId);
        return List.copyOf(turns);
    }
    private void prune(Deque<Turn> turns, Instant now) {
        while (!turns.isEmpty() && Duration.between(turns.peekFirst().at(), now).compareTo(maxAge) > 0) turns.removeFirst();
    }
}
