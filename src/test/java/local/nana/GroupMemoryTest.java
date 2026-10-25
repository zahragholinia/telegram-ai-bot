package local.nana;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroupMemoryTest {
    @Test void keepsGroupsSeparateAndTrimsOldTurns() {
        GroupMemory memory = new GroupMemory(2, Duration.ofMinutes(30));
        Instant now = Instant.now();
        memory.add(1, "user", "one", now);
        memory.add(1, "user", "two", now);
        memory.add(1, "user", "three", now);
        memory.add(2, "user", "private to group two", now);
        assertEquals(2, memory.get(1, now).size());
        assertEquals("two", memory.get(1, now).getFirst().text());
        assertEquals("private to group two", memory.get(2, now).getFirst().text());
        assertTrue(memory.get(1, now.plus(Duration.ofHours(1))).isEmpty());
    }
}
