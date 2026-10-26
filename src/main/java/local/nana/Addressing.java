package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** The single source of truth for explicit Telegram addresses to Nana. */
public final class Addressing {
    private static final Pattern ENGLISH_PREFIX_CALL = Pattern.compile("(?iu)^\\s*(?:(?:hey|hi|hello|dear|please|good\\s+morning)\\s+)?nana\\b(?=\\s|[,!?.:;…]|$)(.*)$");
    private static final Pattern PERSIAN_PREFIX_CALL = Pattern.compile("(?iu)^\\s*نانا(?=\\s|[,!?.:؛،؟…]|$)(.*)$");
    private static final Pattern ENGLISH_STATEMENT = Pattern.compile("(?iu)^\\s*(?:was|were|is|are|am|has|have|had|said|says|thinks|thought|went|came|called|wrote|did|does|will|would|looks|seems|means|knows|likes|lives|works|asked|told|mentioned|texted|messaged|reminded|warned|taught|helped|joined|left|agreed|answered)\\b");
    private static final Pattern PERSIAN_STATEMENT = Pattern.compile("(?iu)^\\s*(?:درست|قبلا|قبلاً|گفت|گفته|میگفت|می\\s+گفت|بود|هست|است|رفت|اومد|آمد|نوشت|فکر\\s+میکرد|فکر\\s+می\\s+کرد)(?=\\s|[؟?!.,،؛:]|$)");
    private static final Pattern ENGLISH_DIRECT_FOLLOWER = Pattern.compile("(?iu)^\\s*(?:what|how|why|when|where|who|can|could|would|will|do\\s+you|did\\s+you|are\\s+you|please|help|tell\\s+me|say|quick\\s+question|hi|hello)\\b");
    private static final Pattern PERSIAN_DIRECT_FOLLOWER = Pattern.compile("(?iu)^\\s*(?:نظرت|میشه|می\\s+شه|میتونی|می\\s+تونی|کمک|چطوری|چی|چرا|کجا|کی|لطفا|لطفاً|بگو|یه\\s+سوال|سلام|راهنمایی|جان|جون)(?=\\s|[؟?!.,،؛:]|$)");

    private Addressing() {}

    public static boolean isAddressed(JsonNode message, long botId, String botUsername) {
        if (isReplyToBot(message, botId)) return true;
        String text = message.path("text").asText("");
        if (text.isBlank()) text = message.path("caption").asText("");
        if (hasValidMention(message, text, botId, botUsername)) return true;
        return callsNanaByName(text);
    }

    private static boolean isReplyToBot(JsonNode message, long botId) {
        JsonNode sender = message.path("reply_to_message").path("from");
        return sender.path("id").asLong(Long.MIN_VALUE) == botId
                && sender.path("is_bot").asBoolean(false);
    }

    private static boolean hasValidMention(JsonNode message, String text, long botId,
                                           String botUsername) {
        if (botUsername != null && !botUsername.isBlank()) {
            String username = botUsername.replaceFirst("^@", "");
            Pattern mention = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])@"
                    + Pattern.quote(username) + "(?![\\p{L}\\p{N}_])");
            if (mention.matcher(text).find()) return true;
        }
        return containsTextMention(message.path("entities"), botId)
                || containsTextMention(message.path("caption_entities"), botId);
    }

    private static boolean containsTextMention(JsonNode entities, long botId) {
        if (!entities.isArray()) return false;
        for (JsonNode entity : entities) {
            if ("text_mention".equals(entity.path("type").asText())
                    && entity.path("user").path("id").asLong(Long.MIN_VALUE) == botId) {
                return true;
            }
        }
        return false;
    }

    static boolean callsNanaByName(String original) {
        if (original == null || original.isBlank()) return false;
        String text = normalize(original);
        if (Pattern.compile("(?iu)^\\s*(?:hey|hi|hello)\\s+nana\\b").matcher(text).find()
                || Pattern.compile("(?iu)^\\s*سلام\\s+نانا(?:\\s|[,،!؟]|$)").matcher(text).find()) {
            return true;
        }
        var english = ENGLISH_PREFIX_CALL.matcher(text);
        if (english.matches()) {
            String rawTail = english.group(1);
            String tail = stripCallPunctuation(rawTail);
            if (rawTail.matches("(?s)^\\s*[,!?.:;…].*")) return true;
            if (tail.isEmpty() || (!ENGLISH_STATEMENT.matcher(tail).find()
                    && ENGLISH_DIRECT_FOLLOWER.matcher(tail).find())) return true;
        }
        var persian = PERSIAN_PREFIX_CALL.matcher(text);
        if (persian.matches()) {
            String rawTail = persian.group(1);
            String tail = stripCallPunctuation(rawTail)
                    .replaceFirst("^(?:جان|جون)\\s+", "");
            if (rawTail.matches("(?s)^\\s*[,!?.:؛،؟…].*")) return true;
            if (tail.isEmpty() || (!PERSIAN_STATEMENT.matcher(tail).find()
                    && PERSIAN_DIRECT_FOLLOWER.matcher(tail).find())) return true;
        }
        // A name used as a vocative at the end is an address (e.g. "Who are you, Nana?").
        return Pattern.compile("(?iu)(?:[,،]\\s*|\\s+)(?:nana|نانا)\\s*[?!؟.!…]*$")
                .matcher(text).find()
                && Pattern.compile("(?iu)(?:\\?|؟|who|what|how|why|where|when|نظرت|کی|چی|چرا|چطوری)")
                        .matcher(text).find();
    }

    private static String stripCallPunctuation(String value) {
        return value.replaceFirst("^[\\s,،!?.:؛؟…]+", "");
    }

    private static String normalize(String value) {
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
                .replace('ۀ', 'ه').replace('ة', 'ه')
                .replace("ـ", "").replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replace('\u200c', ' ').replace('\u200d', ' ');
        return text.replaceAll("ناناا+", "نانا")
                .replaceAll("\\s+", " ").strip();
    }
}
