package depth.finvibe.shared.outbox;

import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.ranking.UserProfitSnapshotDailyService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderExecutedSnapshotConsumer {
    private static final String CONSUMER_NAME = "ranking-snapshot";

    private final ConsumedEventJdbcRepository consumedEventJdbcRepository;
    private final UserProfitSnapshotDailyService snapshotService;

    public OrderExecutedSnapshotConsumer(
            ConsumedEventJdbcRepository consumedEventJdbcRepository,
            UserProfitSnapshotDailyService snapshotService
    ) {
        this.consumedEventJdbcRepository = consumedEventJdbcRepository;
        this.snapshotService = snapshotService;
    }

    @Transactional
    @KafkaListener(
            topics = "finvibe.order.executed",
            groupId = "finvibe-ranking-snapshot"
    )
    public void onMessage(String payload, Acknowledgment acknowledgment) {
        var envelope = Json.parseObject(payload);
        long outboxEventId = ((Number) envelope.get("outboxEventId")).longValue();

        if (!consumedEventJdbcRepository.tryMarkConsumed(CONSUMER_NAME, outboxEventId)) {
            acknowledgment.acknowledge();
            return;
        }

        @SuppressWarnings("unchecked")
        var event = (java.util.Map<String, Object>) envelope.get("payload");
        String userId = String.valueOf(event.get("userId"));

        snapshotService.refreshSnapshot(userId);
        acknowledgment.acknowledge();
    }
}