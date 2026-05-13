package depth.finvibe.investment.modules.portfolio.application.service;

import depth.finvibe.investment.modules.wallet.application.service.WalletService;
import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.market.StockQueryService;
import depth.finvibe.shared.persistence.investment.AssetEntity;
import depth.finvibe.shared.persistence.investment.AssetRepository;
import depth.finvibe.shared.persistence.investment.FolderEntity;
import depth.finvibe.shared.persistence.investment.FolderRepository;
import depth.finvibe.shared.persistence.investment.PortfolioEntity;
import depth.finvibe.shared.persistence.investment.PortfolioRepository;
import depth.finvibe.shared.persistence.investment.PortfolioStockEntity;
import depth.finvibe.shared.persistence.investment.PortfolioStockId;
import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.util.Maps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final depth.finvibe.shared.persistence.investment.PortfolioStockRepository portfolioStockRepository;
    private final FolderRepository folderRepository;
    private final AssetRepository assetRepository;
    private final StockQueryService stockQueryService;
    private final WalletService walletService;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            depth.finvibe.shared.persistence.investment.PortfolioStockRepository portfolioStockRepository,
            FolderRepository folderRepository,
            AssetRepository assetRepository,
            StockQueryService stockQueryService,
            WalletService walletService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioStockRepository = portfolioStockRepository;
        this.folderRepository = folderRepository;
        this.assetRepository = assetRepository;
        this.stockQueryService = stockQueryService;
        this.walletService = walletService;
    }

    public List<Map<String, Object>> listPortfolios(String userId) {
        return portfolioRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(this::toPortfolioMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> createPortfolio(String userId, String name, List<String> stocks) {
        PortfolioEntity entity = new PortfolioEntity();
        entity.setPortfolioId(newPortfolioId());
        entity.setUserId(userId);
        entity.setName(name);
        portfolioRepository.save(entity);
        replacePortfolioStocks(entity.getPortfolioId(), stocks);
        return toPortfolioMap(entity);
    }

    @Transactional
    public Map<String, Object> updatePortfolio(String userId, String portfolioId, String name, List<String> stocks) {
        PortfolioEntity entity = portfolioRepository.findByPortfolioIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> ApiException.notFound("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + portfolioId));
        if (name != null) {
            entity.setName(name);
        }
        portfolioRepository.save(entity);
        if (stocks != null) {
            replacePortfolioStocks(entity.getPortfolioId(), stocks);
        }
        return toPortfolioMap(entity);
    }

    @Transactional
    public void deletePortfolio(String userId, String portfolioId) {
        PortfolioEntity entity = portfolioRepository.findByPortfolioIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> ApiException.notFound("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + portfolioId));
        portfolioRepository.delete(entity);
    }

    public List<Map<String, Object>> listFolders(String userId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FolderEntity folder : folderRepository.findAllByUserIdOrderByCreatedAtAsc(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", folder.getFolderId());
            row.put("name", folder.getName());
            row.put("color", folder.getColor());
            row.put("stockCount", assetRepository.countByUserIdAndFolderId(userId, folder.getFolderId()));
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> createFolder(String userId, String name, String color) {
        FolderEntity entity = new FolderEntity();
        entity.setFolderId(newFolderId());
        entity.setUserId(userId);
        entity.setName(name);
        entity.setColor(color == null ? "#3b82f6" : color);
        folderRepository.save(entity);
        return toFolderMap(entity, 0);
    }

    @Transactional
    public Map<String, Object> updateFolder(String userId, String folderId, String name, String color) {
        FolderEntity entity = folderRepository.findByFolderIdAndUserId(folderId, userId)
                .orElseThrow(() -> ApiException.notFound("FOLDER_NOT_FOUND", "폴더를 찾을 수 없습니다: " + folderId));
        if (name != null) {
            entity.setName(name);
        }
        if (color != null) {
            entity.setColor(color);
        }
        folderRepository.save(entity);
        return toFolderMap(entity, assetRepository.countByUserIdAndFolderId(userId, folderId));
    }

    @Transactional
    public void deleteFolder(String userId, String folderId) {
        FolderEntity entity = folderRepository.findByFolderIdAndUserId(folderId, userId)
                .orElseThrow(() -> ApiException.notFound("FOLDER_NOT_FOUND", "폴더를 찾을 수 없습니다: " + folderId));
        for (AssetEntity asset : assetRepository.findAllByUserIdAndFolderIdOrderByCreatedAtAsc(userId, folderId)) {
            asset.setFolderId(null);
            assetRepository.save(asset);
        }
        folderRepository.delete(entity);
    }

    public List<Map<String, Object>> listHoldings(String userId, String folderId) {
        List<AssetEntity> assets = folderId == null
                ? assetRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
                : assetRepository.findAllByUserIdAndFolderIdOrderByCreatedAtAsc(userId, folderId);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetEntity asset : assets) {
            if (asset.getQuantity().doubleValue() <= 0) {
                continue;
            }
            StockEntity stock = stockQueryService.requireStockEntity(asset.getStockId());
            Map<String, Object> snapshot = stockQueryService.stockSnapshot(stock);
            double currentPrice = Maps.doubleVal(snapshot, "price");
            double quantity = asset.getQuantity().doubleValue();
            long avgPrice = asset.getAvgBuyPriceKrw();
            double currentValue = currentPrice * quantity;
            double investedAmount = avgPrice * quantity;
            double profitRate = investedAmount == 0 ? 0.0 : ((currentValue - investedAmount) / investedAmount) * 100.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", stock.getStockId());
            row.put("stockId", stock.getStockId());
            row.put("name", stock.getNameKr());
            row.put("code", stock.getSymbol());
            row.put("quantity", asset.getQuantity().stripTrailingZeros());
            row.put("amount", asset.getQuantity().stripTrailingZeros());
            row.put("avgPrice", avgPrice);
            row.put("currentPrice", Math.round(currentPrice * 100.0) / 100.0);
            row.put("currentValue", Math.round(currentValue * 100.0) / 100.0);
            row.put("totalPrice", asset.getInvestedAmountKrw());
            row.put("currency", "KRW");
            row.put("profitRate", Math.round(profitRate * 100.0) / 100.0);
            row.put("type", stock.getStockType());
            row.put("folderId", asset.getFolderId());
            rows.add(row);
        }
        return rows;
    }

    public List<Map<String, Object>> portfolioAssets(String userId, String portfolioId) {
        PortfolioEntity portfolio = portfolioRepository.findByPortfolioIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> ApiException.notFound("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + portfolioId));
        List<String> stockIds = portfolioStockRepository.findAllByIdPortfolioId(portfolio.getPortfolioId())
                .stream()
                .map(item -> item.getId().getStockId())
                .toList();
        return listHoldings(userId, null).stream()
                .filter(item -> stockIds.contains(String.valueOf(item.get("id"))))
                .toList();
    }

    public List<Map<String, Object>> listTopHoldingStocks(String userId, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetEntity asset : assetRepository.findAllByUserIdOrderByCreatedAtAsc(userId)) {
            if (asset.getQuantity().doubleValue() <= 0) {
                continue;
            }
            StockEntity stock = stockQueryService.requireStockEntity(asset.getStockId());
            double currentPrice = stock.getLastPrice() == null
                    ? asset.getCurrentPriceKrw()
                    : stock.getLastPrice().doubleValue();
            double totalAmount = currentPrice * asset.getQuantity().doubleValue();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockId", stock.getStockId());
            row.put("name", stock.getNameKr());
            row.put("totalAmount", Math.round(totalAmount));
            rows.add(row);
        }

        rows.sort((left, right) -> Long.compare(
                Maps.longVal(right.get("totalAmount"), 0),
                Maps.longVal(left.get("totalAmount"), 0)
        ));

        if (rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    public List<Map<String, Object>> listPortfolioComparison(String userId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PortfolioEntity portfolio : portfolioRepository.findAllByUserIdOrderByCreatedAtAsc(userId)) {
            List<String> stockIds = portfolioStockRepository.findAllByIdPortfolioId(portfolio.getPortfolioId())
                    .stream()
                    .map(item -> item.getId().getStockId())
                    .toList();
            double totalCurrentValue = 0.0;
            long totalPurchaseAmount = 0L;
            long realizedProfit = 0L;
            for (String stockId : stockIds) {
                AssetEntity asset = assetRepository.findByUserIdAndStockId(userId, stockId).orElse(null);
                if (asset == null || asset.getQuantity().doubleValue() <= 0) {
                    continue;
                }
                totalCurrentValue += currentValue(asset);
                totalPurchaseAmount += asset.getInvestedAmountKrw();
                realizedProfit += asset.getRealizedPnlKrw();
            }
            double returnRate = totalPurchaseAmount == 0L
                    ? 0.0
                    : ((totalCurrentValue - totalPurchaseAmount) / totalPurchaseAmount) * 100.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", portfolio.getName());
            row.put("totalAssetAmount", Math.round(totalCurrentValue));
            row.put("returnRate", round2(returnRate));
            row.put("realizedProfit", realizedProfit);
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> getAssetAllocation(String userId) {
        long cashAmount = Maps.longVal(walletService.getWalletSummary(userId).get("balance"), 0L);
        double stockAmount = 0.0;
        long investedAmount = 0L;
        for (AssetEntity asset : assetRepository.findAllByUserIdOrderByCreatedAtAsc(userId)) {
            if (asset.getQuantity().doubleValue() <= 0) {
                continue;
            }
            stockAmount += currentValue(asset);
            investedAmount += asset.getInvestedAmountKrw();
        }
        double changeAmount = stockAmount - investedAmount;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cashAmount", cashAmount);
        row.put("stockAmount", Math.round(stockAmount));
        row.put("totalAmount", Math.round(cashAmount + stockAmount));
        row.put("changeAmount", Math.round(changeAmount));
        row.put("changeRate", investedAmount == 0L ? 0.0 : round2((changeAmount / investedAmount) * 100.0));
        return row;
    }

    @Transactional
    public Map<String, Object> transferPortfolioAsset(String userId, String sourcePortfolioId, String stockId, String targetPortfolioId) {
        PortfolioEntity source = portfolioRepository.findByPortfolioIdAndUserId(sourcePortfolioId, userId)
                .orElseThrow(() -> ApiException.notFound("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + sourcePortfolioId));
        PortfolioEntity target = portfolioRepository.findByPortfolioIdAndUserId(targetPortfolioId, userId)
                .orElseThrow(() -> ApiException.notFound("PORTFOLIO_NOT_FOUND", "포트폴리오를 찾을 수 없습니다: " + targetPortfolioId));
        String canonicalStockId = stockQueryService.requireStockEntity(stockId).getStockId();
        boolean inSource = portfolioStockRepository.findAllByIdPortfolioId(source.getPortfolioId())
                .stream()
                .anyMatch(item -> canonicalStockId.equals(item.getId().getStockId()));
        if (!inSource) {
            throw ApiException.notFound("PORTFOLIO_ASSET_NOT_FOUND", "포트폴리오에 해당 종목이 없습니다.");
        }

        portfolioStockRepository.deleteById(new PortfolioStockId(source.getPortfolioId(), canonicalStockId));

        boolean inTarget = portfolioStockRepository.findAllByIdPortfolioId(target.getPortfolioId())
                .stream()
                .anyMatch(item -> canonicalStockId.equals(item.getId().getStockId()));
        if (!inTarget) {
            PortfolioStockEntity entity = new PortfolioStockEntity();
            entity.setId(new PortfolioStockId(target.getPortfolioId(), canonicalStockId));
            portfolioStockRepository.save(entity);
        }

        return Maps.of(
                "sourcePortfolioId", source.getPortfolioId(),
                "targetPortfolioId", target.getPortfolioId(),
                "stockId", canonicalStockId
        );
    }

    private Map<String, Object> toPortfolioMap(PortfolioEntity entity) {
        List<String> stockIds = portfolioStockRepository.findAllByIdPortfolioId(entity.getPortfolioId())
                .stream()
                .map(item -> item.getId().getStockId())
                .toList();
        double totalCurrentValue = 0.0;
        long totalPurchaseAmount = 0L;
        for (String stockId : stockIds) {
            AssetEntity asset = assetRepository.findByUserIdAndStockId(entity.getUserId(), stockId).orElse(null);
            if (asset == null || asset.getQuantity().doubleValue() <= 0) {
                continue;
            }
            totalCurrentValue += currentValue(asset);
            totalPurchaseAmount += asset.getInvestedAmountKrw();
        }
        double totalReturnRate = totalPurchaseAmount == 0L
                ? 0.0
                : ((totalCurrentValue - totalPurchaseAmount) / totalPurchaseAmount) * 100.0;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getPortfolioId());
        row.put("name", entity.getName());
        row.put("stocks", stockIds);
        row.put("iconCode", "DEFAULT");
        row.put("totalPurchaseAmount", totalPurchaseAmount);
        row.put("totalCurrentValue", Math.round(totalCurrentValue));
        row.put("totalReturnRate", round2(totalReturnRate));
        return row;
    }

    private Map<String, Object> toFolderMap(FolderEntity entity, long stockCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getFolderId());
        row.put("name", entity.getName());
        row.put("color", entity.getColor());
        row.put("stockCount", stockCount);
        return row;
    }

    private void replacePortfolioStocks(String portfolioId, List<String> stocks) {
        portfolioStockRepository.deleteAllByIdPortfolioId(portfolioId);
        if (stocks == null) {
            return;
        }
        Set<String> uniqueStockIds = new LinkedHashSet<>();
        for (String stockId : stocks) {
            if (stockId == null || stockId.isBlank()) {
                continue;
            }
            uniqueStockIds.add(stockQueryService.requireStockEntity(stockId.trim()).getStockId());
        }
        for (String stockId : uniqueStockIds) {
            PortfolioStockEntity row = new PortfolioStockEntity();
            row.setId(new PortfolioStockId(portfolioId, stockId));
            portfolioStockRepository.save(row);
        }
    }

    private String newPortfolioId() {
        return "portfolio-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String newFolderId() {
        return "folder-" + UUID.randomUUID().toString().replace("-", "");
    }

    private double currentValue(AssetEntity asset) {
        if (asset.getQuantity().doubleValue() <= 0) {
            return 0.0;
        }
        StockEntity stock = stockQueryService.requireStockEntity(asset.getStockId());
        Map<String, Object> snapshot = stockQueryService.stockSnapshot(stock);
        return Maps.doubleVal(snapshot, "price") * asset.getQuantity().doubleValue();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
