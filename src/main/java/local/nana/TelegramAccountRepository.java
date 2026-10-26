package local.nana;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TelegramAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public TelegramAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long upsertAccount(
            long telegramUserId,
            String username,
            String displayName
    ) {
        String sql = """
                INSERT INTO telegram_accounts (
                    telegram_user_id,
                    username,
                    display_name
                )
                VALUES (?, ?, ?)
                ON CONFLICT (telegram_user_id)
                DO UPDATE SET
                    username = EXCLUDED.username,
                    display_name = EXCLUDED.display_name,
                    updated_at = NOW()
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                telegramUserId,
                emptyToNull(username),
                emptyToNull(displayName)
        );

        if (id == null) {
            throw new IllegalStateException(
                    "Failed to save Telegram account"
            );
        }

        return id;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}