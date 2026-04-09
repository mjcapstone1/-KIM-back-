package depth.finvibe.shared.redis;

public final class RedisKeys {
    public static final String SEARCH_POPULAR = "search:popular";

    private RedisKeys() {
    }

    public static String stockPrice(String code) {
        return "stock:price:" + safe(code);
    }

    public static String stockDetail(String stockId) {
        return "stock:detail:" + safe(stockId);
    }

    public static String stockList(String market, String query, int limit) {
        return "stock:list:" + safe(market) + ":" + safe(query) + ":" + limit;
    }

    public static String homeScreen() {
        return "home:screen";
    }

    public static String homeIndices() {
        return "home:indices";
    }

    public static String homeRankings(String metric, String market, int limit) {
        return "ranking:home:" + safe(metric) + ":" + safe(market) + ":" + limit;
    }

    public static String interestStocks(String userId) {
        return "user:" + safe(userId) + ":interest-stocks";
    }

    public static String homeSummary(String userId) {
        return "user:" + safe(userId) + ":home-summary";
    }

    public static String accessBlacklist(String jti) {
        return "auth:blacklist:access:" + safe(jti);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9가-힣:_-]", "_");
    }
}