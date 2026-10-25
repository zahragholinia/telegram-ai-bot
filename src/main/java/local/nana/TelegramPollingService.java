package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TelegramPollingService implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TelegramPollingService.class);
    private final TelegramClient telegram;
    private final OllamaClient ollama;
    private final GroupMemory memory;
    private final RecentUpdateIds seen = new RecentUpdateIds(20_000);
    private final GroupRateLimiter limiter;
    private final NanaProperties properties;
    private final ConversationDetector detector = new ConversationDetector();
    public TelegramPollingService(TelegramClient telegram, OllamaClient ollama, NanaProperties properties) {
        this.telegram = telegram; this.ollama = ollama; this.properties = properties;
        this.memory = new GroupMemory(properties.getContext().getMaxMessages(), Duration.ofMinutes(properties.getContext().getMaxAgeMinutes()));
        this.limiter = new GroupRateLimiter(Duration.ofSeconds(properties.getRateLimit().getGroupSeconds()));
    }
    @Override public void run(String... args) {
        long offset = 0;
        try {
            TelegramClient.BotIdentity bot = telegram.getMe();
            log.info("Nana polling started as @{} (long polling)", bot.username());
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    JsonNode updates = telegram.getUpdates(offset);
                    for (JsonNode update : updates) {
                        long id = update.path("update_id").asLong(-1);
                        if (id >= 0) offset = Math.max(offset, id + 1);
                        if (id >= 0 && seen.firstTime(id)) handle(update.path("message"), bot);
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (Exception e) {
                    log.error("Telegram polling/processing failed", e);
                    try { Thread.sleep(2000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            }
        } catch (Exception e) {
            log.error("Nana could not start Telegram polling ({}). Check token and network configuration.", e.getClass().getSimpleName());
        }
    }
    void handle(JsonNode message, TelegramClient.BotIdentity bot) {
        JsonNode chat = message.path("chat");
        String type = chat.path("type").asText("");
        if (!type.equals("group") && !type.equals("supergroup")) return;
        long chatId = chat.path("id").asLong();
        long messageId = message.path("message_id").asLong(-1);
        JsonNode sender = message.path("from");
        long senderId = sender.path("id").asLong(-1);
        // Telegram may echo bot-authored messages in some update configurations.
        // The sendMessage success path below records Nana's replies exactly once.
        if (senderId == bot.id()) return;
        String text = message.path("text").asText("");
        if (text.isBlank()) text = message.path("caption").asText("");

        String userName = sender.path("username").asText("");
        if (userName.isBlank()) {
            userName = sender.path("first_name").asText("member");
        }

        Instant now = Instant.now();
        memory.observeParticipant(chatId, senderId, userName, sender.path("username").asText(""), now);
        JsonNode newMembers = message.path("new_chat_members");
        if (newMembers.isArray()) for (JsonNode member : newMembers) {
            long id = member.path("id").asLong(-1);
            if (id >= 0) memory.observeParticipant(chatId, id,
                    member.path("username").asText(member.path("first_name").asText("member")),
                    member.path("username").asText(""), now);
        }
        if (text.isBlank()) text = newMembers.isArray() && !newMembers.isEmpty() ? "[a new participant joined]" : "";
        if (text.isBlank()) return;
        String speaker = userName + " [userId=" + senderId + "]";
        List<GroupMemory.Turn> prior = memory.get(chatId, now);
        ConversationDetector.Decision decision = detector.detect(message, bot.id(), bot.username(), prior,
                memory.participants(chatId, now), now);
        memory.add(chatId, "user", speaker + ": " + text, now);
        long nanaTurns = prior.stream().filter(turn -> "assistant".equalsIgnoreCase(turn.role())
                || "model".equalsIgnoreCase(turn.role())).count();
        String chatTag = Integer.toHexString(Long.hashCode(chatId));
        log.info("Conversation check chat={} message={} history={} nanaReplies={} decision={} reason={}",
                chatTag, messageId, prior.size(), nanaTurns,
                decision.shouldRespond() ? "EVALUATE" : "SILENT", decision.reasonCode());
        if (!decision.shouldRespond()) return;
        if (!"DIRECT_ADDRESS".equals(decision.reasonCode()) && !limiter.allow(chatId, now)) {
            log.info("Conversation check chat={} message={} decision=SILENT reason=RATE_LIMITED",
                    chatTag, messageId);
            return;
        }
        try {
            String reply = ollama.respond(
                    "Latest message from " + speaker + ": " + text
                            + "\n[conversation assessment: target=" + decision.target()
                            + ", continuation=" + decision.continuation()
                            + ", reason=" + decision.reasonCode() + "]",
                    prior,
                    properties.getResponseMaxChars()
            );
            String silentReason = OllamaClient.silentReason(reply);
            if (reply.isBlank() || !silentReason.isEmpty()) {
                log.info("Conversation check chat={} message={} decision=SILENT reason={}",
                        chatTag, messageId,
                        silentReason.isEmpty() ? "NO_RESPONSE_NEEDED" : silentReason);
                return;
            }
            telegram.sendMessage(chatId, reply,
                    message.path("message_id").isNumber()
                            ? message.path("message_id").asInt() : null);
            memory.add(chatId, "assistant", reply, Instant.now());
            log.info("Conversation check chat={} message={} decision=RESPOND reason={}",
                    chatTag, messageId, decision.reasonCode());
        } catch (Exception e) {
            log.error("Reply failed in group {}: {}", chatId,
                    e.getMessage(), e);
        }
    }
}
