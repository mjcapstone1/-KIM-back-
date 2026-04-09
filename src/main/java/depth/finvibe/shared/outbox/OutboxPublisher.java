package depth.finvibe.shared.outbox;

import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.util.TimeUtil;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxPublisher {
    private final OutboxJdbcRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int maxRetryCount;
    private final String workerId;

    public OutboxPublisher(
            OutboxJdbcRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${finvibe.outbox.batch-size:100}") int batchSize,
            @Value("${finvibe.outbox.max-retry-count:20}") int maxRetryCount
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.maxRetryCount = maxRetryCount;
        this.workerId = resolveWorkerId();
    }

    @Scheduled(fixedDelayString = "${finvibe.outbox.poll-delay-ms:500}")
    public void publishLoop() {
        while (true) {
            List<OutboxEventRow> batch = claimBatch();
            if (batch.isEmpty()) {
                return;
            }

            for (OutboxEventRow row : batch) {
                publishOne(row);
            }
        }
    }

    private List<OutboxEventRow> claimBatch() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(TimeUtil.SEOUL);
            List<OutboxEventRow> rows = outboxRepository.selectReadyBatchForUpdate(batchSize, now);
            if (rows.isEmpty()) {
                return List.of();
            }

            outboxRepository.markProcessing(
                    rows.stream().map(OutboxEventRow::eventId).toList(),
                    workerId,
                    now
            );
            return rows;
        });
    }

    private void publishOne(OutboxEventRow row) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("outboxEventId", row.eventId());
            envelope.put("eventType", row.eventType());
            envelope.put("aggregateType", row.aggregateType());
            envelope.put("aggregateId", row.aggregateId());
            envelope.put("occurredAt", row.occurredAt().toString());
            envelope.put("payload", Json.parse(row.payloadJson()));

            String topic = row.eventType();
            String key = (row.eventKey() == null || row.eventKey().isBlank()) ? row.aggregateId() : row.eventKey();

            SendResult<String, String> result = kafkaTemplate.send(topic, key, Json.stringify(envelope))
                    .get(10, TimeUnit.SECONDS);

            RecordMetadata metadata = result.getRecordMetadata();

            outboxRepository.markPublished(
                    row.eventId(),
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset(),
                    LocalDateTime.now(TimeUtil.SEOUL)
            );
        } catch (Exception e) {
            int attempt = row.retryCount() + 1;
            LocalDateTime nextAttemptAt = LocalDateTime.now(TimeUtil.SEOUL).plus(backoff(attempt));

            outboxRepository.reschedule(
                    row.eventId(),
                    maxRetryCount,
                    nextAttemptAt,
                    rootMessage(e)
            );
        }
    }

    private Duration backoff(int attempt) {
        long seconds = Math.min(300, 1L << Math.min(attempt, 8));
        return Duration.ofSeconds(seconds);
    }

    private String resolveWorkerId() {
        String host = System.getenv("HOSTNAME");
        if (host != null && !host.isBlank()) {
            return host;
        }
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}