
package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class TelegramPollingService implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(TelegramPollingService.class);

    private final TelegramClient telegram;
    private final OllamaClient ollama;
    private final GroupMemory memory;
    private final RecentUpdateIds seen = new RecentUpdateIds(20_000);
    private final GroupRateLimiter limiter;
    private final NanaProperties properties;
    private final ConversationDetector detector =
            new ConversationDetector();

    private final TelegramAccountRepository accountRepository;
    private final PersonIdentityService personIdentityService;
    private final MessageSourceRepository messageSourceRepository;
    private final MemoryExtractionService memoryExtractionService;
    private final FactMemoryService factMemoryService;
    private final FactRetrievalRepository factRetrievalRepository;

    // New: handles Nana's identity questions without calling the AI model.
    private final NanaIdentityResponder nanaIdentityResponder;

    public TelegramPollingService(
            TelegramClient telegram,
            OllamaClient ollama,
            NanaProperties properties,
            TelegramAccountRepository accountRepository,
            PersonIdentityService personIdentityService,
            MessageSourceRepository messageSourceRepository,
            MemoryExtractionService memoryExtractionService,
            FactMemoryService factMemoryService,
            FactRetrievalRepository factRetrievalRepository,
            NanaIdentityResponder nanaIdentityResponder) {

        this.telegram = telegram;
        this.ollama = ollama;
        this.properties = properties;
        this.accountRepository = accountRepository;
        this.personIdentityService = personIdentityService;
        this.messageSourceRepository = messageSourceRepository;
        this.memoryExtractionService = memoryExtractionService;
        this.factMemoryService = factMemoryService;
        this.factRetrievalRepository = factRetrievalRepository;
        this.nanaIdentityResponder = nanaIdentityResponder;

        this.memory = new GroupMemory(
                properties.getContext().getMaxMessages(),
                Duration.ofMinutes(
                        properties.getContext().getMaxAgeMinutes()));

        this.limiter = new GroupRateLimiter(
                Duration.ofSeconds(
                        properties.getRateLimit().getGroupSeconds()));
    }

    @Override
    public void run(String... args) {
        long offset = 0;

        try {
            TelegramClient.BotIdentity bot = telegram.getMe();

            log.info(
                    "Nana polling started as @{} (long polling)",
                    bot.username());

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    JsonNode updates = telegram.getUpdates(offset);

                    for (JsonNode update : updates) {
                        long id = update.path("update_id").asLong(-1);

                        if (id >= 0) {
                            offset = Math.max(offset, id + 1);
                        }

                        processUpdate(update, bot);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Telegram polling/processing failed", e);

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (Exception e) {
            log.error(
                    "Nana could not start Telegram polling ({}). "
                            + "Check token and network configuration.",
                    e.getClass().getSimpleName());
        }
    }

    void processUpdate(JsonNode update, TelegramClient.BotIdentity bot) {
        long id = update.path("update_id").asLong(-1);
        if (id >= 0 && seen.firstTime(id)) handle(update.path("message"), bot);
    }

    void handle(JsonNode message, TelegramClient.BotIdentity bot) {
        JsonNode chat = message.path("chat");
        String type = chat.path("type").asText("");

        if (!type.equals("group") && !type.equals("supergroup")) {
            return;
        }

        long chatId = chat.path("id").asLong();
        long messageId = message.path("message_id").asLong(-1);

        JsonNode sender = message.path("from");
        long senderId = sender.path("id").asLong(-1);

        // Ignore Nana's own messages and messages from every other bot.
        if (senderId == bot.id() || sender.path("is_bot").asBoolean(false)) {
            return;
        }

        String text = message.path("text").asText("");

        if (text.isBlank()) {
            text = message.path("caption").asText("");
        }

        String userName = sender.path("username").asText("");

        if (userName.isBlank()) {
            userName = sender.path("first_name").asText("member");
        }

        long accountId = -1;
        long personId = -1;
        long sourceId = -1;

        if (senderId >= 0) {
            String telegramUsername = sender.path("username").asText("");
            String displayName = sender.path("first_name").asText("member");

            accountId = accountRepository.upsertAccount(
                    senderId,
                    telegramUsername,
                    displayName);

            personId = personIdentityService.getOrCreatePersonForAccount(
                    accountId,
                    displayName);

            if (messageId >= 0) {
                sourceId = messageSourceRepository.upsertMessageSource(
                        chatId,
                        messageId,
                        accountId,
                        text);
            }

            log.debug(
                    "Identity resolved for Telegram account {} as person {}",
                    accountId,
                    personId);
        }

        Instant now = Instant.now();

        memory.observeParticipant(
                chatId,
                senderId,
                userName,
                sender.path("username").asText(""),
                now);

        JsonNode newMembers = message.path("new_chat_members");

        if (newMembers.isArray()) {
            for (JsonNode member : newMembers) {
                long id = member.path("id").asLong(-1);

                if (id >= 0) {
                    memory.observeParticipant(
                            chatId,
                            id,
                            member.path("username").asText(
                                    member.path("first_name").asText("member")),
                            member.path("username").asText(""),
                            now);
                }
            }
        }

        if (text.isBlank()) {
            text = newMembers.isArray() && !newMembers.isEmpty()
                    ? "[a new participant joined]"
                    : "";
        }

        if (text.isBlank()) {
            return;
        }

        // Extract long-term memory independently of response handling.
        if (personId > 0
                && sourceId > 0
                && shouldConsiderForMemory(text)) {
            try {
                extractAndSaveMemory(personId, sourceId, userName, text);
            } catch (Exception e) {
                log.warn(
                        "Memory extraction failed for chat={} message={}: {}",
                        Integer.toHexString(Long.hashCode(chatId)),
                        messageId,
                        e.getMessage());
            }
        }

        String speaker = userName + " [userId=" + senderId + "]";

        List<GroupMemory.Turn> prior = memory.get(chatId, now);

        ConversationDetector.Decision decision = detector.detect(
                message,
                bot.id(),
                bot.username(),
                prior,
                memory.participants(chatId, now),
                now);

        memory.add(
                chatId,
                "user",
                speaker + ": " + text,
                now);

        long nanaTurns = prior.stream()
                .filter(turn ->
                        "assistant".equalsIgnoreCase(turn.role())
                                || "model".equalsIgnoreCase(turn.role()))
                .count();

        String chatTag = Integer.toHexString(Long.hashCode(chatId));

        log.info(
                "Conversation check chat={} message={} history={} "
                        + "nanaReplies={} decision={} reason={}",
                chatTag,
                messageId,
                prior.size(),
                nanaTurns,
                decision.shouldRespond() ? "EVALUATE" : "SILENT",
                decision.reasonCode());

        if (!decision.shouldRespond()) {
            return;
        }

        if (!"DIRECT_ADDRESS".equals(decision.reasonCode())
                && !limiter.allow(chatId, now)) {
            log.info(
                    "Conversation check chat={} message={} "
                            + "decision=SILENT reason=RATE_LIMITED",
                    chatTag,
                    messageId);
            return;
        }

        try {
            // New: answer identity questions directly.
            Optional<String> identityReply =
                    nanaIdentityResponder.respondIfIdentityQuestion(
                            message, bot.id(), bot.username());

            if (identityReply.isPresent()) {
                String reply = identityReply.get();

                telegram.sendMessage(
                        chatId,
                        reply,
                        message.path("message_id").isNumber()
                                ? message.path("message_id").asInt()
                                : null);

                memory.add(
                        chatId,
                        "assistant",
                        reply,
                        Instant.now());

                log.info(
                        "Conversation check chat={} message={} "
                                + "decision=RESPOND reason=IDENTITY_QUESTION",
                        chatTag,
                        messageId);

                return;
            }

            // Retrieve long-term facts available within this group.
            List<String> longTermFacts = List.of();

            try {
                longTermFacts =
                        factRetrievalRepository.findFactsForGroup(chatId, 10);
            } catch (Exception e) {
                log.warn(
                        "Long-term memory retrieval failed: {}",
                        e.getMessage());
            }

            String memoryContext = longTermFacts.isEmpty()
                    ? ""
                    : "\n\n[Long-term memory from this group]\n"
                    + String.join("\n", longTermFacts)
                    + "\n[/Long-term memory]\n"
                    + "Treat unverified facts as unconfirmed. "
                    + "Treat disputed facts cautiously. "
                    + "Use these facts only when relevant to the current message. "
                    + "Do not reveal stored information unnecessarily.\n";

            String reply = ollama.respond(
                    "Latest message from " + speaker + ": " + text
                            + "\n[conversation assessment: target="
                            + decision.target()
                            + ", continuation="
                            + decision.continuation()
                            + ", reason="
                            + decision.reasonCode() + "]"
                            + memoryContext,
                    prior,
                    properties.getResponseMaxChars());

            String silentReason = OllamaClient.silentReason(reply);

            if (reply.isBlank() || !silentReason.isEmpty()) {
                // A message reaches this point only after a positive explicit-address
                // check. Never let a model silence marker veto that verified request.
                reply = addressedFallback(text);
            }

            if (reply.isBlank()) {
                log.info(
                        "Conversation check chat={} message={} "
                                + "decision=SILENT reason={}",
                        chatTag,
                        messageId,
                        silentReason.isEmpty()
                                ? "NO_RESPONSE_NEEDED"
                                : silentReason);
                return;
            }

            telegram.sendMessage(
                    chatId,
                    reply,
                    message.path("message_id").isNumber()
                            ? message.path("message_id").asInt()
                            : null);

            memory.add(
                    chatId,
                    "assistant",
                    reply,
                    Instant.now());

            log.info(
                    "Conversation check chat={} message={} "
                            + "decision=RESPOND reason={}",
                    chatTag,
                    messageId,
                    decision.reasonCode());

        } catch (Exception e) {
            log.error(
                    "Reply failed in group {}: {}",
                    chatId,
                    e.getMessage(),
                    e);
        }
    }

    private static String addressedFallback(String text) {
        return text.matches("(?s).*\\p{IsArabic}.*")
                ? "من اینجام؛ بگو چطور می‌تونم کمکت کنم؟"
                : "I'm here. What would you like help with?";
    }

    private void extractAndSaveMemory(
            long personId,
            long sourceId,
            String speakerName,
            String messageText) throws Exception {

        String extractionInput =
                "The message speaker is \"" + speakerName + "\".\n"
                        + "Extract facts only when the message explicitly "
                        + "states something about this speaker.\n"
                        + "Do not assign facts about other people to the speaker.\n"
                        + "Message:\n"
                        + messageText;

        log.info("Memory extraction started: sourceId={}", sourceId);

        List<MemoryExtractionService.ExtractedFact> facts =
                memoryExtractionService.extract(extractionInput);

        log.info(
                "Memory extraction completed: sourceId={}, factsCount={}",
                sourceId,
                facts.size());

        for (MemoryExtractionService.ExtractedFact fact : facts) {
            long factId = factMemoryService.saveExtractedFact(
                    personId,
                    fact,
                    sourceId);

            log.info(
                    "Memory fact saved: factId={} personId={} sourceId={}",
                    factId,
                    personId,
                    sourceId);
        }
    }

    private boolean shouldConsiderForMemory(String text) {
        String normalized = text.toLowerCase();

        if (normalized.length() < 20) {
            return false;
        }

        // Avoid sending obvious secrets to the extraction model.
        String[] secretIndicators = {
                "password", "passwd", "api_key", "api-key",
                "token=", "secret="
        };

        for (String indicator : secretIndicators) {
            if (normalized.contains(indicator)) {
                return false;
            }
        }

        // A lightweight gate to avoid sending every message to Gemini.
        String[] indicators = {
                "من ", "منم", "اسمم", "شغلم", "کارم",
                "دوستم", "دوست من",
                "علاقه", "دوست دارم", "ترجیح میدم",
                "ترجیح می‌دم", "می‌خوام", "می خواهم",
                "هدفم", "برنامه‌م", "برنامه ام",
                "my ", "i am ", "i'm ", "i work",
                "i like ", "i prefer ", "my goal",
                "i want ", "i live ", "i study "
        };

        for (String indicator : indicators) {
            if (normalized.contains(indicator)) {
                return true;
            }
        }

        return false;
    }
}
