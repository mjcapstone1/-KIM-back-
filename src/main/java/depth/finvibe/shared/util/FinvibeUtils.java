package depth.finvibe.shared.util;

import depth.finvibe.shared.json.Json;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;

public final class FinvibeUtils {
    private static final Map<String, Integer> TIMEFRAME_DEFAULT_POINTS = Map.ofEntries(
            Map.entry("1min", 60),
            Map.entry("3min", 60),
            Map.entry("5min", 60),
            Map.entry("10min", 48),
            Map.entry("15min", 48),
            Map.entry("30min", 48),
            Map.entry("60min", 48),
            Map.entry("day", 30),
            Map.entry("week", 12),
            Map.entry("month", 24),
            Map.entry("year", 12)
    );

    private FinvibeUtils() {
    }

    public static Object loadJsonResource(String fileName) {
        return Json.parse(Maps.resourceAsString("seed/" + fileName));
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase().replaceAll("\\s+", "");
    }

    public static Random seededRandom(String key, int bucketSeconds) {
        long bucket = System.currentTimeMillis() / 1000L / bucketSeconds;
        CRC32 crc32 = new CRC32();
        crc32.update((key + ":" + bucket).getBytes());
        return new Random(crc32.getValue());
    }

    public static double clamp(double value, double minValue, double maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    public static double roundStockPrice(double price, String stockType) {
        if ("domestic".equals(stockType)) {
            return Math.rint(price);
        }
        return Math.round(price * 100.0) / 100.0;
    }

    public static String formatNumberKr(double value, int digits) {
        if (digits <= 0) {
            return String.format("%,d", Math.round(value));
        }
        return String.format("%,.2f", value);
    }

    public static String formatHomePrice(double priceKrw) {
        return formatNumberKr(priceKrw, 0) + "원";
    }

    public static String formatChangePercent(double changeRate) {
        return (changeRate >= 0 ? "+" : "") + String.format("%.2f%%", changeRate);
    }

    public static int stockPriceInKrw(Map<String, Object> stock, int exchangeRate) {
        if ("foreign".equals(Maps.str(stock, "type"))) {
            return (int) Math.round(Maps.doubleVal(stock, "price") * exchangeRate);
        }
        return (int) Math.round(Maps.doubleVal(stock, "price"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> applyLiveStock(Map<String, Object> stock, int exchangeRate) {
        Map<String, Object> snapshot = (Map<String, Object>) Json.deepCopy(stock);
        Random priceRng = seededRandom("stock-price:" + Maps.str(snapshot, "code"), 5);
        Random rateRng = seededRandom("stock-rate:" + Maps.str(snapshot, "code"), 7);

        double priceShift = -0.008 + priceRng.nextDouble() * 0.016;
        double basePrice = Maps.doubleVal(snapshot, "price");
        double livePrice = Math.max(basePrice * (1 + priceShift), basePrice * 0.5);
        snapshot.put("price", roundStockPrice(livePrice, Maps.str(snapshot, "type")));

        double changeRate = Maps.doubleVal(snapshot, "changeRate") + (-0.35 + rateRng.nextDouble() * 0.70);
        snapshot.put("changeRate", Math.round(clamp(changeRate, -29.99, 29.99) * 100.0) / 100.0);
        snapshot.put("currency", snapshot.getOrDefault("currency", "domestic".equals(Maps.str(snapshot, "type")) ? "KRW" : "USD"));
        snapshot.put("priceKrw", stockPriceInKrw(snapshot, exchangeRate));
        if ("domestic".equals(Maps.str(snapshot, "type"))) {
            snapshot.put("displayPrice", "₩" + formatNumberKr(Maps.doubleVal(snapshot, "price"), 0));
        } else {
            snapshot.put("displayPrice", "$" + formatNumberKr(Maps.doubleVal(snapshot, "price"), 2));
        }
        snapshot.put("displayChangeRate", formatChangePercent(Maps.doubleVal(snapshot, "changeRate")));
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> applyLiveIndex(Map<String, Object> indexData) {
        Map<String, Object> snapshot = (Map<String, Object>) Json.deepCopy(indexData);
        Random rng = seededRandom("index:" + Maps.str(snapshot, "name"), 5);
        double liveValue = Maps.doubleVal(snapshot, "value") * (1 + (-0.003 + rng.nextDouble() * 0.006));
        liveValue = Math.round(liveValue * 100.0) / 100.0;
        double baseValue = Maps.doubleVal(snapshot, "baseValue");
        double change = Math.round((liveValue - baseValue) * 100.0) / 100.0;
        double percent = Math.round((change / baseValue) * 10000.0) / 100.0;
        snapshot.put("value", liveValue);
        snapshot.put("change", change);
        snapshot.put("percent", percent);
        snapshot.put("isUp", change >= 0);
        return snapshot;
    }

    public static int getDefaultPoints(String timeframe) {
        return TIMEFRAME_DEFAULT_POINTS.getOrDefault(timeframe, 30);
    }

    public static List<Map<String, Object>> generateStockCandles(String code, String stockType, double basePrice, String timeframe, Integer points) {
        List<Map<String, Object>> candles = new ArrayList<>();
        int count = points == null ? getDefaultPoints(timeframe) : points;
        CRC32 crc32 = new CRC32();
        crc32.update(("candles:" + code + ":" + timeframe + ":" + count).getBytes());
        Random rng = new Random(crc32.getValue());
        double current = basePrice * (1 - (0.03 + rng.nextDouble() * 0.05));
        ZonedDateTime now = TimeUtil.nowSeoul();

        for (int index = 0; index < count; index++) {
            double openPrice = current;
            double closePrice = openPrice * (1 + (-0.015 + rng.nextDouble() * 0.033));
            double highPrice = Math.max(openPrice, closePrice) * (1 + rng.nextDouble() * 0.012);
            double lowPrice = Math.min(openPrice, closePrice) * (1 - rng.nextDouble() * 0.012);

            openPrice = roundStockPrice(openPrice, stockType);
            closePrice = roundStockPrice(closePrice, stockType);
            highPrice = roundStockPrice(highPrice, stockType);
            lowPrice = roundStockPrice(lowPrice, stockType);

            String label;
            if (timeframe.endsWith("min")) {
                int minuteStep = Integer.parseInt(timeframe.replaceAll("\\D", "").isBlank() ? "1" : timeframe.replaceAll("\\D", ""));
                ZonedDateTime ts = now.minusMinutes((long) (count - index) * minuteStep);
                label = ts.format(DateTimeFormatter.ofPattern("HH:mm"));
            } else if ("day".equals(timeframe)) {
                ZonedDateTime ts = now.minusDays(count - index);
                label = ts.format(DateTimeFormatter.ofPattern("MM/dd"));
            } else if ("week".equals(timeframe)) {
                ZonedDateTime ts = now.minusWeeks(count - index);
                label = "W" + ts.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            } else if ("month".equals(timeframe)) {
                ZonedDateTime ts = now.minusDays((long) (count - index) * 30);
                label = ts.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            } else {
                ZonedDateTime ts = now.minusDays((long) (count - index) * 365);
                label = ts.format(DateTimeFormatter.ofPattern("yyyy"));
            }

            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("time", label);
            candle.put("open", openPrice);
            candle.put("high", highPrice);
            candle.put("low", lowPrice);
            candle.put("close", closePrice);
            candle.put("color", closePrice >= openPrice ? "#ef4444" : "#3b82f6");
            candles.add(candle);
            current = closePrice;
        }

        return candles;
    }

    public static List<Map<String, Object>> generateThemeChart(String themeId, double basePrice, int days) {
        CRC32 crc32 = new CRC32();
        crc32.update(("theme:" + themeId + ":" + days).getBytes());
        Random rng = new Random(crc32.getValue());
        double current = basePrice * (1 - (0.02 + rng.nextDouble() * 0.04));
        LocalDate today = LocalDate.now(TimeUtil.SEOUL);
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            current *= 1 + (-0.018 + rng.nextDouble() * 0.044);
            LocalDate pointDate = today.minusDays(days - i - 1L);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", pointDate.format(DateTimeFormatter.ofPattern("MM/dd")));
            point.put("price", Math.round(current));
            points.add(point);
        }
        return points;
    }

    public static List<Map<String, Object>> generateIndexChart(String indexName, double baseValue, int points) {
        CRC32 crc32 = new CRC32();
        crc32.update(("index-chart:" + indexName + ":" + points).getBytes());
        Random rng = new Random(crc32.getValue());
        double current = baseValue * (1 - (0.01 + rng.nextDouble() * 0.02));
        LocalDate today = LocalDate.now(TimeUtil.SEOUL);
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            current *= 1 + (-0.01 + rng.nextDouble() * 0.022);
            LocalDate pointDate = today.minusDays(points - i - 1L);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", pointDate.format(DateTimeFormatter.ofPattern("MM/dd")));
            point.put("value", Math.round(current * 100.0) / 100.0);
            data.add(point);
        }
        return data;
    }

    public static Map<String, Object> generateOrderBook(String code, String stockType, double basePrice) {
        Random rng = seededRandom("orderbook:" + code, 3);
        double increment = "domestic".equals(stockType) ? 100 : 0.1;
        List<Map<String, Object>> asks = new ArrayList<>();
        List<Map<String, Object>> bids = new ArrayList<>();

        for (int i = 5; i >= 1; i--) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", roundStockPrice(basePrice + increment * i, stockType));
            row.put("volume", 1000 + rng.nextInt(5001));
            asks.add(row);
        }
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price", roundStockPrice(Math.max(basePrice - increment * i, increment), stockType));
            row.put("volume", 1000 + rng.nextInt(5001));
            bids.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asks", asks);
        result.put("bids", bids);
        return result;
    }

    public static String levelToKorean(String level) {
        return switch (level) {
            case "beginner" -> "초급";
            case "intermediate" -> "중급";
            case "advanced" -> "고급";
            default -> level;
        };
    }

    public static String inferCourseLevel(List<String> keywords) {
        List<String> advancedKeywords = List.of("리스크", "헤징", "포트폴리오", "가치투자", "MACD", "RSI", "재무제표");
        boolean hasAdvanced = false;
        for (String keyword : keywords) {
            if (advancedKeywords.contains(keyword)) {
                hasAdvanced = true;
                break;
            }
        }
        if (keywords.size() >= 4 || hasAdvanced) {
            return keywords.size() >= 5 ? "advanced" : "intermediate";
        }
        return "beginner";
    }
}
