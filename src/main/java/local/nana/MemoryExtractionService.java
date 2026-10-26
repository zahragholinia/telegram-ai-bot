
package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryExtractionService {

    private final OllamaClient gemini;
    private final ObjectMapper mapper;

    public MemoryExtractionService(
            OllamaClient gemini,
            ObjectMapper mapper) {
        this.gemini = gemini;
        this.mapper = mapper;
    }

    public List<ExtractedFact> extract(String message)
            throws Exception {

        if (message == null || message.isBlank()) {
            return List.of();
        }

        ObjectNode body = mapper.createObjectNode();

        body.putObject("systemInstruction")
                .putArray("parts")
                .addObject()
                .put("text", """
                    You extract useful long-term facts from group chat messages.

                    Return JSON only, using this structure:
                    {
                      "facts": [
                        {
                          "key": "string",
                          "value": "string",
                          "content": "short human-readable fact",
                          "evidence_type": "direct or reported or inferred",
                          "confidence": 0.0,
                          "about_speaker": true
                        }
                      ]
                    }

                    Rules:
                    - Extract only useful, stable information.
                    - Do not invent facts.
                    - A direct statement is not independently verified.
                    - The application saves extracted facts to the sender's profile.
                    - Set about_speaker=true ONLY when the message clearly
                      states a fact about the sender themselves.
                    - Set about_speaker=false when the fact is about another
                      person, a friend, a relative, or someone else.
                    - If the owner of the fact is ambiguous, set false.
                    - Mentioning someone's name is not enough to establish
                      that the person is the sender.
                    - Do not treat a third-person statement as a self-statement,
                      even if the named person might be the sender.
                    - A reported claim about the sender is not the same as
                      the sender explicitly stating it about themselves.
                    - Do not extract passwords, tokens, or authentication secrets.
                    - Do not extract sensitive personal information.
                    - Do not turn jokes or obvious sarcasm into facts.
                    - Return an empty facts array if there are no suitable facts.
                    - Return at most 3 facts.
                    """);

        body.putObject("generationConfig")
                .put("responseMimeType", "application/json");

        body.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", message);

        String response = gemini.generateContent(body);
        JsonNode root = mapper.readTree(response);
        JsonNode facts = root.path("facts");

        System.out.println(
                "Memory extraction response: factsArray=" + facts.isArray()
                        + ", factsCount=" + (facts.isArray() ? facts.size() : -1)
        );

        if (!facts.isArray()) {
            return List.of();
        }

        List<ExtractedFact> result = new ArrayList<>();

        for (JsonNode fact : facts) {
            // Reject facts not explicitly attributed to the sender.
            if (!fact.path("about_speaker").asBoolean(false)) {
                System.out.println(
                        "Memory fact rejected: about_speaker is false or missing");
                continue;
            }


            String key = fact.path("key").asText("").strip();
            String value = fact.path("value").asText("").strip();
            String content = fact.path("content").asText("").strip();
            String evidence = fact.path("evidence_type")
                    .asText("").strip();
            double confidence = fact.path("confidence").asDouble(-1);

            if (key.isBlank()
                    || value.isBlank()
                    || content.isBlank()
                    || key.length() > 150
                    || value.length() > 1000
                    || content.length() > 1000
                    || !List.of("direct", "reported", "inferred")
                    .contains(evidence)
                    || !Double.isFinite(confidence)
                    || confidence < 0
                    || confidence > 1) {
                continue;
            }

            result.add(new ExtractedFact(
                    key,
                    mapper.writeValueAsString(value),
                    content,
                    evidence,
                    confidence));
        }

        return List.copyOf(result);
    }

    public record ExtractedFact(
            String key,
            String valueJson,
            String content,
            String evidenceType,
            double confidence) {
    }
}
