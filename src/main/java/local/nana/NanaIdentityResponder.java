package local.nana;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Produces an introduction only after the caller has passed Addressing's gate. */
@Component
public class NanaIdentityResponder {
    public Optional<String> respondIfIdentityQuestion(JsonNode message,
                                                       long botId,
                                                       String botUsername) {
        if (message == null) return Optional.empty();
        if (!Addressing.isAddressed(message, botId, botUsername)) {
            return Optional.empty();
        }
        String text = message.path("text").asText("");
        if (text.isBlank()) text = message.path("caption").asText("");
        return introductionFor(text);
    }

    private Optional<String> introductionFor(String message) {
        if (message == null || message.isBlank()) return Optional.empty();
        String text = normalize(message);
        if (!isIdentityQuestion(text)) return Optional.empty();

        boolean persian = text.matches("(?s).*\\p{IsArabic}.*");
        boolean formal = text.contains("لطفا") || text.contains("لطفاً")
                || text.contains("could you") || text.contains("please");
        boolean playful = text.contains("😂") || text.contains("🤣") || text.contains("😅");
        if (persian) {
            if (formal) return Optional.of(
                    " من نانام، دخترِ زهرا!\n"
                            + "اومدم به سؤال‌هات جواب بدم، کمکت کنم، تو بحثاتون دخالت کنم "
                            + "و یه کمم اظهارنظر بی‌موقع داشته باشم  "
                            + "خلاصه هم مشاورم، هم هم‌صحبت، هم رفیق فضول جمع! "
                            + "من فقط وقتی جواب می‌دم که صدام کنین و بگین «نانا»، "
                            + "منشنم کنین، یا مستقیم به پیامم ریپلای بزنین. "
                            + "پس اگه دارین با هم حرف می‌زنین و منو صدا نکردین، "
                            + "نگران نباشین، فضولی نمی‌کنم و ساکت می‌مونم! \n"
            );
            if (playful) return Optional.of(
                    "من نانام، دخترِ زهرا!\n"
                            + "اومدم به سؤال‌هات جواب بدم، کمکت کنم، تو بحثاتون دخالت کنم "
                            + "و یه کمم اظهارنظر بی‌موقع داشته باشم  "
                            + "خلاصه هم مشاورم، هم هم‌صحبت، هم رفیق فضول جمع! "
                            + "من فقط وقتی جواب می‌دم که صدام کنین و بگین «نانا»، "
                            + "منشنم کنین، یا مستقیم به پیامم ریپلای بزنین. "
                            + "پس اگه دارین با هم حرف می‌زنین و منو صدا نکردین، "
                            + "نگران نباشین، فضولی نمی‌کنم و ساکت می‌مونم! \n"
            );
            return Optional.of(
                    "من نانام، دخترِ زهرا!\n"
                            + "اومدم به سؤال‌هات جواب بدم، کمکت کنم، تو بحثاتون دخالت کنم "
                            + "و یه کمم اظهارنظر بی‌موقع داشته باشم "
                            + "خلاصه هم مشاورم، هم هم‌صحبت، هم رفیق فضول جمع! "
                            + "من فقط وقتی جواب می‌دم که صدام کنین و بگین «نانا»، "
                            + "منشنم کنین، یا مستقیم به پیامم ریپلای بزنین. "
                            + "پس اگه دارین با هم حرف می‌زنین و منو صدا نکردین، "
                            + "نگران نباشین، فضولی نمی‌کنم و ساکت می‌مونم! \n"
            );
        }
        if (formal) return Optional.of("I'm Nana, a girl and Zahra's daughter. I'm here to answer questions, help, and join discussions. I respond only when you call me Nana, mention @ZaraNanaBot, or directly reply to one of my messages. If a question isn't addressed to me, I'll stay quiet.");
        if (playful) return Optional.of("I'm Nana, Zahra's daughter, and a girl. I'm here to help, answer questions, and join the conversation. Call me Nana, mention @ZaraNanaBot, or reply to one of my messages when you want me in; otherwise I'll keep quiet 🙂");
        return Optional.of("I'm Nana, a girl and Zahra's daughter, here to answer questions, help, and take part in discussions. I reply only when you call me by name, mention @ZaraNanaBot, or directly reply to one of my messages. If you ask something without addressing me, I'll stay quiet.");
    }

    private static boolean isIdentityQuestion(String text) {
        String[] phrases = {
                "تو کی هستی", "تو کیستی", "خودتو معرفی کن", "خودت رو معرفی کن",
                "خودت را معرفی کن", "خودتو معرفی میکنی", "خودت رو معرفی میکنی",
                "اسمت چیه", "اسم تو چیه", "اسم شما چیه", "اسم شما چیست", "اسم تو چیست",
                "تو چی هستی", "شما کی هستی", "شما کیستید", "درباره خودت بگو",
                "از خودت بگو", "معرفی کن خودتو", "تو رباتی", "تو هوش مصنوعی هستی",
                "چیکار میکنی", "چیکار می کنی", "چه کار میکنی", "چه کار می کنی",
                "چه کاری انجام میدی", "چه کمکی میکنی"
        };
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return text.contains("who are you") || text.contains("what is your name")
                || text.contains("introduce yourself") || text.contains("tell us about yourself")
                || text.contains("are you a bot") || text.contains("what do you do")
                || text.contains("what can you do") || text.contains("how can you help");
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
                .replace('\u200c', ' ').replace('\u200d', ' ')
                .replaceAll("[؟?!.,،؛:]+", " ").replaceAll("\\s+", " ").strip();
    }
}
