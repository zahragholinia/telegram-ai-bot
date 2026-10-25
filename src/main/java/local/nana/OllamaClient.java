
package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

@Component
public class OllamaClient {
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String apiKey;

    private static final String MODEL = "gemini-3.5-flash-lite";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent";

    public OllamaClient(ObjectMapper mapper, NanaProperties properties) {
        this.mapper = mapper;

        this.apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY environment variable is missing");
        }

        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .proxy(ProxySelector.of(
                        new InetSocketAddress("127.0.0.1", 8081)))
                .build();
    }


    public String respond(
            String currentMessage,
            List<GroupMemory.Turn> context,
            int maxChars) throws Exception {

        ObjectNode body = mapper.createObjectNode();

        body.putObject("systemInstruction")
                .putArray("parts")
                .addObject()
                .put("text",
                        "You are Nana, a natural participant in a group chat. "
                                + "Your most important communication rule is to adapt "
                                + "your tone to the user's current message. "

                                + "If the user is friendly and informal, be friendly "
                                + "and informal. If they are polite or formal, respond "
                                + "with the same level of politeness and formality. "

                                + "If the user is angry, irritated, or speaks harshly, "
                                + "be direct, firm, and appropriately assertive. Do not "
                                + "sound overly sweet, cheerful, or preachy. "
                                + "If they joke, tease, or use sarcasm, respond in a "
                                + "similar spirit when appropriate. If they are sad or "
                                + "upset, be understanding and avoid inappropriate jokes. "

                                + "Notice slang, colloquial expressions, humor, warmth, "
                                + "and the emotional tone of each message. Match them "
                                + "naturally without exaggerating or copying their words. "
                                + "Do not use the same fixed tone for every person or message. "

                                + "If someone is rude, you may respond firmly, but do not "
                                + "escalate into unnecessary insults or hostility. "

                                + "Reply in the language the user uses. For Persian, "
                                + "use natural conversational Persian when appropriate. "
                                + "Keep your response style, directness, and length suited "
                                + "to the current message and the ongoing conversation. "

                                + "Use only the supplied conversation context. "
                                + "Be concise and aim for at most "
                                + maxChars + " characters.");

        ArrayNode contents = body.putArray("contents");

        for (GroupMemory.Turn turn : context) {
            String role = "assistant".equalsIgnoreCase(turn.role())
                    || "model".equalsIgnoreCase(turn.role())
                    ? "model" : "user";

            contents.addObject()
                    .put("role", role)
                    .putArray("parts")
                    .addObject()
                    .put("text", turn.text());
        }

        contents.addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", currentMessage);

        String requestBody = mapper.writeValueAsString(body);

        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();

            if (status / 100 == 2) {
                JsonNode root = mapper.readTree(response.body());

                String result = root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text")
                        .asText("")
                        .strip();

                if (result.isEmpty()) {
                    throw new IllegalStateException(
                            "Gemini returned an empty response");
                }

                if (result.length() > maxChars) {
                    result = result.substring(0, maxChars).stripTrailing();
                }

                return result;
            }

            boolean retryable = status == 429 || status >= 500;

            if (!retryable || attempt == 3) {
                throw new IllegalStateException(
                        "Gemini returned HTTP " + status + ": "
                                + response.body());
            }

            long delayMillis =
                    (long) (1000 * Math.pow(2, attempt - 1))
                            + ThreadLocalRandom.current().nextLong(250, 1000);

            Thread.sleep(delayMillis);
        }

        throw new IllegalStateException("Gemini retry attempts exhausted");
    }

}
