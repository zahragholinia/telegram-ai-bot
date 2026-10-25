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
    private final Instant now = Instant.parse("2026-10-25T10:00:00Z");
    private JsonNode message(String json) throws Exception { return mapper.readTree(json); }

    @Test void distinguishesNanaAnotherParticipantAndGroup() throws Exception {
        var participants = List.of(new GroupMemory.Participant(2, "Mina", "mina", now, now));
        assertEquals(ConversationDetector.Target.NANA, detector.detect(message("{\"from\":{\"id\":1},\"text\":\"Nana help?\"}"), 9, "nana_bot", List.of(), participants, now).target());
        assertEquals(ConversationDetector.Target.PERSON, detector.detect(message("{\"from\":{\"id\":1},\"text\":\"@mina what do you think?\"}"), 9, "nana_bot", List.of(), participants, now).target());
        var group = detector.detect(message("{\"from\":{\"id\":1},\"text\":\"Anyone know the answer?\"}"), 9, "nana_bot", List.of(new GroupMemory.Turn("user", "topic", now.minusSeconds(20))), participants, now);
        assertEquals(ConversationDetector.Target.NONE, group.target());
        assertTrue(group.shouldRespond());
    }

    @Test void avoidsInterruptingUnrelatedMessageAndRecognizesJoin() throws Exception {
        var unrelated = detector.detect(message("{\"from\":{\"id\":1},\"text\":\"Thanks!\"}"), 9, "nana_bot", List.of(), List.of(), now);
        assertFalse(unrelated.shouldRespond());
        var join = detector.detect(message("{\"from\":{\"id\":1},\"new_chat_members\":[{\"id\":3,\"first_name\":\"Ali\"}]}"), 9, "nana_bot", List.of(), List.of(), now);
        assertTrue(join.newParticipant());
    }
}
