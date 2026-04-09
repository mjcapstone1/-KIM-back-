package depth.finvibe.shared.outbox;

import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.util.TimeUtil;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    public OutboxJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long append(String aggregateType, String aggregateId, String eventType, String eventKey, Object payload) {
        LocalDateTime now = LocalDateTime.now(TimeUtil.SEOUL);
        String payloadJson = Json.stringify(payload);

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO outbox_events (
                        aggregate_type,
                        aggregate_id,
                        event_type,
                        event_key,
                        payload_json,
                        status,
                        retry_count,
                        occurred_at,
                        next_attempt_at,
                        locked_at,
                        locked_by,
                        published_at,
                        published_topic,
                        published_partition,
                        published_offset,
                        last_error
                    ) VALUES (?, ?, ?, ?, ?, 'READY', 0, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, aggregateType);
            ps.setString(2, aggregateId);
            ps.setString(3, eventType);
            ps.setString(4, eventKey);
            ps.setString(5, payloadJson);
            ps.setTimestamp(6, Timestamp.valueOf(now));
            ps.setTimestamp(7, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public List<OutboxEventRow> selectReadyBatchForUpdate(int limit, LocalDateTime now) {
        return jdbcTemplate.query("""
                SELECT
                    event_id,
                    aggregate_type,
                    aggregate_id,
                    event_type,
                    event_key,
                    payload_json,
                    status,
                    retry_count,
                    occurred_at,
                    next_attempt_at
                FROM outbox_events
                WHERE status = 'READY'
                  AND next_attempt_at <= ?
                ORDER BY occurred_at ASC, event_id ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, (rs, rowNum) -> new OutboxEventRow(
                rs.getLong("event_id"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("event_key"),
                rs.getString("payload_json"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getTimestamp("occurred_at").toLocalDateTime(),
                rs.getTimestamp("next_attempt_at").toLocalDateTime()
        ), Timestamp.valueOf(now), limit);
    }

    public void markProcessing(List<Long> ids, String workerId, LocalDateTime now) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        String sql = """
                UPDATE outbox_events
                SET status = 'PROCESSING',
                    locked_by = ?,
                    locked_at = ?,
                    last_error = NULL
                WHERE status = 'READY'
                  AND event_id IN (%s)
                """.formatted(placeholders(ids.size()));

        Object[] args = new Object[ids.size() + 2];
        args[0] = workerId;
        args[1] = Timestamp.valueOf(now);
        for (int i = 0; i < ids.size(); i++) {
            args[i + 2] = ids.get(i);
        }

        jdbcTemplate.update(sql, args);
    }

    public void markPublished(long eventId, String topic, int partition, long offset, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'PUBLISHED',
                    published_at = ?,
                    published_topic = ?,
                    published_partition = ?,
                    published_offset = ?,
                    locked_at = NULL,
                    locked_by = NULL,
                    last_error = NULL
                WHERE event_id = ?
                """, Timestamp.valueOf(now), topic, partition, offset, eventId);
    }

    public void reschedule(long eventId, int maxRetryCount, LocalDateTime nextAttemptAt, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET retry_count = retry_count + 1,
                    status = CASE
                        WHEN retry_count + 1 >= ? THEN 'FAILED'
                        ELSE 'READY'
                    END,
                    next_attempt_at = CASE
                        WHEN retry_count + 1 >= ? THEN next_attempt_at
                        ELSE ?
                    END,
                    locked_at = NULL,
                    locked_by = NULL,
                    last_error = ?
                WHERE event_id = ?
                """,
                maxRetryCount,
                maxRetryCount,
                Timestamp.valueOf(nextAttemptAt),
                shorten(errorMessage),
                eventId
        );
    }

    public int recoverTimedOutProcessing(Duration timeout) {
        LocalDateTime cutoff = LocalDateTime.now(TimeUtil.SEOUL).minus(timeout);
        return jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'READY',
                    locked_at = NULL,
                    locked_by = NULL,
                    next_attempt_at = ?,
                    last_error = COALESCE(last_error, 'Recovered from stale PROCESSING lock')
                WHERE status = 'PROCESSING'
                  AND locked_at < ?
                """, Timestamp.valueOf(LocalDateTime.now(TimeUtil.SEOUL)), Timestamp.valueOf(cutoff));
    }

    private String placeholders(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "?")
                .collect(Collectors.joining(","));
    }

    private String shorten(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1500 ? message : message.substring(0, 1500);
    }
}