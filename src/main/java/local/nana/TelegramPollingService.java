package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
    private void handle(JsonNode message, TelegramClient.BotIdentity bot) {
        JsonNode chat = message.path("chat");
        String type = chat.path("type").asText("");
        if (!type.equals("group") && !type.equals("supergroup")) return;
        long chatId = chat.path("id").asLong();
        String text = message.path("text").asText("");
        if (text.isBlank()) text = message.path("caption").asText("");
        if (text.isBlank()) return;
        JsonNode sender = message.path("from");

        String userName = sender.path("username").asText("");
        if (userName.isBlank()) {
            userName = sender.path("first_name").asText("member");
        }

        long senderId = sender.path("id").asLong();
        String speaker = userName + " [userId=" + senderId + "]";
        Instant now = Instant.now();
        boolean addressed = Addressing.isAddressed(message, bot.id(), bot.username());
        List<GroupMemory.Turn> prior = memory.get(chatId, now);
        memory.add(chatId, "user", speaker + ": " + text, now);
        if (!addressed) return;
        if (!limiter.allow(chatId, now)) { log.info("Addressed message rate-limited in group {}", chatId); return; }
        try {
            String reply = ollama.respond(
                    speaker + ": " + text,
                    prior,
                    properties.getResponseMaxChars()
            );
            if (reply.isBlank()) return;
            int sentMessageId = telegram.sendMessage(chatId, reply, message.path("message_id").isNumber() ? message.path("message_id").asInt() : null);
            memory.add(chatId, "assistant", reply, Instant.now());
        } catch (Exception e) {
            log.error("Reply failed in group {}: {}", chatId,
                    e.getMessage(), e);
        }
    }
}
