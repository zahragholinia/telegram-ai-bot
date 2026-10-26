package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelegramPollingServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TelegramClient.BotIdentity bot = new TelegramClient.BotIdentity(99, "ZaraNanaBot");

    @Test void ordinaryGreetingsQuestionsAndDiscussionNeverCallResponseGemini() throws Exception {
        Fixture f = fixture();
        f.service.handle(message(1, 10, "Hi everyone!"), bot);
        f.service.handle(message(1, 10, "What do you think?"), bot);
        f.service.handle(message(1, 10, "Nana was right earlier"), bot);
        f.service.handle(message(1, 10, "Nana, I am asking Ali what he thinks"), bot); // Explicit call is eligible.
        verify(f.gemini, times(1)).respond(anyString(), anyList(), anyInt());
        verify(f.telegram, times(1)).sendMessage(eq(1L), eq("reply"), any());
    }

    @Test void supportsEnglishPersianUsernameMentionAndDirectReplyToNana() throws Exception {
        Fixture f = fixture();
        when(f.gemini.respond(anyString(), anyList(), anyInt()))
                .thenReturn("English reply", "جواب فارسی", "Mention reply", "Reply-to reply");
        f.service.handle(message(2, 10, "Nana, what do you think?"), bot);
        f.service.handle(message(2, 10, "نانا، نظرت چیه؟"), bot);
        f.service.handle(message(2, 10, "@ZaraNanaBot please help"), bot);
        f.service.handle(mapper.readTree("{\"chat\":{\"id\":2,\"type\":\"group\"},"
                + "\"from\":{\"id\":10,\"first_name\":\"Ari\"},\"message_id\":4,"
                + "\"text\":\"What about this?\",\"reply_to_message\":{"
                + "\"from\":{\"id\":99,\"is_bot\":true}}}"), bot);
        verify(f.gemini, times(4)).respond(anyString(), anyList(), anyInt());
        verify(f.telegram, times(4)).sendMessage(eq(2L), anyString(), any());
    }

    @Test void replyToAnotherMemberDoesNotTriggerUnlessNanaIsAlsoAddressed() throws Exception {
        Fixture f = fixture();
        f.service.handle(mapper.readTree("{\"chat\":{\"id\":3,\"type\":\"group\"},"
                + "\"from\":{\"id\":10,\"first_name\":\"Ari\"},\"message_id\":1,"
                + "\"text\":\"What do you think?\",\"reply_to_message\":{"
                + "\"from\":{\"id\":20,\"is_bot\":false}}}"), bot);
        verifyNoInteractions(f.gemini);
        f.service.handle(mapper.readTree("{\"chat\":{\"id\":3,\"type\":\"group\"},"
                + "\"from\":{\"id\":10,\"first_name\":\"Ari\"},\"message_id\":2,"
                + "\"text\":\"Nana, what do you think?\",\"reply_to_message\":{"
                + "\"from\":{\"id\":20,\"is_bot\":false}}}"), bot);
        verify(f.gemini).respond(anyString(), anyList(), anyInt());
        verify(f.telegram).sendMessage(eq(3L), eq("reply"), any());
    }

    @Test void explicitIdentityQuestionUsesNanaIntroductionAndExplainsPolicyWithoutBypass() throws Exception {
        Fixture f = fixture();
        f.service.handle(message(4, 10, "Who are you, Nana?"), bot);
        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(f.telegram).sendMessage(eq(4L), reply.capture(), any());
        assertTrue(reply.getValue().contains("I'm Nana"));
        assertTrue(reply.getValue().contains("@ZaraNanaBot"));
        assertTrue(reply.getValue().contains("stay quiet"));
        verifyNoInteractions(f.gemini);

        Fixture unaddressed = fixture();
        unaddressed.service.handle(message(4, 10, "Who are you?"), bot);
        verifyNoInteractions(unaddressed.gemini);
        verifyNoInteractions(unaddressed.telegram);
    }

    @Test void addressedMessageStillGetsReplyWhenGeminiReturnsAInterruptedOrOtherPersonMarker() throws Exception {
        Fixture f = fixture();
        when(f.gemini.respond(anyString(), anyList(), anyInt()))
                .thenReturn("[[NANA_SILENT:OTHER_PERSON]]", "[[NANA_SILENT:WOULD_INTERRUPT]]");
        f.service.handle(message(5, 10, "Nana, Ali and I need your help"), bot);
        f.service.handle(message(5, 10, "@ZaraNanaBot, please weigh in on their discussion"), bot);
        verify(f.gemini, times(2)).respond(anyString(), anyList(), anyInt());
        verify(f.telegram, times(2)).sendMessage(eq(5L),
                eq("I'm here. What would you like help with?"), any());
    }

    @Test void ignoresNanaAndOtherBots() throws Exception {
        Fixture f = fixture();
        f.service.handle(message(6, 99, "NanaBot", "Nana, reply to yourself"), bot);
        JsonNode otherBot = mapper.readTree("{\"chat\":{\"id\":6,\"type\":\"group\"},"
                + "\"from\":{\"id\":77,\"is_bot\":true,\"username\":\"other_bot\"},"
                + "\"message_id\":2,\"text\":\"Nana please answer\"}");
        f.service.handle(otherBot, bot);
        verifyNoInteractions(f.gemini);
        verifyNoInteractions(f.telegram);
    }

    @Test void duplicateTelegramUpdateProducesOnlyOneResponse() throws Exception {
        Fixture f = fixture();
        JsonNode update = mapper.readTree("{\"update_id\":123,\"message\":"
                + "{\"chat\":{\"id\":7,\"type\":\"group\"},"
                + "\"from\":{\"id\":10,\"first_name\":\"Ari\"},"
                + "\"message_id\":5,\"text\":\"Nana, hello\"}}");
        f.service.processUpdate(update, bot);
        f.service.processUpdate(update, bot);
        verify(f.gemini, times(1)).respond(anyString(), anyList(), anyInt());
        verify(f.telegram, times(1)).sendMessage(eq(7L), anyString(), any());
    }

    @Test void restartWithEmptyHistoryStillRequiresAndAcceptsExplicitAddress() throws Exception {
        Fixture restarted = fixture();
        restarted.service.handle(message(8, 10, "What do you think?"), bot);
        verifyNoInteractions(restarted.gemini);
        verifyNoInteractions(restarted.telegram);

        Fixture freshAfterRestart = fixture();
        freshAfterRestart.service.handle(message(8, 10, "Nana, are you there?"), bot);
        ArgumentCaptor<List<GroupMemory.Turn>> context = ArgumentCaptor.forClass(List.class);
        verify(freshAfterRestart.gemini).respond(anyString(), context.capture(), anyInt());
        assertTrue(context.getValue().isEmpty());
        verify(freshAfterRestart.telegram).sendMessage(eq(8L), eq("reply"), any());
    }

    private Fixture fixture() throws Exception {
        TelegramClient telegram = mock(TelegramClient.class);
        OllamaClient gemini = mock(OllamaClient.class);
        when(gemini.respond(anyString(), anyList(), anyInt())).thenReturn("reply");
        NanaProperties properties = new NanaProperties();
        properties.getRateLimit().setGroupSeconds(0);
        TelegramPollingService service = new TelegramPollingService(telegram, gemini, properties,
                mock(TelegramAccountRepository.class), mock(PersonIdentityService.class),
                mock(MessageSourceRepository.class), mock(MemoryExtractionService.class),
                mock(FactMemoryService.class), mock(FactRetrievalRepository.class),
                new NanaIdentityResponder());
        return new Fixture(service, telegram, gemini);
    }

    private JsonNode message(long chat, long user, String text) throws Exception {
        return message(chat, user, "Ari", text);
    }

    private JsonNode message(long chat, long user, String name, String text) {
        var root = mapper.createObjectNode();
        root.putObject("chat").put("id", chat).put("type", "group");
        root.putObject("from").put("id", user).put("first_name", name);
        root.put("message_id", 1).put("text", text);
        return root;
    }

    private record Fixture(TelegramPollingService service, TelegramClient telegram,
                           OllamaClient gemini) {}
}
