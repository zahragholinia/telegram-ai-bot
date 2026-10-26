package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddressingTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode message(String value) throws Exception { return mapper.readTree(value); }

    @Test void acceptsCallsByNameAndPersianVariants() throws Exception {
        assertTrue(addressed("Hey Nana, what do you think?"));
        assertTrue(addressed("Nana what do you think?"));
        assertTrue(addressed("نانا، نظرت چیه؟"));
        assertTrue(addressed("نانا نظرت چیه؟"));
        assertTrue(addressed("ناناا، میشه کمکم کنی؟"));
        assertTrue(addressed("Who are you, Nana?"));
    }

    @Test void requiresActualBotMentionOrReplyToBot() throws Exception {
        assertTrue(Addressing.isAddressed(message("{\"text\":\"@ZaraNanaBot what do you think?\"}"), 9, "ZaraNanaBot"));
        assertFalse(Addressing.isAddressed(message("{\"text\":\"@someone_else help\"}"), 9, "ZaraNanaBot"));
        assertTrue(Addressing.isAddressed(message("{\"reply_to_message\":{\"from\":{\"id\":9,\"is_bot\":true}}}"), 9, "ZaraNanaBot"));
        assertFalse(Addressing.isAddressed(message("{\"reply_to_message\":{\"from\":{\"id\":10,\"is_bot\":false}}}"), 9, "ZaraNanaBot"));
        assertTrue(Addressing.isAddressed(message("{\"text\":\"hello there\",\"entities\":[{\"type\":\"text_mention\",\"user\":{\"id\":9}}]}"), 9, "ZaraNanaBot"));
    }

    @Test void doesNotTreatAReferenceToNanaAsCallingHer() throws Exception {
        assertFalse(addressed("Nana was right earlier"));
        assertFalse(addressed("Hi everyone!"));
        assertFalse(addressed("What do you think?"));
        assertFalse(addressed("banana bread"));
    }

    private boolean addressed(String text) throws Exception {
        return Addressing.isAddressed(message(mapper.createObjectNode().put("text", text).toString()), 9, "ZaraNanaBot");
    }
}
