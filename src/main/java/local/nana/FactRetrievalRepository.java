
package local.nana;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FactRetrievalRepository {

    private final JdbcTemplate jdbcTemplate;

    public FactRetrievalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findFactsForGroup(long chatId, int limit) {
        String sql = """
            SELECT
                COALESCE(
                    NULLIF(p.preferred_name, ''),
                    NULLIF(a.display_name, ''),
                    'عضو گروه'
                ) AS person_name,
                f.human_readable_content,
                f.evidence_type,
                f.verification_status
            FROM facts f
            JOIN message_sources ms ON ms.id = f.source_id
            JOIN persons p ON p.id = f.subject_person_id
            LEFT JOIN LATERAL (
                SELECT ta.display_name
                FROM person_account_links pal
                JOIN telegram_accounts ta ON ta.id = pal.account_id
                WHERE pal.person_id = p.id
                  AND pal.verification_status <> 'rejected'
                ORDER BY pal.id
                LIMIT 1
            ) a ON TRUE
            WHERE ms.telegram_chat_id = ?
              AND f.visibility = 'source_group_only'
              AND f.verification_status <> 'rejected'
              AND (f.valid_until IS NULL OR f.valid_until > NOW())
            ORDER BY
                CASE f.verification_status
                    WHEN 'confirmed' THEN 0
                    WHEN 'unverified' THEN 1
                    WHEN 'disputed' THEN 2
                    ELSE 3
                END,
                f.confidence DESC,
                f.id DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String status = rs.getString("verification_status");
                    String evidence = rs.getString("evidence_type");

                    return "شخص: " + rs.getString("person_name")
                            + " | اطلاعات: "
                            + rs.getString("human_readable_content")
                            + " | نوع شواهد: " + evidence
                            + " | وضعیت تأیید: " + status;
                },
                chatId,
                Math.max(1, Math.min(limit, 15))
        );
    }
}
