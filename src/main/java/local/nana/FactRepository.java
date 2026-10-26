
package local.nana;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FactRepository {

    private final JdbcTemplate jdbcTemplate;

    public FactRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockPersonFacts(long personId) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(?)",
                Object.class,
                personId
        );
    }

    public List<ActiveFact> findActiveFacts(long personId) {
        String sql = """
            SELECT id, fact_key, fact_value::text AS fact_value_json
            FROM facts
            WHERE subject_person_id = ?
              AND valid_until IS NULL
            ORDER BY id
            FOR UPDATE
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ActiveFact(
                        rs.getLong("id"),
                        rs.getString("fact_key"),
                        rs.getString("fact_value_json")
                ),
                personId
        );
    }

    public void expireFact(long factId) {
        String sql = """
            UPDATE facts
            SET valid_until = NOW(),
                updated_at = NOW()
            WHERE id = ?
              AND valid_until IS NULL
            """;

        jdbcTemplate.update(sql, factId);
    }

    public long saveFact(
            long subjectPersonId,
            String factKey,
            String factValueJson,
            String humanReadableContent,
            BigDecimal confidence,
            String evidenceType,
            String visibility,
            long sourceId) {

        return saveFactVersion(
                subjectPersonId,
                factKey,
                factValueJson,
                humanReadableContent,
                confidence,
                evidenceType,
                visibility,
                sourceId,
                null
        );
    }

    public long saveFactVersion(
            long subjectPersonId,
            String factKey,
            String factValueJson,
            String humanReadableContent,
            BigDecimal confidence,
            String evidenceType,
            String visibility,
            long sourceId,
            Long supersedesFactId) {

        String sql = """
            INSERT INTO facts (
                subject_person_id,
                fact_key,
                fact_value,
                human_readable_content,
                confidence,
                evidence_type,
                verification_status,
                visibility,
                source_id,
                valid_from,
                supersedes_fact_id
            )
            VALUES (
                ?, ?, CAST(? AS jsonb), ?, ?, ?,
                'unverified', ?, ?, NOW(), ?
            )
            RETURNING id
            """;

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                subjectPersonId,
                factKey,
                factValueJson,
                humanReadableContent,
                confidence,
                evidenceType,
                visibility,
                sourceId,
                supersedesFactId
        );
    }

    public record ActiveFact(
            long id,
            String factKey,
            String factValueJson) {
    }
}
