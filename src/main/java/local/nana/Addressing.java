package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Addressing {
    private Addressing() {}
    public static boolean isAddressed(JsonNode message, long botId, String botUsername) {
        JsonNode reply = message.path("reply_to_message");
        if (!reply.isMissingNode() && reply.path("from").path("id").asLong(Long.MIN_VALUE) == botId) return true;
        String text = message.path("text").asText("");
        if (text.isBlank()) text = message.path("caption").asText("");
        if (Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])(?:nana|نانا)(?![\\p{L}\\p{N}_])")
                .matcher(text).find()) {
            return true;
        }
        if (botUsername == null || botUsername.isBlank()) return false;
        String username = "@" + botUsername.replaceFirst("^@", "");
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(username) + "(?![\\p{L}\\p{N}_])")
                .matcher(text).find();
    }
}
