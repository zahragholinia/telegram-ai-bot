package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddressingTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode message(String value) throws Exception { return mapper.readTree(value); }
    @Test void detectsNameMentionAndReply() throws Exception {
        assertTrue(Addressing.isAddressed(message("{\"text\":\"hey Nana, can you help?\"}"), 9, "nana_bot"));
        assertTrue(Addressing.isAddressed(message("{\"text\":\"@nana_bot hello\"}"), 9, "nana_bot"));
        assertTrue(Addressing.isAddressed(message("{\"reply_to_message\":{\"from\":{\"id\":9}}}"), 9, "nana_bot"));
    }
    @Test void ignoresUnrelatedTextAndNameSubstrings() throws Exception {
        assertFalse(Addressing.isAddressed(message("{\"text\":\"banana bread\"}"), 9, "nana_bot"));
        assertFalse(Addressing.isAddressed(message("{\"text\":\"hello group\"}"), 9, "nana_bot"));
    }
}
