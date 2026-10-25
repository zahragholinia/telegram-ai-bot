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
    public record Participant(long id, String name, String username, Instant firstSeen, Instant lastSeen) {}
    private final int capacity;
    private final Duration maxAge;
    private final Map<Long, Deque<Turn>> groups = new HashMap<>();
    private final Map<Long, Map<Long, Participant>> participants = new HashMap<>();

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
    public synchronized void observeParticipant(long chatId, long id, String name, String username, Instant now) {
        Map<Long, Participant> people = participants.computeIfAbsent(chatId, ignored -> new HashMap<>());
        Participant old = people.get(id);
        people.put(id, new Participant(id, name, username, old == null ? now : old.firstSeen(), now));
    }
    public synchronized List<Participant> participants(long chatId) {
        return List.copyOf(participants.getOrDefault(chatId, Map.of()).values());
    }
    public synchronized List<Participant> participants(long chatId, Instant now) {
        Map<Long, Participant> people = participants.get(chatId);
        if (people == null) return List.of();
        people.values().removeIf(person ->
                Duration.between(person.lastSeen(), now).compareTo(maxAge) > 0);
        if (people.isEmpty()) {
            participants.remove(chatId);
            return List.of();
        }
        return List.copyOf(people.values());
    }
    private void prune(Deque<Turn> turns, Instant now) {
        while (!turns.isEmpty() && Duration.between(turns.peekFirst().at(), now).compareTo(maxAge) > 0) turns.removeFirst();
    }
}
