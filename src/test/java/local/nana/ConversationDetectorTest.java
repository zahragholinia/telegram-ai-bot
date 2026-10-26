package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConversationDetectorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConversationDetector detector = new ConversationDetector();
    private JsonNode message(String json) throws Exception { return mapper.readTree(json); }

    @Test void conversationHistoryNeverMakesAnUnaddressedMessageEligible() throws Exception {
        var now = Instant.now();
        var active = List.of(new GroupMemory.Turn("assistant", "Nana said something", now));
        var decision = detector.detect(message("{\"from\":{\"id\":1},\"text\":\"What do you think?\"}"),
                9, "ZaraNanaBot", active, List.of(), now);
        assertFalse(decision.shouldRespond());
        assertEquals("NOT_ADDRESSED", decision.reasonCode());
    }

    @Test void explicitAddressPassesEvenWhenMessageTargetsOtherParticipantsToo() throws Exception {
        var decision = detector.detect(message("{\"from\":{\"id\":1},\"text\":\"Nana, what does Ali think?\"}"),
                9, "ZaraNanaBot", List.of(), List.of(), Instant.now());
        assertTrue(decision.shouldRespond());
        assertEquals("DIRECT_ADDRESS", decision.reasonCode());
    }
}
