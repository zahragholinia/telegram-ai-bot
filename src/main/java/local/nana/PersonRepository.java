
package local.nana;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PersonRepository {

    private final JdbcTemplate jdbcTemplate;

    public PersonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createPerson(String preferredName) {
        String sql = """
                INSERT INTO persons (preferred_name)
                VALUES (?)
                RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                emptyToNull(preferredName)
        );

        if (id == null) {
            throw new IllegalStateException(
                    "Failed to create person"
            );
        }

        return id;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
