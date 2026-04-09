package depth.finvibe.shared.outbox;

import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.redis.RedisKeys;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class WalletChangedConsumer {
    private final StringRedisTemplate redisTemplate;

    public WalletChangedConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(
            topics = "finvibe.wallet.changed",
            groupId = "finvibe-wallet-cache"
    )
    public void onMessage(String payload, Acknowledgment acknowledgment) {
        Map<String, Object> envelope = Json.parseObject(payload);

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) envelope.get("payload");

        String userId = String.valueOf(event.get("userId"));

        redisTemplate.delete(List.of(
                RedisKeys.homeSummary(userId),
                RedisKeys.interestStocks(userId)
        ));

        acknowledgment.acknowledge();
    }
}