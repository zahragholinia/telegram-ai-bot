package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** Response routing is intentionally independent of conversation context. */
public final class ConversationDetector {
    public enum Target { NANA, PERSON, GROUP, NONE }
    public record Decision(Target target, String person, boolean continuation,
                           boolean newParticipant, boolean shouldRespond,
                           String reasonCode) {}

    public Decision detect(JsonNode message, long botId, String botUsername,
                           List<GroupMemory.Turn> history,
                           List<GroupMemory.Participant> participants,
                           Instant now) {
        boolean addressed = Addressing.isAddressed(message, botId, botUsername);
        boolean botMessage = message.path("from").path("is_bot").asBoolean(false)
                && message.path("from").path("id").asLong(-1) != botId;
        if (message.path("from").path("id").asLong(-1) == botId || botMessage) {
            return new Decision(Target.NONE, "", false, false, false, "BOT_MESSAGE");
        }
        return new Decision(addressed ? Target.NANA : Target.NONE, "", false,
                false, addressed, addressed ? "DIRECT_ADDRESS" : "NOT_ADDRESSED");
    }
}
