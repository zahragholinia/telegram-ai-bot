
package local.nana;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MessageSourceRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long upsertMessageSource(
            long telegramChatId,
            long telegramMessageId,
            Long speakerAccountId,
            String description
    ) {
        String sql = """
                INSERT INTO message_sources (
                    telegram_chat_id,
                    telegram_message_id,
                    speaker_account_id,
                    source_type,
                    description
                )
                VALUES (?, ?, ?, 'telegram_message', ?)
                ON CONFLICT (telegram_chat_id, telegram_message_id)
                DO UPDATE SET
                    speaker_account_id = EXCLUDED.speaker_account_id,
                    description = EXCLUDED.description
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                telegramChatId,
                telegramMessageId,
                speakerAccountId,
                emptyToNull(description)
        );

        if (id == null) {
            throw new IllegalStateException(
                    "Failed to save message source"
            );
        }

        return id;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
