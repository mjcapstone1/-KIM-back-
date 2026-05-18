package depth.finvibe.shared.market;

import depth.finvibe.shared.persistence.market.ClosingPriceEntity;
import depth.finvibe.shared.persistence.market.ClosingPriceRepository;
import depth.finvibe.shared.persistence.market.PriceCandleEntity;
import depth.finvibe.shared.persistence.market.PriceCandleRepository;
import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import depth.finvibe.shared.redis.RedisJsonCacheService;
import depth.finvibe.shared.redis.RedisKeys;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockPriceStore {
    private static final Duration LAST_QUOTE_TTL = Duration.ofDays(2);

    private final StockRepository stockRepository;
    private final ClosingPriceRepository closingPriceRepository;
    private final PriceCandleRepository priceCandleRepository;
    private final RedisJsonCacheService cache;

    public StockPriceStore(
            StockRepository stockRepository,
            ClosingPriceRepository closingPriceRepository,
            PriceCandleRepository priceCandleRepository,
            RedisJsonCacheService cache
    ) {
        this.stockRepository = stockRepository;
        this.closingPriceRepository = closingPriceRepository;
        this.priceCandleRepository = priceCandleRepository;
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
            entity.setLastVolume(Maps.longVal(quote.get("volume"), 0L));
            entity.setLastTradeValueKrw(Maps.longVal(quote.get("tradeValue"), 0L));
            entity.setLastQuoteAt(TimeUtil.nowSeoul().toLocalDateTime());
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
            hydrateQuoteMetricsFromDatabase(stockId, row);
            row.put("dataSource", "saved");
            return row;
        }

        Map<String, Object> fromDb = loadFromDatabase(stockId);
        if (fromDb != null && Maps.doubleVal(fromDb, "price") > 0) {
            return fromDb;
        }

        return null;
    }

    @Transactional
    public void saveCandles(String stockId, String timeframe, List<Map<String, Object>> candles, String source) {
        if (stockId == null || stockId.isBlank() || candles == null || candles.isEmpty()) {
            return;
        }
        String storedTimeframe = storedTimeframe(timeframe);
        for (Map<String, Object> candle : candles) {
            LocalDateTime candleAt = parseCandleAt(candle.get("at"));
            double close = Maps.doubleVal(candle, "close");
            if (candleAt == null || close <= 0) {
                continue;
            }
            double open = Maps.doubleVal(candle, "open", close);
            double high = Maps.doubleVal(candle, "high", Math.max(open, close));
            double low = Maps.doubleVal(candle, "low", Math.min(open, close));
            long volume = Maps.longVal(candle.get("volume"), 0L);
            long value = Maps.longVal(candle.get("value"), Math.round(close * volume));

            PriceCandleEntity entity = priceCandleRepository
                    .findByStockIdAndTimeframeAndCandleAt(stockId, storedTimeframe, candleAt)
                    .orElseGet(PriceCandleEntity::new);
            entity.setStockId(stockId);
            entity.setTimeframe(storedTimeframe);
            entity.setCandleAt(candleAt);
            entity.setOpenPrice(BigDecimal.valueOf(open));
            entity.setHighPrice(BigDecimal.valueOf(high));
            entity.setLowPrice(BigDecimal.valueOf(low));
            entity.setClosePrice(BigDecimal.valueOf(close));
            entity.setVolume(volume);
            entity.setTradingValueKrw(value);
            entity.setSource(source == null || source.isBlank() ? "kis" : source);
            priceCandleRepository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadPriceCandles(String stockId, String timeframe, int points) {
        if (stockId == null || stockId.isBlank()) {
            return List.of();
        }
        String storedTimeframe = storedTimeframe(timeframe);
        List<PriceCandleEntity> candles = priceCandleRepository.findByStockIdAndTimeframeOrderByCandleAtAsc(
                stockId,
                storedTimeframe
        );
        if (candles.isEmpty()) {
            return List.of();
        }

        int resolvedPoints = Math.max(1, points);
        int fromIndex = Math.max(0, candles.size() - resolvedPoints);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PriceCandleEntity candle : candles.subList(fromIndex, candles.size())) {
            rows.add(toCandleRow(candle));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public boolean needsCandleBackfill(String stockId, String timeframe, int minimumPoints) {
        if (stockId == null || stockId.isBlank()) {
            return false;
        }
        String storedTimeframe = storedTimeframe(timeframe);
        return priceCandleRepository.countByStockIdAndTimeframe(stockId, storedTimeframe) < Math.max(1, minimumPoints);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> loadClosingCandles(String stockId, int points) {
        if (stockId == null || stockId.isBlank()) {
            return List.of();
        }

        List<ClosingPriceEntity> closings = closingPriceRepository.findByStockIdOrderByTradeDateAsc(stockId);
        if (closings.isEmpty()) {
            return List.of();
        }

        int resolvedPoints = Math.max(1, points);
        int fromIndex = Math.max(0, closings.size() - resolvedPoints);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ClosingPriceEntity closing : closings.subList(fromIndex, closings.size())) {
            double close = closing.getClosePrice() == null ? 0.0 : closing.getClosePrice().doubleValue();
            if (close <= 0) {
                continue;
            }
            double prevClose = closing.getPrevClosePrice() == null ? close : closing.getPrevClosePrice().doubleValue();
            long volume = closing.getVolume();
            long value = closing.getTradingValueKrw() > 0 ? closing.getTradingValueKrw() : Math.round(close * volume);

            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("stockId", stockId);
            candle.put("timeframe", "DAY");
            candle.put("at", closing.getTradeDate().atStartOfDay(TimeUtil.SEOUL).toString());
            candle.put("open", prevClose);
            candle.put("high", Math.max(prevClose, close));
            candle.put("low", Math.min(prevClose, close));
            candle.put("close", close);
            candle.put("volume", volume);
            candle.put("value", value);
            candle.put("prevDayChangePct", closing.getChangeRate() == null ? 0.0 : closing.getChangeRate().doubleValue());
            candle.put("dataSource", "closing_prices");
            rows.add(candle);
        }
        return rows;
    }

    private Map<String, Object> toCandleRow(PriceCandleEntity candle) {
        Map<String, Object> row = new LinkedHashMap<>();
        double close = candle.getClosePrice().doubleValue();
        row.put("stockId", candle.getStockId());
        row.put("timeframe", candle.getTimeframe());
        row.put("at", candle.getCandleAt().atZone(TimeUtil.SEOUL).toString());
        row.put("open", candle.getOpenPrice().doubleValue());
        row.put("high", candle.getHighPrice().doubleValue());
        row.put("low", candle.getLowPrice().doubleValue());
        row.put("close", close);
        row.put("volume", candle.getVolume());
        row.put("value", candle.getTradingValueKrw());
        row.put("prevDayChangePct", 0.0);
        row.put("dataSource", candle.getSource());
        return row;
    }

    private LocalDateTime parseCandleAt(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(text).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String storedTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return "DAY";
        }
        return switch (timeframe.trim().toLowerCase()) {
            case "day", "daily" -> "DAY";
            case "week", "weekly" -> "WEEK";
            case "month", "monthly" -> "MONTH";
            case "year", "yearly" -> "YEAR";
            default -> timeframe.trim().toUpperCase();
        };
    }

    private void hydrateQuoteMetricsFromDatabase(String stockId, Map<String, Object> quote) {
        boolean hasVolume = Maps.longVal(quote.get("volume"), 0L) > 0;
        boolean hasTradeValue = Maps.longVal(quote.get("tradeValue"), 0L) > 0;
        if (hasVolume && hasTradeValue) {
            return;
        }
        stockRepository.findById(stockId).ifPresent(entity -> {
            if (!hasVolume) {
                quote.put("volume", entity.getLastVolume());
            }
            if (!hasTradeValue) {
                quote.put("tradeValue", entity.getLastTradeValueKrw());
            }
            quote.putIfAbsent("fetchedAt", entity.getLastQuoteAt() == null ? TimeUtil.nowSeoulIso() : entity.getLastQuoteAt().atZone(TimeUtil.SEOUL).toString());
        });
    }

    private Map<String, Object> loadFromDatabase(String stockId) {
        Optional<StockEntity> stockOptional = stockRepository.findById(stockId);
        if (stockOptional.isEmpty()) {
            return null;
        }

        StockEntity entity = stockOptional.get();
        double lastPrice = entity.getLastPrice() == null ? 0.0 : entity.getLastPrice().doubleValue();
        double lastChangeRate = entity.getLastChangeRate() == null ? 0.0 : entity.getLastChangeRate().doubleValue();
        long lastVolume = entity.getLastVolume();
        long lastTradeValue = entity.getLastTradeValueKrw();
        Optional<ClosingPriceEntity> closingOptional = closingPriceRepository.findTopByStockIdOrderByTradeDateDesc(stockId);

        double prevClose = closingOptional
                .map(ClosingPriceEntity::getClosePrice)
                .map(BigDecimal::doubleValue)
                .orElse(lastPrice);

        long volume = lastVolume > 0 ? lastVolume : closingOptional.map(ClosingPriceEntity::getVolume).orElse(0L);
        long tradeValue = lastTradeValue > 0
                ? lastTradeValue
                : closingOptional.map(ClosingPriceEntity::getTradingValueKrw).orElse(0L);

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
        row.put("tradeValue", tradeValue > 0 ? tradeValue : Math.round(lastPrice * volume));
        row.put("fetchedAt", entity.getLastQuoteAt() == null ? TimeUtil.nowSeoulIso() : entity.getLastQuoteAt().atZone(TimeUtil.SEOUL).toString());
        row.put("dataSource", "saved");
        return row;
    }
}
