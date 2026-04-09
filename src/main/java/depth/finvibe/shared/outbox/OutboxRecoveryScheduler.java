package depth.finvibe.shared.outbox;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboxRecoveryScheduler {
    private final OutboxJdbcRepository outboxRepository;
    private final long lockTimeoutSeconds;

    public OutboxRecoveryScheduler(
            OutboxJdbcRepository outboxRepository,
            @Value("${finvibe.outbox.lock-timeout-seconds:60}") long lockTimeoutSeconds
    ) {
        this.outboxRepository = outboxRepository;
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${finvibe.outbox.recovery-delay-ms:30000}")
    public void recoverStaleRows() {
        outboxRepository.recoverTimedOutProcessing(Duration.ofSeconds(lockTimeoutSeconds));
    }
}
