package local.nana;

import java.util.LinkedHashSet;
import java.util.Set;

public class RecentUpdateIds {
    private final int capacity;
    private final Set<Long> ids = new LinkedHashSet<>();
    public RecentUpdateIds(int capacity) { this.capacity = capacity; }
    public synchronized boolean firstTime(long id) {
        if (!ids.add(id)) return false;
        while (ids.size() > capacity) ids.remove(ids.iterator().next());
        return true;
    }
}
