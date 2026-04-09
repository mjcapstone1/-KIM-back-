package depth.finvibe.investment.modules.portfolio.application.service;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final depth.finvibe.shared.persistence.investment.PortfolioStockRepository portfolioStockRepository;
    private final FolderRepository folderRepository;
    private final AssetRepository assetRepository;
    private final StockQueryService stockQueryService;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            depth.finvibe.shared.persistence.investment.PortfolioStockRepository portfolioStockRepository,
            FolderRepository folderRepository,
            AssetRepository assetRepository,
            StockQueryService stockQueryService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioStockRepository = portfolioStockRepository;
        this.folderRepository = folderRepository;
        this.assetRepository = assetRepository;
        this.stockQueryService = stockQueryService;
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
        entity.setPortfolioId(nextPortfolioId(userId));
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
        entity.setFolderId(nextFolderId(userId));
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
            row.put("name", stock.getNameKr());
            row.put("code", stock.getSymbol());
            row.put("quantity", asset.getQuantity().stripTrailingZeros());
            row.put("avgPrice", avgPrice);
            row.put("currentPrice", Math.round(currentPrice * 100.0) / 100.0);
            row.put("currentValue", Math.round(currentValue * 100.0) / 100.0);
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

    private Map<String, Object> toPortfolioMap(PortfolioEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getPortfolioId());
        row.put("name", entity.getName());
        row.put("stocks", portfolioStockRepository.findAllByIdPortfolioId(entity.getPortfolioId())
                .stream()
                .map(item -> item.getId().getStockId())
                .toList());
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
        for (String stockId : stocks) {
            String canonical = stockQueryService.requireStockEntity(stockId).getStockId();
            PortfolioStockEntity row = new PortfolioStockEntity();
            row.setId(new PortfolioStockId(portfolioId, canonical));
            portfolioStockRepository.save(row);
        }
    }

    private String nextPortfolioId(String userId) {
        int next = portfolioRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(PortfolioEntity::getPortfolioId)
                .filter(id -> id.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
        return String.valueOf(next);
    }

    private String nextFolderId(String userId) {
        int next = folderRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(FolderEntity::getFolderId)
                .filter(id -> id.matches("folder-\\d+"))
                .map(id -> Integer.parseInt(id.substring("folder-".length())))
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
        return "folder-" + next;
    }
}
