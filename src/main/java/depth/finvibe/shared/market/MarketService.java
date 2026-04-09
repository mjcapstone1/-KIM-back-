package depth.finvibe.shared.market;

import depth.finvibe.shared.config.AppConfig;
import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.util.FinvibeUtils;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarketService {
    private final AppConfig config;
    private final KisClient kisClient;

    public MarketService(AppConfig config) {
        this.config = config;
        this.kisClient = new KisClient(config);
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

    public Map<String, Object> getStockSnapshot(Map<String, Object> stock, int exchangeRate) {
        if (shouldUseKis(stock)) {
            try {
                Map<String, Object> quote = kisClient.fetchDomesticQuote(Maps.str(stock, "code"));
                if (quote != null) {
                    return quoteToSnapshot(stock, quote, exchangeRate);
                }
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> snapshot = FinvibeUtils.applyLiveStock(stock, exchangeRate);
        snapshot.put("dataSource", "mock");
        snapshot.put("fetchedAt", TimeUtil.nowSeoulIso());
        return snapshot;
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
            } catch (Exception ignored) {
            }
        }
        return FinvibeUtils.generateStockCandles(
                Maps.str(stock, "code"),
                Maps.str(stock, "type"),
                Maps.doubleVal(stock, "price"),
                timeframe,
                resolvedPoints
        );
    }

    public Map<String, Object> getOrderBook(Map<String, Object> stock, int exchangeRate) {
        Map<String, Object> snapshot = getStockSnapshot(stock, exchangeRate);
        if ("kis".equals(snapshot.get("dataSource")) && "domestic".equals(Maps.str(stock, "type"))) {
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
            result.put("dataSource", "kis");
            result.put("fetchedAt", snapshot.get("fetchedAt"));
            return result;
        }
        Map<String, Object> book = FinvibeUtils.generateOrderBook(Maps.str(stock, "code"), Maps.str(stock, "type"), Maps.doubleVal(snapshot, "price"));
        book.put("dataSource", "mock");
        book.put("fetchedAt", TimeUtil.nowSeoulIso());
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
        result.put("isOpen", Boolean.TRUE);
        result.put("session", "REGULAR");
        result.put("message", "모의투자용 시장이 열려 있습니다.");
        result.put("checkedAt", TimeUtil.nowSeoulIso());
        result.put("serverTime", TimeUtil.nowSeoulIso());
        result.put("dataSource", "mock");
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
