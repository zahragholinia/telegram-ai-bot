
package local.nana;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PersonAccountLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    public PersonAccountLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long linkAccountToPerson(
            long personId,
            long accountId
    ) {
        String sql = """
                INSERT INTO person_account_links (
                    person_id,
                    account_id,
                    verification_status
                )
                VALUES (?, ?, 'unverified')
                ON CONFLICT (person_id, account_id)
                DO UPDATE SET
                    person_id = EXCLUDED.person_id
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                personId,
                accountId
        );

        if (id == null) {
            throw new IllegalStateException(
                    "Failed to link account to person"
            );
        }

        return id;
    }

    public Long findPersonIdByAccountId(long accountId) {
        String sql = """
                SELECT person_id
                FROM person_account_links
                WHERE account_id = ?
                  AND verification_status <> 'rejected'
                """;

        java.util.List<Long> personIds = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("person_id"),
                accountId
        );

        if (personIds.size() > 1) {
            throw new IllegalStateException(
                    "Telegram account is linked to multiple persons"
            );
        }

        return personIds.isEmpty() ? null : personIds.get(0);
    }

}
