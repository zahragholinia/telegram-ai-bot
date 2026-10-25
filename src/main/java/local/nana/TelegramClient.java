package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.ProxySelector;
import java.net.InetSocketAddress;
import org.springframework.stereotype.Component;

@Component
public class TelegramClient {
    public record BotIdentity(long id, String username) {}
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final NanaProperties properties;
    private final String token;
    public TelegramClient(ObjectMapper mapper, NanaProperties properties) {
        this.mapper = mapper; this.properties = properties;
        this.token = requireToken();

        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(
                        properties.getTelegram().getConnectTimeoutSeconds()))
                .proxy(ProxySelector.of(
                        new InetSocketAddress("127.0.0.1", 8081)))
                .build();

    }
    public BotIdentity getMe() throws Exception {
        JsonNode result = call("getMe", null, 20);
        return new BotIdentity(result.path("id").asLong(), result.path("username").asText(""));
    }
    public JsonNode getUpdates(long offset) throws Exception {
        ObjectNode params = mapper.createObjectNode().put("offset", offset)
                .put("timeout", properties.getTelegram().getPollTimeoutSeconds());
        params.putArray("allowed_updates").add("message");
        return call("getUpdates", params, properties.getTelegram().getPollTimeoutSeconds() + 15);
    }
    public int sendMessage(long chatId, String text, Integer replyToMessageId) throws Exception {
        ObjectNode params = mapper.createObjectNode().put("chat_id", chatId).put("text", text);
        if (replyToMessageId != null) params.put("reply_to_message_id", replyToMessageId);
        return call("sendMessage", params, 30).path("message_id").asInt();
    }

    private JsonNode call(String method, ObjectNode params, int timeoutSeconds)
            throws Exception {

        String endpoint = properties.getTelegram().getApiBase()
                .replaceAll("/+$", "") + "/bot" + token + "/" + method;

        String requestBody = params == null
                ? null
                : mapper.writeValueAsString(params);

        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds));

            if (params == null) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            }

            try {
                HttpResponse<String> response = http.send(
                        builder.build(),
                        HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();

                if (status / 100 == 2) {
                    JsonNode root = mapper.readTree(response.body());

                    if (!root.path("ok").asBoolean(false)) {
                        throw new IllegalStateException(
                                "Telegram API request failed with code "
                                        + root.path("error_code").asInt());
                    }

                    return root.path("result");
                }

                if (status < 500 || attempt == 3) {
                    throw new IllegalStateException(
                            "Telegram returned HTTP " + status);
                }

            } catch (java.io.IOException e) {
                if (attempt == 3) {
                    throw e;
                }
            }

            Thread.sleep(1000L * attempt);
        }

        throw new IllegalStateException("Telegram retry attempts exhausted");
    }

    private static String requireToken() {
        String value = System.getenv("TELEGRAM_BOT_TOKEN");
        if (value == null || value.isBlank()) throw new IllegalStateException("Set TELEGRAM_BOT_TOKEN in the process environment");
        return value.strip();
    }
}
