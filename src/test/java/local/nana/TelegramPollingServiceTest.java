package local.nana;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramPollingServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void explicitAddressStillWorksAndNanaReplyAppearsInNextContext() throws Exception {
        TelegramClient telegram = mock(TelegramClient.class);
        OllamaClient gemini = mock(OllamaClient.class); // OllamaClient is the existing Gemini API adapter.
        when(gemini.respond(anyString(), anyList(), anyInt())).thenReturn("Here is an answer.");
        NanaProperties properties = new NanaProperties();
        properties.getRateLimit().setGroupSeconds(0);
        TelegramPollingService service = new TelegramPollingService(telegram, gemini, properties);
        var bot = new TelegramClient.BotIdentity(99, "nana_bot");

        service.handle(mapper.readTree("{\"chat\":{\"id\":1,\"type\":\"group\"},\"from\":{\"id\":10,\"first_name\":\"Ari\"},\"message_id\":1,\"text\":\"just chatting\"}"), bot);
        verifyNoInteractions(gemini);

        service.handle(mapper.readTree("{\"chat\":{\"id\":1,\"type\":\"group\"},\"from\":{\"id\":10,\"first_name\":\"Ari\"},\"message_id\":2,\"text\":\"Nana, can you explain?\"}"), bot);
        service.handle(mapper.readTree("{\"chat\":{\"id\":1,\"type\":\"group\"},\"from\":{\"id\":10,\"first_name\":\"Ari\"},\"message_id\":3,\"text\":\"I meant the second part\"}"), bot);
        verify(gemini, times(2)).respond(contains("Ari [userId=10]"), anyList(), anyInt());
        ArgumentCaptor<List<GroupMemory.Turn>> contexts = ArgumentCaptor.forClass(List.class);
        verify(gemini, times(2)).respond(anyString(), contexts.capture(), anyInt());
        assertTrue(contexts.getAllValues().get(1).stream()
                .anyMatch(turn -> "assistant".equals(turn.role())
                        && turn.text().equals("Here is an answer.")));
        verify(telegram).sendMessage(eq(1L), eq("Here is an answer."), eq(2));
        verify(telegram).sendMessage(eq(1L), eq("Here is an answer."), eq(3));
    }

    @Test void followsSixTurnMultiParticipantConversationAndKeepsNanaTurnsInContext() throws Exception {
        TelegramClient telegram = mock(TelegramClient.class);
        OllamaClient gemini = mock(OllamaClient.class);
        when(gemini.respond(anyString(), anyList(), anyInt())).thenReturn(
                "چه جور رستورانی بود؟", "ایتالیایی بود؟", "چی سفارش دادی؟",
                "چه خوب!", OllamaClient.SILENT_RESPONSE, "پاستا همیشه انتخاب خوبیه.",
                "با سس تند هم عالی میشه!");
        NanaProperties properties = new NanaProperties();
        properties.getRateLimit().setGroupSeconds(0);
        TelegramPollingService service = new TelegramPollingService(telegram, gemini, properties);
        var bot = new TelegramClient.BotIdentity(99, "nana_bot");
        long chat = 42;
        service.handle(message(chat, 10, "A", 1, "Nana، رستوران جدید ایتالیایی رو دیدی؟"), bot);
        service.handle(message(chat, 10, "A", 2, "آره، خیلی خوب بود"), bot);
        service.handle(message(chat, 20, "B", 3, "چی سفارش دادی؟"), bot);
        service.handle(message(chat, 10, "A", 4, "پاستا گرفتم"), bot);
        service.handle(message(chat, 20, "B", 5, "منم عاشق پاستام"), bot);
        service.handle(message(chat, 30, "C", 6, "منم یه رستوران خوب می‌شناسم"), bot);
        service.handle(message(chat, 20, "B", 7, "با سس تندش خیلی خوب میشه"), bot);

        ArgumentCaptor<List<GroupMemory.Turn>> contexts = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> currentMessages = ArgumentCaptor.forClass(String.class);
        verify(gemini, times(7)).respond(currentMessages.capture(), contexts.capture(), anyInt());
        List<GroupMemory.Turn> seventhContext = contexts.getAllValues().get(6);
        assertTrue(seventhContext.stream().anyMatch(turn -> turn.text().contains("userId=20")));
        assertTrue(seventhContext.stream().anyMatch(turn -> turn.text().contains("userId=30")));
        assertTrue(seventhContext.stream().filter(turn -> "assistant".equals(turn.role())).count() >= 4);
        assertTrue(seventhContext.stream().anyMatch(turn -> turn.text().contains("پاستا گرفتم")));
        assertTrue(currentMessages.getAllValues().get(6).contains("سس تند"));
        verify(telegram, times(6)).sendMessage(eq(chat), anyString(), any());
    }

    @Test void letsGeminiSilentlyRejectTopicChangeAndSkipsClearOtherPersonReply() throws Exception {
        TelegramClient telegram = mock(TelegramClient.class);
        OllamaClient gemini = mock(OllamaClient.class);
        when(gemini.respond(anyString(), anyList(), anyInt()))
                .thenReturn("حرف بزنیم", OllamaClient.SILENT_RESPONSE,
                        OllamaClient.SILENT_RESPONSE);
        NanaProperties properties = new NanaProperties();
        properties.getRateLimit().setGroupSeconds(0);
        TelegramPollingService service = new TelegramPollingService(telegram, gemini, properties);
        var bot = new TelegramClient.BotIdentity(99, "nana_bot");
        service.handle(message(9, 10, "A", 1, "Nana نظرت درباره سفر چیه؟"), bot);
        service.handle(message(9, 10, "A", 2, "راستی بازی امشب رو دیدین؟"), bot);
        assertEquals(2, mockingDetails(gemini).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("respond")).count());
        verify(telegram, times(1)).sendMessage(eq(9L), anyString(), any());

        service.handle(message(9, 20, "B", 3, "سلام"), bot);
        var directed = mapper.readTree("{\"chat\":{\"id\":9,\"type\":\"group\"},"
                + "\"from\":{\"id\":10,\"first_name\":\"A\"},\"message_id\":4,"
                + "\"text\":\"Ali, are you coming?\",\"reply_to_message\":{"
                + "\"from\":{\"id\":20}}}");
        service.handle(directed, bot);
        verify(gemini, times(3)).respond(anyString(), anyList(), anyInt());
    }

    @Test void ignoresIncomingEchoFromNana() throws Exception {
        TelegramClient telegram = mock(TelegramClient.class);
        OllamaClient gemini = mock(OllamaClient.class);
        TelegramPollingService service = new TelegramPollingService(telegram, gemini,
                new NanaProperties());
        service.handle(message(3, 99, "nana_bot", 1, "Nana's own reply"),
                new TelegramClient.BotIdentity(99, "nana_bot"));
        verifyNoInteractions(gemini);
        verifyNoInteractions(telegram);
    }

    @Test void doesNotRecordReplyWhenTelegramRejectsTheSend() throws Exception {
        TelegramClient telegram = mock(TelegramClient.class);
        doThrow(new IllegalStateException("Telegram rejected message"))
                .when(telegram).sendMessage(anyLong(), anyString(), any());
        OllamaClient gemini = mock(OllamaClient.class);
        when(gemini.respond(anyString(), anyList(), anyInt())).thenReturn("reply");
        NanaProperties properties = new NanaProperties();
        properties.getRateLimit().setGroupSeconds(0);
        TelegramPollingService service = new TelegramPollingService(telegram, gemini, properties);
        var bot = new TelegramClient.BotIdentity(99, "nana_bot");

        service.handle(message(5, 10, "A", 1, "Nana, are you there?"), bot);
        service.handle(message(5, 10, "A", 2, "here is my follow-up"), bot);

        ArgumentCaptor<List<GroupMemory.Turn>> contexts = ArgumentCaptor.forClass(List.class);
        verify(gemini, times(2)).respond(anyString(), contexts.capture(), anyInt());
        assertTrue(contexts.getAllValues().get(1).stream()
                .noneMatch(turn -> "assistant".equals(turn.role())));
    }

    private com.fasterxml.jackson.databind.JsonNode message(long chat, long user,
            String name, int id, String text) throws Exception {
        return mapper.readTree("{\"chat\":{\"id\":" + chat + ",\"type\":\"group\"},"
                + "\"from\":{\"id\":" + user + ",\"first_name\":\"" + name
                + "\"},\"message_id\":" + id + ",\"text\":\"" + text + "\"}");
    }
}
