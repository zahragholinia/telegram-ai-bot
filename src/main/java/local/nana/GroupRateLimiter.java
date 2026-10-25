package local.nana;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class GroupRateLimiter {
    private final Duration interval;
    private final Map<Long, Instant> lastAllowed = new HashMap<>();
    public GroupRateLimiter(Duration interval) { this.interval = interval; }
    public synchronized boolean allow(long groupId, Instant now) {
        Instant previous = lastAllowed.get(groupId);
        if (previous != null && Duration.between(previous, now).compareTo(interval) < 0) return false;
        lastAllowed.put(groupId, now);
        return true;
    }
}
