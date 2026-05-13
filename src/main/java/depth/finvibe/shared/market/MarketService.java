package depth.finvibe.shared.market;

import depth.finvibe.shared.config.AppConfig;
import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.util.FinvibeUtils;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarketService {
    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    private final AppConfig config;
    private final KisClient kisClient;
    private final StockPriceStore stockPriceStore;

    public MarketService(AppConfig config, StockPriceStore stockPriceStore) {
        this.config = config;
        this.kisClient = new KisClient(config);
        this.stockPriceStore = stockPriceStore;
    }

    public boolean kisEnabled() {
        return kisClient.isEnabled();
    }

    private boolean shouldUseKis(Map<String, Object> stock) {
        return kisClient.isEnabled() && "domestic".equals(Maps.str(stock, "type"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> quoteToSnapshot(Map<String, Object> stock, Map<String, Object> quote, int exchangeRate) {
        Map<String, Object> snapshot = (Map<String, Object>) Json.deepCopy(stock);
        snapshot.put("price", FinvibeUtils.roundStockPrice(Maps.doubleVal(quote, "price", Maps.doubleVal(stock, "price")), Maps.str(stock, "type")));
        snapshot.put("changeRate", Math.round(Maps.doubleVal(quote, "changeRate", Maps.doubleVal(stock, "changeRate")) * 100.0) / 100.0);
        snapshot.put("volume", Maps.intVal(quote, "volume", Maps.intVal(stock, "volume")));
        snapshot.put("currency", "KRW");
        snapshot.put("priceKrw", FinvibeUtils.stockPriceInKrw(snapshot, exchangeRate));
        snapshot.put("displayPrice", "₩" + FinvibeUtils.formatNumberKr(Maps.doubleVal(snapshot, "price"), 0));
        snapshot.put("displayChangeRate", FinvibeUtils.formatChangePercent(Maps.doubleVal(snapshot, "changeRate")));
        snapshot.put("open", Maps.intVal(quote, "open"));
        snapshot.put("high", Maps.intVal(quote, "high"));
        snapshot.put("low", Maps.intVal(quote, "low"));
        snapshot.put("askPrice", Maps.intVal(quote, "askPrice"));
        snapshot.put("bidPrice", Maps.intVal(quote, "bidPrice"));
        snapshot.put("tradeValue", Maps.intVal(quote, "tradeValue"));
        snapshot.put("totalAskVolume", Maps.intVal(quote, "totalAskVolume"));
        snapshot.put("totalBidVolume", Maps.intVal(quote, "totalBidVolume"));
        snapshot.put("dataSource", quote.getOrDefault("dataSource", "kis"));
        snapshot.put("fetchedAt", quote.getOrDefault("fetchedAt", TimeUtil.nowSeoulIso()));
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadataOnlySnapshot(Map<String, Object> stock, int exchangeRate) {
        Map<String, Object> snapshot = (Map<String, Object>) Json.deepCopy(stock);
        snapshot.put("price", 0);
        snapshot.put("changeRate", 0.0);
        snapshot.put("volume", 0);
        snapshot.put("currency", Maps.str(stock, "currency", "KRW"));
        snapshot.put("priceKrw", 0);
        snapshot.put("displayPrice", "데이터 없음");
        snapshot.put("displayChangeRate", "0.00%");
        snapshot.put("open", 0);
        snapshot.put("high", 0);
        snapshot.put("low", 0);
        snapshot.put("askPrice", 0);
        snapshot.put("bidPrice", 0);
        snapshot.put("tradeValue", 0);
        snapshot.put("totalAskVolume", 0);
        snapshot.put("totalBidVolume", 0);
        snapshot.put("dataSource", "unavailable");
        snapshot.put("fetchedAt", TimeUtil.nowSeoulIso());
        snapshot.put("message", "KIS 실데이터 또는 DB 저장 가격이 없습니다.");
        return snapshot;
    }

    public Map<String, Object> getStockSnapshot(Map<String, Object> stock, int exchangeRate) {
        String stockId = Maps.str(stock, "id");

        if (shouldUseKis(stock)) {
            try {
                Map<String, Object> quote = kisClient.fetchDomesticQuote(Maps.str(stock, "code"));
                if (quote != null && Maps.doubleVal(quote, "price") > 0) {
                    stockPriceStore.saveLiveQuote(stockId, quote);
                    return quoteToSnapshot(stock, quote, exchangeRate);
                }
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> saved = stockPriceStore.loadLastSavedQuote(stockId);
        if (saved != null && Maps.doubleVal(saved, "price") > 0) {
            saved.put("dataSource", "saved");
            saved.putIfAbsent("fetchedAt", TimeUtil.nowSeoulIso());
            return quoteToSnapshot(stock, saved, exchangeRate);
        }

        return metadataOnlySnapshot(stock, exchangeRate);
    }

    public List<Map<String, Object>> listStockSnapshots(List<Map<String, Object>> stocks, int exchangeRate) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<Map<String, Object>>> futures = new ArrayList<>();
            for (Map<String, Object> stock : stocks) {
                futures.add(executor.submit(() -> getStockSnapshot(stock, exchangeRate)));
            }
            for (java.util.concurrent.Future<Map<String, Object>> future : futures) {
                try {
                    rows.add(future.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return rows;
    }

    public List<Map<String, Object>> getCandles(Map<String, Object> stock, String timeframe, Integer points) {
        int resolvedPoints = points == null ? FinvibeUtils.getDefaultPoints(timeframe) : points;
        if (shouldUseKis(stock)) {
            try {
                if (List.of("day", "week", "month", "year").contains(timeframe)) {
                    List<Map<String, Object>> candles = kisClient.fetchDomesticDailyChart(Maps.str(stock, "code"), timeframe, resolvedPoints);
                    if (!candles.isEmpty()) {
                        return candles;
                    }
                } else if (timeframe.endsWith("min")) {
                    List<Map<String, Object>> candles = kisClient.fetchDomesticMinuteChart(Maps.str(stock, "code"), timeframe, resolvedPoints);
                    if (!candles.isEmpty()) {
                        return candles;
                    }
                }
                log.warn("KIS candle response was empty. stockId={}, code={}, timeframe={}, points={}",
                        Maps.str(stock, "id"), Maps.str(stock, "code"), timeframe, resolvedPoints);
            } catch (Exception e) {
                log.warn("KIS candle fetch failed. stockId={}, code={}, timeframe={}, points={}",
                        Maps.str(stock, "id"), Maps.str(stock, "code"), timeframe, resolvedPoints, e);
            }
        }
        return List.of();
    }

    public List<Map<String, Object>> getIndexCandles(String indexCode, String timeframe, int points) {
        if (!kisClient.isEnabled()) {
            return List.of();
        }
        try {
            return kisClient.fetchDomesticIndexDailyChart(indexCode, timeframe, points);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public Map<String, Object> getOrderBook(Map<String, Object> stock, int exchangeRate) {
        Map<String, Object> snapshot = getStockSnapshot(stock, exchangeRate);
        String source = Maps.str(snapshot, "dataSource");
        if (("kis".equals(source) || "saved".equals(source)) && "domestic".equals(Maps.str(stock, "type"))) {
            int ask = Maps.intVal(snapshot, "askPrice", Maps.intVal(snapshot, "price"));
            int bid = Maps.intVal(snapshot, "bidPrice", Maps.intVal(snapshot, "price"));
            int step = domesticTickSize(Maps.doubleVal(snapshot, "price", Math.max(ask, bid)));
            int totalAskVolume = Math.max(Maps.intVal(snapshot, "totalAskVolume", 1), 1);
            int totalBidVolume = Math.max(Maps.intVal(snapshot, "totalBidVolume", 1), 1);
            List<Map<String, Object>> asks = new ArrayList<>();
            List<Map<String, Object>> bids = new ArrayList<>();
            for (int i = 5; i >= 1; i--) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("price", ask + step * (i - 1));
                row.put("volume", Math.max(1, Math.ceilDiv(totalAskVolume, i + 3)));
                asks.add(row);
            }
            for (int i = 1; i <= 5; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("price", Math.max(step, bid - step * (i - 1)));
                row.put("volume", Math.max(1, Math.ceilDiv(totalBidVolume, i + 3)));
                bids.add(row);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("asks", asks);
            result.put("bids", bids);
            result.put("bestAsk", ask);
            result.put("bestBid", bid);
            result.put("dataSource", source);
            result.put("fetchedAt", snapshot.get("fetchedAt"));
            return result;
        }
        Map<String, Object> book = new LinkedHashMap<>();
        book.put("asks", List.of());
        book.put("bids", List.of());
        book.put("bestAsk", 0);
        book.put("bestBid", 0);
        book.put("dataSource", "unavailable");
        book.put("fetchedAt", TimeUtil.nowSeoulIso());
        book.put("message", "실시간 호가 데이터가 없습니다.");
        return book;
    }

    public Map<String, Object> getMarketStatus() {
        if (kisClient.isEnabled()) {
            try {
                return kisClient.fetchMarketStatus(LocalDate.now(TimeUtil.SEOUL));
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isOpen", Boolean.FALSE);
        result.put("session", "CLOSED");
        result.put("message", "KIS 설정이 없어서 실제 장 상태를 확인할 수 없습니다.");
        result.put("checkedAt", TimeUtil.nowSeoulIso());
        result.put("serverTime", TimeUtil.nowSeoulIso());
        result.put("dataSource", "unavailable");
        return result;
    }

    private int domesticTickSize(double price) {
        price = Math.max(price, 1);
        if (price < 2_000) {
            return 1;
        }
        if (price < 5_000) {
            return 5;
        }
        if (price < 20_000) {
            return 10;
        }
        if (price < 50_000) {
            return 50;
        }
        if (price < 200_000) {
            return 100;
        }
        if (price < 500_000) {
            return 500;
        }
        return 1_000;
    }
}
