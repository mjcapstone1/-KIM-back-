package depth.finvibe.shared.market;

import depth.finvibe.shared.persistence.market.ClosingPriceEntity;
import depth.finvibe.shared.persistence.market.ClosingPriceRepository;
import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import depth.finvibe.shared.redis.RedisJsonCacheService;
import depth.finvibe.shared.redis.RedisKeys;
import depth.finvibe.shared.util.Maps;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockPriceStore {
    private static final Duration LAST_QUOTE_TTL = Duration.ofDays(2);

    private final StockRepository stockRepository;
    private final ClosingPriceRepository closingPriceRepository;
    private final RedisJsonCacheService cache;

    public StockPriceStore(
            StockRepository stockRepository,
            ClosingPriceRepository closingPriceRepository,
            RedisJsonCacheService cache
    ) {
        this.stockRepository = stockRepository;
        this.closingPriceRepository = closingPriceRepository;
        this.cache = cache;
    }

    @Transactional
    public void saveLiveQuote(String stockId, Map<String, Object> quote) {
        if (stockId == null || stockId.isBlank() || quote == null) {
            return;
        }
        double price = Maps.doubleVal(quote, "price");
        if (price <= 0) {
            return;
        }

        stockRepository.findById(stockId).ifPresent(entity -> {
            entity.setLastPrice(BigDecimal.valueOf(price));
            entity.setLastChangeRate(BigDecimal.valueOf(Maps.doubleVal(quote, "changeRate")));
            stockRepository.save(entity);
        });

        Map<String, Object> snapshot = new LinkedHashMap<>(quote);
        snapshot.putIfAbsent("dataSource", "saved");
        cache.put(RedisKeys.stockLastQuote(stockId), snapshot, LAST_QUOTE_TTL);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadLastSavedQuote(String stockId) {
        if (stockId == null || stockId.isBlank()) {
            return null;
        }

        Map<String, Object> fromCache = cache.getOrLoadMap(
                RedisKeys.stockLastQuote(stockId),
                LAST_QUOTE_TTL,
                () -> {
                    Map<String, Object> loaded = loadFromDatabase(stockId);
                    return loaded == null ? new LinkedHashMap<>() : loaded;
                }
        );
        if (fromCache != null && Maps.doubleVal(fromCache, "price") > 0) {
            Map<String, Object> row = new LinkedHashMap<>(fromCache);
            row.put("dataSource", "saved");
            return row;
        }

        Map<String, Object> fromDb = loadFromDatabase(stockId);
        if (fromDb != null && Maps.doubleVal(fromDb, "price") > 0) {
            return fromDb;
        }

        return null;
    }

    private Map<String, Object> loadFromDatabase(String stockId) {
        Optional<StockEntity> stockOptional = stockRepository.findById(stockId);
        if (stockOptional.isEmpty()) {
            return null;
        }

        StockEntity entity = stockOptional.get();
        double lastPrice = entity.getLastPrice() == null ? 0.0 : entity.getLastPrice().doubleValue();
        double lastChangeRate = entity.getLastChangeRate() == null ? 0.0 : entity.getLastChangeRate().doubleValue();
        Optional<ClosingPriceEntity> closingOptional = closingPriceRepository.findTopByStockIdOrderByTradeDateDesc(stockId);

        double prevClose = closingOptional
                .map(ClosingPriceEntity::getClosePrice)
                .map(BigDecimal::doubleValue)
                .orElse(lastPrice);

        long volume = closingOptional.map(ClosingPriceEntity::getVolume).orElse(0L);

        if (lastPrice <= 0 && closingOptional.isPresent()) {
            lastPrice = closingOptional.get().getClosePrice().doubleValue();
            lastChangeRate = closingOptional.get().getChangeRate() == null ? 0.0 : closingOptional.get().getChangeRate().doubleValue();
            prevClose = closingOptional.get().getPrevClosePrice() == null
                    ? lastPrice
                    : closingOptional.get().getPrevClosePrice().doubleValue();
        }

        if (lastPrice <= 0) {
            return null;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("price", lastPrice);
        row.put("changeRate", lastChangeRate);
        row.put("previousClose", prevClose);
        row.put("volume", volume);
        row.put("dataSource", "saved");
        return row;
    }
}
