package depth.finvibe.shared.outbox;

import java.time.LocalDateTime;

public record OutboxEventRow(
        long eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String eventKey,
        String payloadJson,
        String status,
        int retryCount,
        LocalDateTime occurredAt,
        LocalDateTime nextAttemptAt
) {
}