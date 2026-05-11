package depth.finvibe.shared.outbox;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConsumedEventJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    public ConsumedEventJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryMarkConsumed(String consumerName, long eventId) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO consumed_events (consumer_name, event_id)
                    VALUES (?, ?)
                    """, consumerName, eventId);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

}


