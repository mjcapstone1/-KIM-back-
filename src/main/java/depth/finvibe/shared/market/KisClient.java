package depth.finvibe.shared.market;

import depth.finvibe.shared.config.AppConfig;
import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KisClient {
    private static final long TOKEN_BUFFER_SECONDS = 120;
    private static final long QUOTE_CACHE_SECONDS = 3;
    private static final long CHART_CACHE_SECONDS = 30;
    private static final long HOLIDAY_CACHE_SECONDS = 60L * 60L * 12L;

    private final AppConfig config;
    private final HttpClient client;
    private final Object lock = new Object();

    private String accessToken;
    private long accessTokenExpiresAtEpochSeconds;
    private final Map<String, CacheEntry> quoteCache = new LinkedHashMap<>();
    private final Map<String, CacheEntry> chartCache = new LinkedHashMap<>();
    private final Map<String, CacheEntry> holidayCache = new LinkedHashMap<>();

    public KisClient(AppConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.kisTimeoutMs()))
                .build();
    }

    public boolean isEnabled() {
        return config.kisEnabled();
    }

    public Map<String, Object> fetchDomesticQuote(String stockCode) {
        String key = "quote:" + stockCode;
        Object cached = getCached(quoteCache, key);
        if (cached instanceof Map<?, ?> map) {
            return Maps.map(Json.deepCopy(map));
        }

        Map<String, Object> payload = request(
                "GET",
                "/uapi/domestic-stock/v1/quotations/inquire-price",
                "FHKST01010100",
                Map.of(
                        "FID_COND_MRKT_DIV_CODE", "J",
                        "FID_INPUT_ISCD", stockCode
                ),
                null
        );
        Map<String, Object> output = Maps.map(payload.getOrDefault("output", new LinkedHashMap<>()));
        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("code", stockCode);
        quote.put("price", Maps.intVal(output, "stck_prpr"));
        quote.put("changeValue", Maps.intVal(output, "prdy_vrss"));
        quote.put("changeRate", Maps.doubleVal(output, "prdy_ctrt"));
        quote.put("volume", Maps.intVal(output, "acml_vol"));
        quote.put("open", Maps.intVal(output, "stck_oprc"));
        quote.put("high", Maps.intVal(output, "stck_hgpr"));
        quote.put("low", Maps.intVal(output, "stck_lwpr"));
        quote.put("askPrice", Maps.intVal(output, "askp1"));
        quote.put("bidPrice", Maps.intVal(output, "bidp1"));
        quote.put("totalAskVolume", Maps.intVal(output, "total_askp_rsqn"));
        quote.put("totalBidVolume", Maps.intVal(output, "total_bidp_rsqn"));
        quote.put("tradeValue", Maps.intVal(output, "acml_tr_pbmn"));
        quote.put("previousClose", Maps.intVal(output, "stck_prdy_clpr"));
        quote.put("dataSource", "kis");
        quote.put("fetchedAt", TimeUtil.nowSeoulIso());

        setCached(quoteCache, key, quote, QUOTE_CACHE_SECONDS);
        return Maps.map(Json.deepCopy(quote));
    }

    public List<Map<String, Object>> fetchDomesticDailyChart(String stockCode, String timeframe, int points) {
        String periodCode = switch (timeframe) {
            case "day" -> "D";
            case "week" -> "W";
            case "month" -> "M";
            case "year" -> "Y";
            default -> throw ApiException.badRequest("UNSUPPORTED_TIMEFRAME", "지원하지 않는 timeframe입니다: " + timeframe);
        };
        LocalDate today = LocalDate.now(TimeUtil.SEOUL);
        int lookbackDays = switch (timeframe) {
            case "day" -> Math.max(points * 3, 90);
            case "week" -> Math.max(points * 14, 365);
            case "month" -> Math.max(points * 40, 365 * 3);
            case "year" -> Math.max(points * 400, 365 * 20);
            default -> 365;
        };
        String startDate = today.minusDays(lookbackDays).format(DateTimeFormatter.BASIC_ISO_DATE);
        String endDate = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "daily:" + stockCode + ":" + timeframe + ":" + points + ":" + startDate + ":" + endDate;
        Object cached = getCached(chartCache, key);
        if (cached instanceof List<?> list) {
            return toMapList(Json.deepCopy(list));
        }

        Map<String, Object> payload = request(
                "GET",
                "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice",
                "FHKST03010100",
                Map.of(
                        "FID_COND_MRKT_DIV_CODE", "J",
                        "FID_INPUT_ISCD", stockCode,
                        "FID_INPUT_DATE_1", startDate,
                        "FID_INPUT_DATE_2", endDate,
                        "FID_PERIOD_DIV_CODE", periodCode,
                        "FID_ORG_ADJ_PRC", "0"
                ),
                null
        );

        List<Map<String, Object>> rows = toMapList(payload.getOrDefault("output2", List.of()));
        List<Map<String, Object>> candles = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> row = rows.get(i);
            String dayValue = Maps.str(row, "stck_bsop_date");
            if (dayValue == null || dayValue.length() != 8) {
                continue;
            }
            LocalDate date = LocalDate.parse(dayValue, DateTimeFormatter.BASIC_ISO_DATE);
            String label = switch (timeframe) {
                case "day" -> date.format(DateTimeFormatter.ofPattern("MM/dd"));
                case "week" -> "W" + date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                case "month" -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                default -> date.format(DateTimeFormatter.ofPattern("yyyy"));
            };
            Map<String, Object> candle = new LinkedHashMap<>();
            int open = Maps.intVal(row, "stck_oprc");
            int high = Maps.intVal(row, "stck_hgpr");
            int low = Maps.intVal(row, "stck_lwpr");
            int close = Maps.intVal(row, "stck_clpr");
            candle.put("time", label);
            candle.put("open", open);
            candle.put("high", high);
            candle.put("low", low);
            candle.put("close", close);
            candle.put("volume", Maps.intVal(row, "acml_vol"));
            candle.put("color", close >= open ? "#ef4444" : "#3b82f6");
            candles.add(candle);
        }
        if (candles.size() > points) {
            candles = new ArrayList<>(candles.subList(candles.size() - points, candles.size()));
        }
        setCached(chartCache, key, candles, CHART_CACHE_SECONDS);
        return candles;
    }

    public List<Map<String, Object>> fetchDomesticMinuteChart(String stockCode, String timeframe, int points) {
        int stepMinutes = Integer.parseInt(timeframe.replaceAll("\\D", "").isBlank() ? "1" : timeframe.replaceAll("\\D", ""));
        ZonedDateTime now = TimeUtil.nowSeoul();
        String key = "minute:" + stockCode + ":" + timeframe + ":" + points + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        Object cached = getCached(chartCache, key);
        if (cached instanceof List<?> list) {
            return toMapList(Json.deepCopy(list));
        }

        Map<String, Object> payload = request(
                "GET",
                "/uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice",
                "FHKST03010230",
                Map.of(
                        "FID_COND_MRKT_DIV_CODE", "J",
                        "FID_INPUT_ISCD", stockCode,
                        "FID_INPUT_HOUR_1", now.format(DateTimeFormatter.ofPattern("HHmmss")),
                        "FID_INPUT_DATE_1", now.format(DateTimeFormatter.BASIC_ISO_DATE),
                        "FID_PW_DATA_INCU_YN", "Y",
                        "FID_FAKE_TICK_INCU_YN", ""
                ),
                null
        );

        List<Map<String, Object>> rows = toMapList(payload.getOrDefault("output2", List.of()));
        List<Map<String, Object>> raw = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> row = rows.get(i);
            String stamp = Maps.str(row, "stck_bsop_date", "") + Maps.str(row, "stck_cntg_hour", "");
            if (stamp.length() != 14) {
                continue;
            }
            ZonedDateTime dt = ZonedDateTime.of(
                    Integer.parseInt(stamp.substring(0, 4)),
                    Integer.parseInt(stamp.substring(4, 6)),
                    Integer.parseInt(stamp.substring(6, 8)),
                    Integer.parseInt(stamp.substring(8, 10)),
                    Integer.parseInt(stamp.substring(10, 12)),
                    Integer.parseInt(stamp.substring(12, 14)),
                    0,
                    TimeUtil.SEOUL
            );
            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("dt", dt);
            candle.put("open", Maps.intVal(row, "stck_oprc"));
            candle.put("high", Maps.intVal(row, "stck_hgpr"));
            candle.put("low", Maps.intVal(row, "stck_lwpr"));
            candle.put("close", Maps.intVal(row, "stck_prpr"));
            candle.put("volume", Maps.intVal(row, "cntg_vol"));
            raw.add(candle);
        }
        List<Map<String, Object>> aggregated = aggregateMinutes(raw, stepMinutes);
        if (aggregated.size() > points) {
            aggregated = new ArrayList<>(aggregated.subList(aggregated.size() - points, aggregated.size()));
        }
        setCached(chartCache, key, aggregated, CHART_CACHE_SECONDS);
        return aggregated;
    }

    public Map<String, Object> fetchMarketStatus(LocalDate targetDate) {
        String key = targetDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        Object cached = getCached(holidayCache, key);
        if (cached instanceof Map<?, ?> map) {
            return Maps.map(Json.deepCopy(map));
        }
        Map<String, Object> payload = request(
                "GET",
                "/uapi/domestic-stock/v1/quotations/chk-holiday",
                "CTCA0903R",
                Map.of(
                        "BASS_DT", key,
                        "CTX_AREA_FK", "",
                        "CTX_AREA_NK", ""
                ),
                null
        );
        List<Map<String, Object>> output = toMapList(payload.getOrDefault("output", List.of()));
        Map<String, Object> row = output.isEmpty() ? new LinkedHashMap<>() : output.get(0);
        boolean isOpen = "Y".equalsIgnoreCase(Maps.str(row, "opnd_yn", "N"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isOpen", isOpen);
        result.put("session", isOpen ? "REGULAR" : "CLOSED");
        result.put("message", isOpen ? "한국투자 Open API 기준 국내장이 열려 있습니다." : "한국투자 Open API 기준 국내장이 휴장입니다.");
        result.put("checkedAt", TimeUtil.nowSeoulIso());
        result.put("serverTime", TimeUtil.nowSeoulIso());
        result.put("dataSource", "kis");
        setCached(holidayCache, key, result, HOLIDAY_CACHE_SECONDS);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value) {
        List<Object> raw = (List<Object>) value;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : raw) {
            rows.add((Map<String, Object>) item);
        }
        return rows;
    }

    private Map<String, Object> request(
            String method,
            String path,
            String trId,
            Map<String, String> queryParams,
            Map<String, Object> body
    ) {
        try {
            String token = getAccessToken();
            String fullUrl = buildUrl(path, queryParams);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(fullUrl))
                    .timeout(Duration.ofMillis(config.kisTimeoutMs()))
                    .header("content-type", "application/json; charset=utf-8")
                    .header("custtype", "P")
                    .header("authorization", "Bearer " + token)
                    .header("appkey", config.kisAppKey())
                    .header("appsecret", config.kisAppSecret())
                    .header("tr_id", trId);

            if ("POST".equalsIgnoreCase(method)) {
                String payload = Json.stringify(body == null ? Map.of() : body);
                builder.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("KIS HTTP error: " + response.statusCode() + " body=" + response.body());
            }
            Map<String, Object> payload = Json.parseObject(response.body());
            String rtCd = Maps.str(payload, "rt_cd", "0");
            if (!"0".equals(rtCd)) {
                String msgCd = Maps.str(payload, "msg_cd", "UNKNOWN");
                String msg1 = Maps.str(payload, "msg1", "KIS API error");
                if ("EGW00123".equals(msgCd)) {
                    synchronized (lock) {
                        accessToken = null;
                        accessTokenExpiresAtEpochSeconds = 0;
                    }
                }
                throw new RuntimeException("KIS API error rt_cd=" + rtCd + " msg_cd=" + msgCd + " msg1=" + msg1);
            }
            return payload;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("KIS request failed", e);
        }
    }

    private String getAccessToken() {
        synchronized (lock) {
            long now = System.currentTimeMillis() / 1000L;
            if (accessToken != null && now < (accessTokenExpiresAtEpochSeconds - TOKEN_BUFFER_SECONDS)) {
                return accessToken;
            }
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("grant_type", "client_credentials");
            body.put("appkey", config.kisAppKey());
            body.put("appsecret", config.kisAppSecret());

            HttpRequest request = HttpRequest.newBuilder(URI.create(config.kisBaseUrl() + "/oauth2/tokenP"))
                    .timeout(Duration.ofMillis(config.kisTimeoutMs()))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("KIS token HTTP error: " + response.statusCode() + " body=" + response.body());
            }
            Map<String, Object> payload = Json.parseObject(response.body());
            String token = Maps.str(payload, "access_token");
            long expiresIn = Maps.longVal(payload.get("expires_in"), 24L * 60L * 60L);
            if (token == null || token.isBlank()) {
                throw new RuntimeException("KIS access token missing");
            }
            synchronized (lock) {
                accessToken = token;
                accessTokenExpiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn;
                return accessToken;
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("KIS token request failed", e);
        }
    }

    private String buildUrl(String path, Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder(config.kisBaseUrl()).append(path);
        if (queryParams != null && !queryParams.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                builder.append(first ? '?' : '&');
                first = false;
                builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                builder.append('=');
                builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return builder.toString();
    }

    private Object getCached(Map<String, CacheEntry> cache, String key) {
        synchronized (lock) {
            CacheEntry entry = cache.get(key);
            if (entry != null && System.currentTimeMillis() < entry.expiresAtEpochMillis()) {
                return entry.value();
            }
            return null;
        }
    }

    private void setCached(Map<String, CacheEntry> cache, String key, Object value, long ttlSeconds) {
        synchronized (lock) {
            cache.put(key, new CacheEntry(System.currentTimeMillis() + ttlSeconds * 1000L, Json.deepCopy(value)));
        }
    }

    private List<Map<String, Object>> aggregateMinutes(List<Map<String, Object>> rawCandles, int stepMinutes) {
        if (stepMinutes <= 1) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> candle : rawCandles) {
                Map<String, Object> row = new LinkedHashMap<>();
                ZonedDateTime dt = (ZonedDateTime) candle.get("dt");
                int open = Maps.intVal(candle, "open");
                int close = Maps.intVal(candle, "close");
                row.put("time", dt.format(DateTimeFormatter.ofPattern("HH:mm")));
                row.put("open", open);
                row.put("high", Maps.intVal(candle, "high"));
                row.put("low", Maps.intVal(candle, "low"));
                row.put("close", close);
                row.put("volume", Maps.intVal(candle, "volume"));
                row.put("color", close >= open ? "#ef4444" : "#3b82f6");
                rows.add(row);
            }
            return rows;
        }

        List<List<Map<String, Object>>> buckets = new ArrayList<>();
        List<Map<String, Object>> current = new ArrayList<>();
        for (Map<String, Object> candle : rawCandles) {
            current.add(candle);
            if (current.size() == stepMinutes) {
                buckets.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            buckets.add(current);
        }

        List<Map<String, Object>> aggregated = new ArrayList<>();
        for (List<Map<String, Object>> bucket : buckets) {
            Map<String, Object> first = bucket.get(0);
            Map<String, Object> last = bucket.get(bucket.size() - 1);
            int open = Maps.intVal(first, "open");
            int close = Maps.intVal(last, "close");
            int high = Integer.MIN_VALUE;
            int low = Integer.MAX_VALUE;
            int volume = 0;
            for (Map<String, Object> item : bucket) {
                high = Math.max(high, Maps.intVal(item, "high"));
                low = Math.min(low, Maps.intVal(item, "low"));
                volume += Maps.intVal(item, "volume");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            ZonedDateTime dt = (ZonedDateTime) last.get("dt");
            row.put("time", dt.format(DateTimeFormatter.ofPattern("HH:mm")));
            row.put("open", open);
            row.put("high", high);
            row.put("low", low);
            row.put("close", close);
            row.put("volume", volume);
            row.put("color", close >= open ? "#ef4444" : "#3b82f6");
            aggregated.add(row);
        }
        return aggregated;
    }

    private record CacheEntry(long expiresAtEpochMillis, Object value) {
    }
}
