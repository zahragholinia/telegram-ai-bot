package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/** Fast deterministic routing before the contextual Gemini decision. */
public final class ConversationDetector {
    public enum Target { NANA, PERSON, GROUP, NONE }
    public record Decision(Target target, String person, boolean continuation,
                           boolean newParticipant, boolean shouldRespond,
                           String reasonCode) {}

    private static final Pattern GROUP = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])(everyone|everybody|guys|folks|group|بچهها|بچه\\s+ها|همه)(?![\\p{L}\\p{N}_])");

    public Decision detect(JsonNode message, long botId, String botUsername,
                           List<GroupMemory.Turn> history,
                           List<GroupMemory.Participant> participants,
                           Instant now) {
        String text = message.path("text").asText(message.path("caption").asText("")).strip();
        long authorId = message.path("from").path("id").asLong(-1);
        boolean joined = false;
        JsonNode joins = message.path("new_chat_members");
        if (joins.isArray()) for (JsonNode member : joins) {
            if (member.path("id").asLong(-1) != botId) joined = true;
        }
        if (authorId == botId)
            return decision(Target.NONE, false, joined, "BOT_MESSAGE");
        if (Addressing.isAddressed(message, botId, botUsername))
            return decision(Target.NANA, true, joined, "DIRECT_ADDRESS");

        long replyToId = message.path("reply_to_message").path("from").path("id")
                .asLong(-1);
        for (GroupMemory.Participant participant : participants) {
            if (participant.id() == authorId) continue;
            boolean repliedToPerson = participant.id() == replyToId;
            String name = participant.username().isBlank()
                    ? participant.name() : participant.username();
            boolean namedPerson = !name.isBlank() && Pattern.compile(
                    "(?iu)(?<![\\p{L}\\p{N}_])@?" + Pattern.quote(name)
                            + "(?![\\p{L}\\p{N}_])").matcher(text).find();
            if (repliedToPerson || namedPerson)
                return new Decision(Target.PERSON, participant.name(),
                        isActive(history, now), joined, false, "OTHER_PERSON");
        }

        boolean active = isActive(history, now);
        boolean groupDirected = GROUP.matcher(text).find();
        boolean groupQuestion = text.matches("(?s).*[?؟].*");
        // Any message during an active exchange must reach Gemini: answers and
        // conversational statements often have no question mark or keywords.
        if (active)
            return new Decision(groupDirected ? Target.GROUP : Target.NONE, "",
                    true, joined, true, "ACTIVE_TOPIC_CONTINUATION");
        if (groupDirected || groupQuestion)
            return new Decision(groupDirected ? Target.GROUP : Target.NONE, "",
                    false, joined, true, "GROUP_MESSAGE_CANDIDATE");
        return decision(Target.NONE, false, joined, "NO_ACTIVE_CONTEXT");
    }

    private static boolean isActive(List<GroupMemory.Turn> history, Instant now) {
        return !history.isEmpty()
                && !Duration.between(history.get(history.size() - 1).at(), now)
                        .isNegative()
                && Duration.between(history.get(history.size() - 1).at(), now)
                        .compareTo(Duration.ofMinutes(12)) <= 0;
    }

    private static Decision decision(Target target, boolean evaluate, boolean joined,
                                     String reason) {
        return new Decision(target, "", false, joined, evaluate, reason);
    }
}
