package depth.finvibe.shared.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisJsonCacheService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisJsonCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getOrLoadMap(String key, Duration ttl, Supplier<Map<String, Object>> loader) {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isBlank()) {
            return read(cached, MAP_TYPE);
        }

        Map<String, Object> loaded = loader.get();
        put(key, loaded, ttl);
        return loaded;
    }

    public List<Map<String, Object>> getOrLoadMapList(String key, Duration ttl, Supplier<List<Map<String, Object>>> loader) {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isBlank()) {
            return read(cached, MAP_LIST_TYPE);
        }

        List<Map<String, Object>> loaded = loader.get();
        put(key, loaded, ttl);
        return loaded;
    }

    public List<String> getOrLoadStringList(String key, Duration ttl, Supplier<List<String>> loader) {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isBlank()) {
            return read(cached, STRING_LIST_TYPE);
        }

        List<String> loaded = loader.get();
        put(key, loaded, ttl);
        return loaded;
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 캐시 저장 실패: " + key, e);
        }
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void deleteAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }

    public void incrementKeywordScore(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        redisTemplate.opsForZSet().incrementScore(RedisKeys.SEARCH_POPULAR, keyword.trim(), 1.0);
        redisTemplate.expire(RedisKeys.SEARCH_POPULAR, Duration.ofDays(7));
    }

    public List<String> topKeywords(int limit) {
        Set<String> rows = redisTemplate.opsForZSet().reverseRange(
                RedisKeys.SEARCH_POPULAR,
                0,
                Math.max(0, limit - 1)
        );
        return rows == null ? List.of() : new ArrayList<>(rows);
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 캐시 역직렬화 실패", e);
        }
    }
}