package depth.finvibe.shared.state;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.market.MarketService;
import depth.finvibe.shared.util.FinvibeUtils;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AppState {
    private final Object lock = new Object();
    private final Path runtimeFile;
    private final MarketService marketService;

    private List<Map<String, Object>> indicesSeed;
    private List<Map<String, Object>> stocksSeed;
    private Map<String, List<Map<String, Object>>> homeRankingsSeed;
    private List<Map<String, Object>> themesSeed;
    private Map<String, List<Map<String, Object>>> themeNewsSeed;
    private List<Map<String, Object>> learningContentSeed;
    private List<String> recommendedKeywords;
    private Map<String, Object> aiInsight;
    private List<Map<String, Object>> badges;
    private List<Map<String, Object>> challenges;
    private Map<String, Object> xpProfile;
    private Map<String, Object> weeklyGoal;

    private int exchangeRate;
    private int walletBalance;
    private List<String> favorites;
    private List<String> tradingStockIds;
    private List<String> reservedStockIds;
    private List<Map<String, Object>> portfolios;
    private List<Map<String, Object>> folders;
    private List<Map<String, Object>> holdings;
    private List<Map<String, Object>> orders;
    private List<Map<String, Object>> completedChallenges;
    private List<Map<String, Object>> userRanking;
    private List<Map<String, Object>> squadRanking;
    private List<Map<String, Object>> squads;
    private int studyMinutes;
    private List<Map<String, Object>> courses;
    private int nextPortfolioSeq;
    private int nextCourseSeq;
    private int nextOrderSeq;

    private Map<String, Map<String, Object>> stockById;
    private Map<String, Map<String, Object>> stockByCode;
    private Map<String, Map<String, Object>> stockLookup;

    private int completedLessons;
    private int totalLessons;

    public AppState(Path dataDir, MarketService marketService) {
        this.marketService = marketService;
        this.runtimeFile = dataDir.resolve("app-state.json");
        loadSeeds();
        loadOrReset();
    }

    public void reset() {
        synchronized (lock) {
            Map<String, Object> simulatorStateSeed = Maps.map(FinvibeUtils.loadJsonResource("simulator_state.json"));
            List<Map<String, Object>> foldersSeed = toMapList(FinvibeUtils.loadJsonResource("portfolio_folders.json"));
            List<Map<String, Object>> holdingsSeed = toMapList(FinvibeUtils.loadJsonResource("portfolio_holdings.json"));
            Map<String, Object> learningStatsSeed = Maps.map(FinvibeUtils.loadJsonResource("learning_stats.json"));

            exchangeRate = Maps.intVal(simulatorStateSeed, "exchangeRate");
            walletBalance = Maps.intVal(simulatorStateSeed, "walletBalance");
            favorites = toStringList(simulatorStateSeed.get("favorites"));
            tradingStockIds = toStringList(simulatorStateSeed.get("tradingStocks"));
            reservedStockIds = toStringList(simulatorStateSeed.get("reservedStocks"));
            portfolios = toMapList(simulatorStateSeed.get("portfolios"));
            folders = copyMapList(foldersSeed);
            holdings = copyMapList(holdingsSeed);
            orders = new ArrayList<>();
            completedChallenges = new ArrayList<>();
            completedChallenges.add(mapOf(
                    "id", "challenge-dividend-completed",
                    "title", "배당주 챌린지",
                    "description", "배당주 포트폴리오 수익률 5% 달성",
                    "participants", 840,
                    "timeLeft", "완료",
                    "status", "completed",
                    "category", "theme"
            ));
            userRanking = new ArrayList<>();
            userRanking.add(mapOf("rank", 1, "nickname", "AlphaTrader", "returnRate", 21.3));
            userRanking.add(mapOf("rank", 2, "nickname", "ValueHunter", "returnRate", 19.7));
            userRanking.add(mapOf("rank", 3, "nickname", "MacroWizard", "returnRate", 18.5));
            userRanking.add(mapOf("rank", 37, "nickname", "FinVibeUser", "returnRate", 8.4));

            squadRanking = new ArrayList<>();
            squadRanking.add(mapOf("rank", 1, "squadName", "투자 고수들 🚀", "xp", 4200));
            squadRanking.add(mapOf("rank", 2, "squadName", "장기 투자파", "xp", 3980));
            squadRanking.add(mapOf("rank", 3, "squadName", "모멘텀 러너스", "xp", 3870));

            squads = new ArrayList<>();
            squads.add(mapOf("id", "squad-1", "name", "투자 고수들 🚀", "members", 5, "groupReturnRate", 12.4));
            squads.add(mapOf("id", "squad-2", "name", "배당 성장 클럽", "members", 7, "groupReturnRate", 8.7));
            squads.add(mapOf("id", "squad-3", "name", "테크 모멘텀", "members", 6, "groupReturnRate", 10.2));

            studyMinutes = Maps.intVal(learningStatsSeed, "studyMinutes");
            courses = loadCourses(toMapList(FinvibeUtils.loadJsonResource("courses.json")));
            nextPortfolioSeq = portfolios.stream().mapToInt(item -> Integer.parseInt(Maps.str(item, "id"))).max().orElse(0) + 1;
            nextCourseSeq = courses.stream().mapToInt(item -> Integer.parseInt(Maps.str(item, "id"))).max().orElse(0) + 1;
            nextOrderSeq = 1;

            buildStockIndexes();
            seedTradeHistory();
            syncDerivedLearningState();
            persist();
        }
    }

    public Map<String, Object> getWalletSummary() {
        synchronized (lock) {
            return mapOf(
                    "balance", walletBalance,
                    "currency", "KRW",
                    "exchangeRate", exchangeRate,
                    "availableUsd", Math.round((walletBalance * 100.0 / exchangeRate)) / 100.0
            );
        }
    }

    public Map<String, Object> chargeWallet(int amount) {
        synchronized (lock) {
            walletBalance += amount;
            persist();
            return getWalletSummary();
        }
    }

    public Map<String, Object> getMarketStatus() {
        return marketService.getMarketStatus();
    }

    public List<Map<String, Object>> getIndices() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : indicesSeed) {
            rows.add(FinvibeUtils.applyLiveIndex(item));
        }
        return rows;
    }

    public List<Map<String, Object>> getIndexChart(String indexName, int points) {
        Map<String, Object> index = null;
        for (Map<String, Object> item : indicesSeed) {
            if (FinvibeUtils.normalizeText(Maps.str(item, "name")).equals(FinvibeUtils.normalizeText(indexName))) {
                index = item;
                break;
            }
        }
        if (index == null) {
            throw ApiException.notFound("INDEX_NOT_FOUND", "지수를 찾을 수 없습니다: " + indexName);
        }
        return FinvibeUtils.generateIndexChart(Maps.str(index, "name"), Maps.doubleVal(index, "baseValue"), points);
    }

    public List<Map<String, Object>> getHomeRankings(String metric, String market, int limit) {
        List<Map<String, Object>> seed = homeRankingsSeed.get(metric);
        if (seed == null) {
            throw ApiException.badRequest("UNSUPPORTED_METRIC", "지원하지 않는 랭킹 타입입니다: " + metric);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : seed) {
            Map<String, Object> linkedStock = resolveStock(Maps.str(item, "stockId"));
            if (!"all".equals(market) && !market.equals(Maps.str(linkedStock, "type"))) {
                continue;
            }
            Map<String, Object> live = stockSnapshot(linkedStock);
            Map<String, Object> row = new LinkedHashMap<>();
            row.putAll(item);
            row.put("name", Maps.str(item, "canonicalName", Maps.str(linkedStock, "name")));
            row.put("displayName", Maps.str(item, "name"));
            row.put("ticker", Maps.str(linkedStock, "code"));
            row.put("type", Maps.str(linkedStock, "type"));
            row.put("price", FinvibeUtils.formatHomePrice(Maps.doubleVal(live, "priceKrw")));
            row.put("change", FinvibeUtils.formatChangePercent(Maps.doubleVal(live, "changeRate")));
            row.put("isUp", Maps.doubleVal(live, "changeRate") >= 0);
            result.add(row);
        }
        for (int i = 0; i < result.size(); i++) {
            result.get(i).put("rank", i + 1);
        }
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    public List<Map<String, Object>> listThemes(String category) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> theme : themesSeed) {
            if (!"all".equals(category) && !category.equals(Maps.str(theme, "category"))) {
                continue;
            }
            Map<String, Object> row = copyMap(theme);
            row.put("topStock", Maps.str(theme, "topStockName"));
            row.put("newsCount", themeNewsSeed.getOrDefault(Maps.str(theme, "id"), List.of()).size());
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> featuredTheme() {
        int index = LocalDateTime.now(TimeUtil.SEOUL).getDayOfYear() % themesSeed.size();
        Map<String, Object> theme = copyMap(themesSeed.get(index));
        theme.put("topStock", Maps.str(theme, "topStockName"));
        return theme;
    }

    public Map<String, Object> getTheme(String themeId) {
        for (Map<String, Object> theme : themesSeed) {
            if (themeId.equals(Maps.str(theme, "id"))) {
                Map<String, Object> row = copyMap(theme);
                row.put("topStock", Maps.str(theme, "topStockName"));
                return row;
            }
        }
        throw ApiException.notFound("THEME_NOT_FOUND", "테마를 찾을 수 없습니다: " + themeId);
    }

    public List<Map<String, Object>> getThemeChart(String themeId, int days) {
        Map<String, Object> theme = getTheme(themeId);
        return FinvibeUtils.generateThemeChart(themeId, Maps.doubleVal(theme, "basePrice"), days);
    }

    public List<Map<String, Object>> getThemeNews(String themeId) {
        List<Map<String, Object>> rows = themeNewsSeed.get(themeId);
        if (rows == null) {
            throw ApiException.notFound("THEME_NEWS_NOT_FOUND", "테마 뉴스를 찾을 수 없습니다: " + themeId);
        }
        return copyMapList(rows);
    }

    public List<String> getTrendingKeywords() {
        List<String> keywords = new ArrayList<>();
        for (int i = 0; i < Math.min(6, themesSeed.size()); i++) {
            keywords.add(Maps.str(themesSeed.get(i), "name"));
        }
        for (Map<String, Object> item : getHomeRankings("trading", "all", 4)) {
            keywords.add(Maps.str(item, "name"));
        }
        if (keywords.size() > 10) {
            return new ArrayList<>(keywords.subList(0, 10));
        }
        return keywords;
    }

    public Map<String, Object> getHomeScreen() {
        Map<String, Object> featured = featuredTheme();
        Map<String, Object> rankings = new LinkedHashMap<>();
        rankings.put("trading", getHomeRankings("trading", "all", 20));
        rankings.put("volume", getHomeRankings("volume", "all", 20));
        rankings.put("surge", getHomeRankings("surge", "all", 20));
        rankings.put("drop", getHomeRankings("drop", "all", 20));
        rankings.put("personal", getHomeRankings("personal", "all", 10));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", TimeUtil.nowSeoulIso());
        result.put("indices", getIndices());
        result.put("rankings", rankings);
        result.put("featuredTheme", featured);
        result.put("featuredThemeChart", getThemeChart(Maps.str(featured, "id"), 30));
        result.put("featuredThemeNews", getThemeNews(Maps.str(featured, "id")));
        result.put("themes", listThemes("all"));
        result.put("trendingKeywords", getTrendingKeywords());
        return result;
    }

    public Map<String, Object> search(String query, int limit) {
        String q = FinvibeUtils.normalizeText(query);
        List<Map<String, Object>> stockResults = new ArrayList<>();
        for (Map<String, Object> stock : stocksSeed) {
            List<String> haystack = new ArrayList<>();
            haystack.add(Maps.str(stock, "name"));
            haystack.add(Maps.str(stock, "code"));
            haystack.addAll(toStringList(stock.get("aliases")));
            boolean matches = q.isBlank();
            for (String candidate : haystack) {
                if (FinvibeUtils.normalizeText(candidate).contains(q)) {
                    matches = true;
                    break;
                }
            }
            if (matches) {
                stockResults.add(mapOf(
                        "stockId", Maps.str(stock, "id"),
                        "name", Maps.str(stock, "name"),
                        "code", Maps.str(stock, "code"),
                        "type", Maps.str(stock, "type"),
                        "aliases", toStringList(stock.get("aliases"))
                ));
            }
        }
        stockResults.sort(Comparator.comparing((Map<String, Object> row) -> FinvibeUtils.normalizeText(Maps.str(row, "name")).startsWith(q) ? 0 : 1)
                .thenComparing(row -> Maps.str(row, "name")));

        List<Map<String, Object>> learningResults = new ArrayList<>();
        List<String> seenTitles = new ArrayList<>();
        for (Map<String, Object> item : learningContentSeed) {
            if (q.isBlank() || FinvibeUtils.normalizeText(Maps.str(item, "title")).contains(q)) {
                seenTitles.add(Maps.str(item, "title"));
                learningResults.add(copyMap(item));
            }
        }
        for (Map<String, Object> course : courses) {
            if (seenTitles.contains(Maps.str(course, "title"))) {
                continue;
            }
            if (q.isBlank() || FinvibeUtils.normalizeText(Maps.str(course, "title")).contains(q)) {
                learningResults.add(mapOf(
                        "id", Maps.str(course, "id"),
                        "title", Maps.str(course, "title"),
                        "category", FinvibeUtils.levelToKorean(Maps.str(course, "level")),
                        "contentType", "course"
                ));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("stocks", stockResults.subList(0, Math.min(limit, stockResults.size())));
        result.put("learning", learningResults.subList(0, Math.min(limit, learningResults.size())));
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> stockSearchOnly(String query, int limit) {
        return (List<Map<String, Object>>) search(query, limit).get("stocks");
    }

    public List<Map<String, Object>> listStocks(String market, String query, Integer limit) {
        String q = FinvibeUtils.normalizeText(query);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> stock : stocksSeed) {
            if (!"all".equals(market) && !market.equals(Maps.str(stock, "type"))) {
                continue;
            }
            if (!q.isBlank()) {
                List<String> haystack = new ArrayList<>();
                haystack.add(Maps.str(stock, "name"));
                haystack.add(Maps.str(stock, "code"));
                haystack.addAll(toStringList(stock.get("aliases")));
                boolean matches = false;
                for (String candidate : haystack) {
                    if (FinvibeUtils.normalizeText(candidate).contains(q)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    continue;
                }
            }
            filtered.add(stock);
        }
        List<Map<String, Object>> rows = marketService.listStockSnapshots(filtered, exchangeRate);
        rows.sort(Comparator.comparing((Map<String, Object> row) -> Maps.str(row, "type"))
                .thenComparing(row -> Maps.str(row, "name")));
        if (limit != null && rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    public Map<String, Object> getStockDetail(String stockId) {
        return stockSnapshot(stockId);
    }

    public List<Map<String, Object>> getStockCandles(String stockId, String timeframe, Integer points) {
        Map<String, Object> stock = resolveStock(stockId);
        return marketService.getCandles(stock, timeframe, points);
    }

    public Map<String, Object> getOrderBook(String stockId) {
        Map<String, Object> stock = resolveStock(stockId);
        return marketService.getOrderBook(stock, exchangeRate);
    }

    public Map<String, Object> getOwnedStock(String stockIdentifier) {
        Map<String, Object> stock = resolveStock(stockIdentifier);
        for (Map<String, Object> item : listHoldings(null)) {
            if (Maps.str(stock, "code").equals(Maps.str(item, "code"))) {
                return item;
            }
        }
        return null;
    }

    public Map<String, Object> getSimulatorState() {
        Map<String, Object> result = new LinkedHashMap<>();
        synchronized (lock) {
            result.put("favorites", new ArrayList<>(favorites));
            result.put("tradingStocks", new ArrayList<>(tradingStockIds));
            result.put("reservedStocks", new ArrayList<>(reservedStockIds));
            result.put("portfolios", listPortfolios());
        }
        result.put("wallet", getWalletSummary());
        return result;
    }

    public Map<String, Object> getSimulatorScreen() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", TimeUtil.nowSeoulIso());
        result.put("stocks", listStocks("all", "", null));
        result.put("state", getSimulatorState());
        return result;
    }

    public Map<String, Object> getStockScreen(String stockId, String timeframe) {
        Map<String, Object> owned = getOwnedStock(stockId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", TimeUtil.nowSeoulIso());
        result.put("stock", getStockDetail(stockId));
        result.put("chartData", getStockCandles(stockId, timeframe, null));
        result.put("orderBook", getOrderBook(stockId));
        result.put("wallet", getWalletSummary());
        result.put("ownedStock", owned);
        result.put("ownedQuantity", owned == null ? 0 : Maps.intVal(owned, "quantity"));
        result.put("exchangeRate", exchangeRate);
        result.put("folders", listFolders());
        result.put("portfolioHoldings", listHoldings(null));
        return result;
    }

    public List<String> addFavorite(String stockId) {
        synchronized (lock) {
            resolveStock(stockId);
            if (!favorites.contains(stockId)) {
                favorites.add(stockId);
                persist();
            }
            return new ArrayList<>(favorites);
        }
    }

    public List<String> removeFavorite(String stockId) {
        synchronized (lock) {
            favorites.removeIf(stockId::equals);
            persist();
            return new ArrayList<>(favorites);
        }
    }

    public List<Map<String, Object>> listFavorites() {
        synchronized (lock) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String stockId : favorites) {
                rows.add(stockSnapshot(stockId));
            }
            return rows;
        }
    }

    public List<Map<String, Object>> watchlist(String tab, String market) {
        List<String> stockIds = switch (tab) {
            case "favorite" -> new ArrayList<>(favorites);
            case "trading" -> new ArrayList<>(tradingStockIds);
            case "reserved" -> new ArrayList<>(reservedStockIds);
            default -> throw ApiException.badRequest("UNSUPPORTED_TAB", "지원하지 않는 탭입니다: " + tab);
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String stockId : stockIds) {
            Map<String, Object> live = stockSnapshot(stockId);
            if (!"all".equals(market) && !market.equals(Maps.str(live, "type"))) {
                continue;
            }
            rows.add(live);
        }
        return rows;
    }

    public List<Map<String, Object>> listPortfolios() {
        synchronized (lock) {
            return copyMapList(portfolios);
        }
    }

    public Map<String, Object> createPortfolio(String name, List<String> stocks) {
        synchronized (lock) {
            Map<String, Object> portfolio = mapOf("id", String.valueOf(nextPortfolioSeq++), "name", name, "stocks", stocks == null ? new ArrayList<>() : new ArrayList<>(stocks));
            portfolios.add(portfolio);
            persist();
            return copyMap(portfolio);
        }
    }

    public Map<String, Object> updatePortfolio(String portfolioId, String name, List<String> stocks) {
        synchronized (lock) {
            Map<String, Object> portfolio = findPortfolioRef(portfolioId);
            if (portfolio == null) {
                throw ApiException.notFound("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + portfolioId);
            }
            if (name != null) {
                portfolio.put("name", name);
            }
            if (stocks != null) {
                portfolio.put("stocks", new ArrayList<>(stocks));
            }
            persist();
            return copyMap(portfolio);
        }
    }

    public void deletePortfolio(String portfolioId) {
        synchronized (lock) {
            boolean removed = portfolios.removeIf(item -> portfolioId.equals(Maps.str(item, "id")));
            if (!removed) {
                throw ApiException.notFound("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + portfolioId);
            }
            persist();
        }
    }

    public List<Map<String, Object>> listFolders() {
        synchronized (lock) {
            List<Map<String, Object>> rows = new ArrayList<>();
            List<Map<String, Object>> currentHoldings = listHoldings(null);
            for (Map<String, Object> folder : folders) {
                int stockCount = 0;
                for (Map<String, Object> holding : currentHoldings) {
                    if (Maps.str(folder, "id").equals(Maps.str(holding, "folderId"))) {
                        stockCount++;
                    }
                }
                Map<String, Object> row = copyMap(folder);
                row.put("stockCount", stockCount);
                rows.add(row);
            }
            return rows;
        }
    }

    public Map<String, Object> createFolder(String name, String color) {
        synchronized (lock) {
            Map<String, Object> folder = mapOf(
                    "id", "folder-" + (folders.size() + 1),
                    "name", name,
                    "color", color == null ? "#3b82f6" : color
            );
            folders.add(folder);
            persist();
            return copyMap(folder);
        }
    }

    public Map<String, Object> updateFolder(String folderId, String name, String color) {
        synchronized (lock) {
            Map<String, Object> folder = findFolderRef(folderId);
            if (folder == null) {
                throw ApiException.notFound("FOLDER_NOT_FOUND", "폴더를 찾을 수 없습니다: " + folderId);
            }
            if (name != null) {
                folder.put("name", name);
            }
            if (color != null) {
                folder.put("color", color);
            }
            persist();
            return copyMap(folder);
        }
    }

    public void deleteFolder(String folderId) {
        synchronized (lock) {
            boolean exists = false;
            for (Map<String, Object> folder : folders) {
                if (folderId.equals(Maps.str(folder, "id"))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                throw ApiException.notFound("FOLDER_NOT_FOUND", "폴더를 찾을 수 없습니다: " + folderId);
            }
            folders.removeIf(item -> folderId.equals(Maps.str(item, "id")));
            for (Map<String, Object> holding : holdings) {
                if (folderId.equals(Maps.str(holding, "folderId"))) {
                    holding.put("folderId", null);
                }
            }
            persist();
        }
    }

    public List<Map<String, Object>> listHoldings(String folderId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        synchronized (lock) {
            for (Map<String, Object> holding : holdings) {
                if (folderId != null && !folderId.equals(Maps.str(holding, "folderId"))) {
                    continue;
                }
                Map<String, Object> row = copyMap(holding);
                try {
                    Map<String, Object> stock = resolveStock(Maps.str(holding, "code"));
                    Map<String, Object> live = stockSnapshot(stock);
                    double currentPrice = Maps.doubleVal(live, "price");
                    double quantity = Maps.doubleVal(row, "quantity");
                    double avgPrice = Maps.doubleVal(row, "avgPrice");
                    row.put("currentPrice", currentPrice);
                    row.put("currentValue", Math.round(currentPrice * quantity * 100.0) / 100.0);
                    row.put("profitRate", Math.round(((currentPrice - avgPrice) / avgPrice) * 10000.0) / 100.0);
                } catch (Exception ignored) {
                    double currentValue = Maps.doubleVal(row, "currentPrice") * Maps.doubleVal(row, "quantity");
                    row.put("currentValue", Math.round(currentValue * 100.0) / 100.0);
                    row.put("profitRate", Math.round(((Maps.doubleVal(row, "currentPrice") - Maps.doubleVal(row, "avgPrice")) / Maps.doubleVal(row, "avgPrice")) * 10000.0) / 100.0);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    public Map<String, Object> createOrder(Map<String, Object> payload, boolean forcePending) {
        synchronized (lock) {
            Map<String, Object> stock = resolveStock(Maps.str(payload, "stockId"));
            Map<String, Object> liveStock = stockSnapshot(stock);
            double marketPrice = Maps.doubleVal(liveStock, "price");
            String priceType = Maps.str(payload, "priceType", "market");
            double price = "market".equals(priceType) || payload.get("price") == null ? marketPrice : Maps.doubleVal(payload, "price");
            price = FinvibeUtils.roundStockPrice(price, Maps.str(stock, "type"));
            int quantity = Maps.intVal(payload, "quantity");
            String type = Maps.str(payload, "type");
            String autoCondition = Maps.str(payload, "autoCondition");
            Double triggerPrice = payload.get("triggerPrice") == null ? null : Maps.doubleVal(payload, "triggerPrice");
            String status = forcePending || "scheduled".equals(priceType) || autoCondition != null ? "pending" : "completed";
            double total = Math.round(price * quantity * 100.0) / 100.0;
            int totalKrw = priceToKrw(stock, total);

            if ("completed".equals(status)) {
                if ("buy".equals(type)) {
                    if (totalKrw > walletBalance) {
                        throw ApiException.conflict("INSUFFICIENT_BALANCE", "잔액이 부족합니다.");
                    }
                    walletBalance -= totalKrw;
                    applyBuy(stock, price, quantity);
                } else {
                    applySell(stock, price, quantity);
                    walletBalance += totalKrw;
                }
            } else {
                appendIfMissing(reservedStockIds, Maps.str(stock, "id"));
            }

            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("orderId", nextOrderId());
            receipt.put("stockId", Maps.str(stock, "id"));
            receipt.put("stockName", Maps.str(stock, "name"));
            receipt.put("type", type);
            receipt.put("priceType", priceType);
            receipt.put("price", price);
            receipt.put("quantity", quantity);
            receipt.put("total", total);
            receipt.put("totalKrw", totalKrw);
            receipt.put("status", status);
            receipt.put("createdAt", TimeUtil.nowSeoulIso());
            receipt.put("autoCondition", autoCondition);
            receipt.put("triggerPrice", triggerPrice);

            orders.add(0, receipt);
            syncReservedIds();
            persist();
            return copyMap(receipt);
        }
    }

    public Map<String, Object> cancelOrder(String orderId) {
        synchronized (lock) {
            Map<String, Object> order = findOrderRef(orderId);
            if (order == null) {
                throw ApiException.notFound("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다: " + orderId);
            }
            if (!"pending".equals(Maps.str(order, "status"))) {
                throw ApiException.conflict("ORDER_NOT_CANCELABLE", "대기 중인 주문만 취소할 수 있습니다.");
            }
            order.put("status", "canceled");
            syncReservedIds();
            persist();
            return copyMap(order);
        }
    }

    public List<Map<String, Object>> listOrders(String status, String kind) {
        List<Map<String, Object>> rows = new ArrayList<>();
        synchronized (lock) {
            for (Map<String, Object> order : orders) {
                if (status != null && !status.equals(Maps.str(order, "status"))) {
                    continue;
                }
                String orderKind = order.get("autoCondition") != null || "scheduled".equals(Maps.str(order, "priceType")) ? "auto" : "manual";
                if (kind != null && !kind.equals(orderKind)) {
                    continue;
                }
                Map<String, Object> row = copyMap(order);
                row.put("kind", orderKind);
                rows.add(row);
            }
        }
        return rows;
    }

    public Map<String, Object> getOrder(String orderId) {
        synchronized (lock) {
            Map<String, Object> order = findOrderRef(orderId);
            if (order == null) {
                throw ApiException.notFound("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다: " + orderId);
            }
            return copyMap(order);
        }
    }

    public List<Map<String, Object>> listCourses() {
        synchronized (lock) {
            syncDerivedLearningState();
            return copyMapList(courses);
        }
    }

    public Map<String, Object> previewCourse(Map<String, Object> payload) {
        List<String> keywords = toStringList(payload.get("keywords"));
        String courseName = Maps.str(payload, "courseName");
        String level = FinvibeUtils.inferCourseLevel(keywords);
        List<String> suggestions = new ArrayList<>();
        suggestions.add((keywords.isEmpty() ? "투자" : keywords.get(0)) + " 기초 개념 이해하기");
        suggestions.add("관련 차트 분석 및 지표 활용법");
        suggestions.add("실전 투자 전략 수립하기");
        suggestions.add("포트폴리오 구성 및 리스크 관리");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", courseName);
        result.put("keywords", keywords);
        result.put("suggestedLessons", suggestions);
        result.put("inferredLevel", level);
        return result;
    }

    public Map<String, Object> createCourse(Map<String, Object> payload) {
        synchronized (lock) {
            Map<String, Object> preview = previewCourse(payload);
            String courseId = String.valueOf(nextCourseSeq++);
            List<String> keywords = toStringList(payload.get("keywords"));
            List<String> suggestedLessons = toStringList(preview.get("suggestedLessons"));
            List<Map<String, Object>> lessonDetails = new ArrayList<>();
            for (int i = 0; i < suggestedLessons.size(); i++) {
                lessonDetails.add(mapOf(
                        "id", courseId + "-lesson-" + (i + 1),
                        "title", suggestedLessons.get(i),
                        "durationMinutes", 6 + (i + 1),
                        "completed", Boolean.FALSE
                ));
            }
            Map<String, Object> course = new LinkedHashMap<>();
            course.put("id", courseId);
            course.put("title", Maps.str(payload, "courseName"));
            course.put("description", String.join(", ", keywords.subList(0, Math.min(3, keywords.size()))) + " 중심 AI 맞춤 학습 코스");
            course.put("level", preview.get("inferredLevel"));
            course.put("progress", 0);
            course.put("completed", Boolean.FALSE);
            course.put("keywords", keywords);
            course.put("lessons", suggestedLessons);
            course.put("lessonDetails", lessonDetails);
            courses.add(0, course);
            syncDerivedLearningState();
            persist();
            return copyMap(course);
        }
    }

    public Map<String, Object> getLesson(String lessonId) {
        synchronized (lock) {
            for (Map<String, Object> course : courses) {
                for (Map<String, Object> lesson : toMapList(course.get("lessonDetails"))) {
                    if (lessonId.equals(Maps.str(lesson, "id"))) {
                        Map<String, Object> result = copyMap(lesson);
                        result.put("courseId", Maps.str(course, "id"));
                        result.put("courseTitle", Maps.str(course, "title"));
                        result.put("description", Maps.str(lesson, "title") + " 학습을 통해 실전 투자에 바로 활용할 수 있는 핵심 개념을 익힙니다.");
                        return result;
                    }
                }
            }
        }
        throw ApiException.notFound("LESSON_NOT_FOUND", "레슨을 찾을 수 없습니다: " + lessonId);
    }

    public Map<String, Object> completeLesson(String lessonId) {
        synchronized (lock) {
            for (Map<String, Object> course : courses) {
                for (Map<String, Object> lesson : toMapList(course.get("lessonDetails"))) {
                    if (lessonId.equals(Maps.str(lesson, "id"))) {
                        if (!Boolean.TRUE.equals(lesson.get("completed"))) {
                            lesson.put("completed", Boolean.TRUE);
                            studyMinutes += Maps.intVal(lesson, "durationMinutes");
                            xpProfile.put("totalXp", Maps.intVal(xpProfile, "totalXp") + 50);
                            xpProfile.put("xpToNextLevel", Math.max(Maps.intVal(xpProfile, "xpToNextLevel") - 50, 0));
                        }
                        syncDerivedLearningState();
                        persist();
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("lesson", copyMap(lesson));
                        result.put("course", copyMap(course));
                        result.put("stats", metricsMe());
                        result.put("xp", xpMe());
                        return result;
                    }
                }
            }
        }
        throw ApiException.notFound("LESSON_NOT_FOUND", "레슨을 찾을 수 없습니다: " + lessonId);
    }

    public List<Map<String, Object>> lessonCompletions() {
        List<Map<String, Object>> rows = new ArrayList<>();
        synchronized (lock) {
            for (Map<String, Object> course : courses) {
                for (Map<String, Object> lesson : toMapList(course.get("lessonDetails"))) {
                    if (Boolean.TRUE.equals(lesson.get("completed"))) {
                        rows.add(mapOf(
                                "courseId", Maps.str(course, "id"),
                                "lessonId", Maps.str(lesson, "id"),
                                "title", Maps.str(lesson, "title")
                        ));
                    }
                }
            }
        }
        return rows;
    }

    public Map<String, Object> metricsMe() {
        synchronized (lock) {
            syncDerivedLearningState();
            return mapOf(
                    "completedLessons", completedLessons,
                    "totalLessons", totalLessons,
                    "studyMinutes", studyMinutes,
                    "xp", Maps.intVal(xpProfile, "totalXp")
            );
        }
    }

    public Map<String, Object> xpMe() {
        synchronized (lock) {
            return copyMap(xpProfile);
        }
    }

    public List<Map<String, Object>> xpUserRanking() {
        synchronized (lock) {
            return copyMapList(userRanking);
        }
    }

    public List<Map<String, Object>> xpSquadRanking() {
        synchronized (lock) {
            return copyMapList(squadRanking);
        }
    }

    public Map<String, Object> squadContributionMe() {
        return mapOf("squadId", "squad-1", "squadName", "투자 고수들 🚀", "contributionXp", 320);
    }

    public List<Map<String, Object>> listSquads() {
        synchronized (lock) {
            return copyMapList(squads);
        }
    }

    public Map<String, Object> squadMe() {
        synchronized (lock) {
            return copyMap(squads.get(0));
        }
    }

    public List<Map<String, Object>> listBadges() {
        synchronized (lock) {
            return copyMapList(badges);
        }
    }

    public List<Map<String, Object>> listChallenges() {
        synchronized (lock) {
            return copyMapList(challenges);
        }
    }

    public List<Map<String, Object>> listCompletedChallenges() {
        synchronized (lock) {
            return copyMapList(completedChallenges);
        }
    }

    public List<String> recommendedKeywordsList() {
        synchronized (lock) {
            return new ArrayList<>(recommendedKeywords);
        }
    }

    public Map<String, Object> aiRecommendationToday() {
        synchronized (lock) {
            return mapOf(
                    "todayRecommendation", Maps.str(aiInsight, "todayRecommendation"),
                    "learningStyleAnalysis", Maps.str(aiInsight, "learningStyleAnalysis")
            );
        }
    }



    public double currentReturnRateForNickname(String nickname) {
        synchronized (lock) {
            for (Map<String, Object> row : userRanking) {
                if (nickname.equals(Maps.str(row, "nickname"))) {
                    return Maps.doubleVal(row, "returnRate");
                }
            }
            return 0.0;
        }
    }

    public int addStudyMinute(String lessonId) {
        synchronized (lock) {
            getLesson(lessonId);
            studyMinutes += 1;
            persist();
            return studyMinutes;
        }
    }

    public Map<String, Object> joinSquad(String squadId) {
        synchronized (lock) {
            for (int i = 0; i < squads.size(); i++) {
                Map<String, Object> squad = squads.get(i);
                if (squadId.equals(Maps.str(squad, "id"))) {
                    squads.remove(i);
                    squads.add(0, squad);
                    persist();
                    return copyMap(squad);
                }
            }
            return null;
        }
    }

    public List<String> reservedStockIds() {
        synchronized (lock) {
            return new ArrayList<>(reservedStockIds);
        }
    }

    public Map<String, Object> learningDashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedAt", TimeUtil.nowSeoulIso());
        result.put("aiInsight", aiRecommendationToday());
        result.put("courses", listCourses());
        result.put("badges", listBadges());
        result.put("recommendedKeywords", recommendedKeywordsList());
        result.put("stats", metricsMe());
        result.put("weeklyGoal", copyMap(weeklyGoal));
        result.put("xp", xpMe());
        result.put("challenges", listChallenges());
        return result;
    }

    public Map<String, Object> resolveStock(String identifier) {
        synchronized (lock) {
            if (stockById.containsKey(identifier)) {
                return stockById.get(identifier);
            }
            if (stockByCode.containsKey(identifier)) {
                return stockByCode.get(identifier);
            }
            String normalized = FinvibeUtils.normalizeText(identifier);
            if (stockLookup.containsKey(normalized)) {
                return stockLookup.get(normalized);
            }
        }
        throw ApiException.notFound("STOCK_NOT_FOUND", "종목을 찾을 수 없습니다: " + identifier);
    }

    public Map<String, Object> stockSnapshot(String identifier) {
        return stockSnapshot(resolveStock(identifier));
    }

    public Map<String, Object> stockSnapshot(Map<String, Object> stock) {
        return marketService.getStockSnapshot(stock, exchangeRate);
    }

    private void loadSeeds() {
        indicesSeed = toMapList(FinvibeUtils.loadJsonResource("indices.json"));
        stocksSeed = toMapList(FinvibeUtils.loadJsonResource("stocks.json"));

        homeRankingsSeed = new LinkedHashMap<>();
        Map<String, Object> homeRankingsRaw = Maps.map(FinvibeUtils.loadJsonResource("home_rankings.json"));
        for (Map.Entry<String, Object> entry : homeRankingsRaw.entrySet()) {
            homeRankingsSeed.put(entry.getKey(), toMapList(entry.getValue()));
        }

        themesSeed = toMapList(FinvibeUtils.loadJsonResource("themes.json"));
        themeNewsSeed = new LinkedHashMap<>();
        Map<String, Object> themeNewsRaw = Maps.map(FinvibeUtils.loadJsonResource("theme_news.json"));
        for (Map.Entry<String, Object> entry : themeNewsRaw.entrySet()) {
            themeNewsSeed.put(entry.getKey(), toMapList(entry.getValue()));
        }

        learningContentSeed = toMapList(FinvibeUtils.loadJsonResource("learning_content.json"));
        recommendedKeywords = toStringList(FinvibeUtils.loadJsonResource("recommended_keywords.json"));
        aiInsight = Maps.map(FinvibeUtils.loadJsonResource("ai_insight.json"));
        badges = toMapList(FinvibeUtils.loadJsonResource("badges.json"));
        challenges = toMapList(FinvibeUtils.loadJsonResource("challenges.json"));
        xpProfile = Maps.map(FinvibeUtils.loadJsonResource("xp_profile.json"));
        weeklyGoal = Maps.map(FinvibeUtils.loadJsonResource("weekly_goal.json"));
    }

    private void loadOrReset() {
        synchronized (lock) {
            if (!Files.exists(runtimeFile)) {
                reset();
                return;
            }
            try {
                Map<String, Object> data = Maps.map(Json.parse(Files.readString(runtimeFile, StandardCharsets.UTF_8)));
                exchangeRate = Maps.intVal(data, "exchangeRate");
                walletBalance = Maps.intVal(data, "walletBalance");
                favorites = toStringList(data.get("favorites"));
                tradingStockIds = toStringList(data.get("tradingStockIds"));
                reservedStockIds = toStringList(data.get("reservedStockIds"));
                portfolios = toMapList(data.get("portfolios"));
                folders = toMapList(data.get("folders"));
                holdings = toMapList(data.get("holdings"));
                orders = toMapList(data.get("orders"));
                completedChallenges = toMapList(data.get("completedChallenges"));
                userRanking = toMapList(data.get("userRanking"));
                squadRanking = toMapList(data.get("squadRanking"));
                squads = toMapList(data.get("squads"));
                studyMinutes = Maps.intVal(data, "studyMinutes");
                courses = toMapList(data.get("courses"));
                nextPortfolioSeq = Maps.intVal(data, "nextPortfolioSeq", 1);
                nextCourseSeq = Maps.intVal(data, "nextCourseSeq", 1);
                nextOrderSeq = Maps.intVal(data, "nextOrderSeq", 1);
                badges = toMapList(data.getOrDefault("badges", badges));
                challenges = toMapList(data.getOrDefault("challenges", challenges));
                xpProfile = Maps.map(data.getOrDefault("xpProfile", xpProfile));
                weeklyGoal = Maps.map(data.getOrDefault("weeklyGoal", weeklyGoal));
                buildStockIndexes();
                syncDerivedLearningState();
            } catch (Exception e) {
                reset();
            }
        }
    }

    private void persist() {
        synchronized (lock) {
            try {
                Files.createDirectories(runtimeFile.getParent());
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("exchangeRate", exchangeRate);
                data.put("walletBalance", walletBalance);
                data.put("favorites", favorites);
                data.put("tradingStockIds", tradingStockIds);
                data.put("reservedStockIds", reservedStockIds);
                data.put("portfolios", portfolios);
                data.put("folders", folders);
                data.put("holdings", holdings);
                data.put("orders", orders);
                data.put("completedChallenges", completedChallenges);
                data.put("userRanking", userRanking);
                data.put("squadRanking", squadRanking);
                data.put("squads", squads);
                data.put("studyMinutes", studyMinutes);
                data.put("courses", courses);
                data.put("nextPortfolioSeq", nextPortfolioSeq);
                data.put("nextCourseSeq", nextCourseSeq);
                data.put("nextOrderSeq", nextOrderSeq);
                data.put("badges", badges);
                data.put("challenges", challenges);
                data.put("xpProfile", xpProfile);
                data.put("weeklyGoal", weeklyGoal);
                Files.writeString(runtimeFile, Json.stringify(data), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List<Map<String, Object>> loadCourses(List<Map<String, Object>> courseSeeds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> courseSeed : courseSeeds) {
            Map<String, Object> course = copyMap(courseSeed);
            List<String> lessonTitles = toStringList(courseSeed.get("lessons"));
            List<Map<String, Object>> lessonDetails = new ArrayList<>();
            int total = lessonTitles.size();
            int completedCount = Boolean.TRUE.equals(courseSeed.get("completed")) ? total : (int) Math.floor(total * Maps.doubleVal(courseSeed, "progress") / 100.0);
            for (int index = 0; index < lessonTitles.size(); index++) {
                lessonDetails.add(mapOf(
                        "id", Maps.str(courseSeed, "id") + "-lesson-" + (index + 1),
                        "title", lessonTitles.get(index),
                        "durationMinutes", 5 + (((index + 1) * 3) % 7),
                        "completed", index < completedCount
                ));
            }
            course.put("lessonDetails", lessonDetails);
            course.put("keywords", new ArrayList<>());
            rows.add(course);
        }
        return rows;
    }

    private void buildStockIndexes() {
        stockById = new LinkedHashMap<>();
        stockByCode = new LinkedHashMap<>();
        stockLookup = new LinkedHashMap<>();
        for (Map<String, Object> stock : stocksSeed) {
            stockById.put(Maps.str(stock, "id"), stock);
            stockByCode.put(Maps.str(stock, "code"), stock);
            stockLookup.put(FinvibeUtils.normalizeText(Maps.str(stock, "name")), stock);
            stockLookup.put(FinvibeUtils.normalizeText(Maps.str(stock, "code")), stock);
            for (String alias : toStringList(stock.get("aliases"))) {
                stockLookup.put(FinvibeUtils.normalizeText(alias), stock);
            }
        }
    }

    private void seedTradeHistory() {
        List<Map<String, Object>> seedOrders = new ArrayList<>();
        seedOrders.add(mapOf("stockId", "1", "type", "buy", "priceType", "limit", "price", 70000, "quantity", 10, "status", "completed"));
        seedOrders.add(mapOf("stockId", "3", "type", "buy", "priceType", "limit", "price", 230000, "quantity", 5, "status", "completed"));
        seedOrders.add(mapOf("stockId", "6", "type", "buy", "priceType", "scheduled", "price", 170.0, "quantity", 3, "status", "pending", "autoCondition", "below", "triggerPrice", 170.0));

        for (Map<String, Object> payload : seedOrders) {
            Map<String, Object> stock = resolveStock(Maps.str(payload, "stockId"));
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("orderId", nextOrderId());
            receipt.put("stockId", Maps.str(stock, "id"));
            receipt.put("stockName", Maps.str(stock, "name"));
            receipt.put("type", Maps.str(payload, "type"));
            receipt.put("priceType", Maps.str(payload, "priceType"));
            receipt.put("price", payload.get("price"));
            receipt.put("quantity", payload.get("quantity"));
            double total = Maps.doubleVal(payload, "price") * Maps.intVal(payload, "quantity");
            receipt.put("total", Math.round(total * 100.0) / 100.0);
            receipt.put("totalKrw", priceToKrw(stock, total));
            receipt.put("status", Maps.str(payload, "status"));
            receipt.put("createdAt", TimeUtil.nowSeoulIso());
            receipt.put("autoCondition", payload.get("autoCondition"));
            receipt.put("triggerPrice", payload.get("triggerPrice"));
            orders.add(receipt);
        }
    }

    private void syncDerivedLearningState() {
        int completed = 0;
        int total = 0;
        for (Map<String, Object> course : courses) {
            List<Map<String, Object>> lessonDetails = toMapList(course.get("lessonDetails"));
            total += lessonDetails.size();
            int completedCount = 0;
            List<String> lessons = new ArrayList<>();
            for (Map<String, Object> lesson : lessonDetails) {
                lessons.add(Maps.str(lesson, "title"));
                if (Boolean.TRUE.equals(lesson.get("completed"))) {
                    completedCount++;
                    completed++;
                }
            }
            course.put("progress", lessonDetails.isEmpty() ? 0 : (int) Math.round((completedCount * 100.0) / lessonDetails.size()));
            course.put("completed", completedCount == lessonDetails.size() && !lessonDetails.isEmpty());
            course.put("lessons", lessons);
        }
        completedLessons = completed;
        totalLessons = total;
        if (completedLessons >= 5) {
            for (Map<String, Object> badge : badges) {
                if ("2".equals(Maps.str(badge, "id"))) {
                    badge.put("earned", Boolean.TRUE);
                }
            }
        }
    }

    private String nextOrderId() {
        return "order-" + String.format("%04d", nextOrderSeq++);
    }

    private int priceToKrw(Map<String, Object> stock, double amount) {
        if ("foreign".equals(Maps.str(stock, "type"))) {
            return (int) Math.round(amount * exchangeRate);
        }
        return (int) Math.round(amount);
    }

    private void applyBuy(Map<String, Object> stock, double price, int quantity) {
        Map<String, Object> holding = findHoldingRef(stock);
        if (holding != null) {
            int totalQty = Maps.intVal(holding, "quantity") + quantity;
            double newAvg = ((Maps.doubleVal(holding, "avgPrice") * Maps.intVal(holding, "quantity")) + (price * quantity)) / totalQty;
            holding.put("avgPrice", Math.round(newAvg * 100.0) / 100.0);
            holding.put("quantity", totalQty);
            holding.put("currentPrice", price);
        } else {
            holdings.add(mapOf(
                    "id", Maps.str(stock, "id"),
                    "name", Maps.str(stock, "name"),
                    "code", Maps.str(stock, "code"),
                    "quantity", quantity,
                    "avgPrice", price,
                    "currentPrice", price,
                    "type", Maps.str(stock, "type"),
                    "folderId", null
            ));
        }
        appendIfMissing(tradingStockIds, Maps.str(stock, "id"));
    }

    private void applySell(Map<String, Object> stock, double price, int quantity) {
        Map<String, Object> holding = findHoldingRef(stock);
        if (holding == null || Maps.intVal(holding, "quantity") < quantity) {
            throw ApiException.conflict("INSUFFICIENT_HOLDINGS", "보유 수량이 부족합니다.");
        }
        holding.put("quantity", Maps.intVal(holding, "quantity") - quantity);
        holding.put("currentPrice", price);
        if (Maps.intVal(holding, "quantity") == 0) {
            holdings.removeIf(item -> Maps.str(stock, "code").equals(Maps.str(item, "code")));
        }
        appendIfMissing(tradingStockIds, Maps.str(stock, "id"));
    }

    private void appendIfMissing(List<String> collection, String stockId) {
        if (!collection.contains(stockId)) {
            collection.add(stockId);
        }
    }

    private void syncReservedIds() {
        List<String> pending = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            if ("pending".equals(Maps.str(order, "status"))) {
                String stockId = Maps.str(order, "stockId");
                if (!pending.contains(stockId)) {
                    pending.add(stockId);
                }
            }
        }
        reservedStockIds = pending;
    }

    private Map<String, Object> findHoldingRef(Map<String, Object> stock) {
        for (Map<String, Object> holding : holdings) {
            if (Maps.str(stock, "code").equals(Maps.str(holding, "code"))) {
                return holding;
            }
        }
        return null;
    }

    private Map<String, Object> findPortfolioRef(String portfolioId) {
        for (Map<String, Object> item : portfolios) {
            if (portfolioId.equals(Maps.str(item, "id"))) {
                return item;
            }
        }
        return null;
    }

    private Map<String, Object> findFolderRef(String folderId) {
        for (Map<String, Object> item : folders) {
            if (folderId.equals(Maps.str(item, "id"))) {
                return item;
            }
        }
        return null;
    }

    private Map<String, Object> findOrderRef(String orderId) {
        for (Map<String, Object> item : orders) {
            if (orderId.equals(Maps.str(item, "orderId"))) {
                return item;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        List<Object> raw = (List<Object>) value;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : raw) {
            rows.add((Map<String, Object>) Json.deepCopy(item));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        List<String> rows = new ArrayList<>();
        if (value == null) {
            return rows;
        }
        for (Object item : (List<Object>) value) {
            rows.add(String.valueOf(item));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> copyMapList(List<Map<String, Object>> list) {
        return (List<Map<String, Object>>) Json.deepCopy(list);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyMap(Map<String, Object> map) {
        return (Map<String, Object>) Json.deepCopy(map);
    }

    private static Map<String, Object> mapOf(Object... items) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i += 2) {
            map.put(String.valueOf(items[i]), items[i + 1]);
        }
        return map;
    }
}
