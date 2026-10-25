package local.nana;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionTest {
    @Test void deduplicatesAndRateLimits() {
        RecentUpdateIds ids = new RecentUpdateIds(2);
        assertTrue(ids.firstTime(1));
        assertFalse(ids.firstTime(1));
        assertTrue(ids.firstTime(2));
        assertTrue(ids.firstTime(3));
        assertTrue(ids.firstTime(1)); // oldest ID was evicted from bounded cache
        GroupRateLimiter limiter = new GroupRateLimiter(Duration.ofSeconds(3));
        Instant now = Instant.now();
        assertTrue(limiter.allow(1, now));
        assertFalse(limiter.allow(1, now.plusSeconds(1)));
        assertTrue(limiter.allow(2, now.plusSeconds(1)));
        assertTrue(limiter.allow(1, now.plusSeconds(3)));
    }
}
