package depth.finvibe.shared.market;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import depth.finvibe.shared.util.FinvibeUtils;
import depth.finvibe.shared.util.Maps;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StockQueryService {
    private final StockRepository stockRepository;
    private final MarketService marketService;
    private static final int EXCHANGE_RATE = 1300;

    public StockQueryService(StockRepository stockRepository, MarketService marketService) {
        this.stockRepository = stockRepository;
        this.marketService = marketService;
    }

    public StockEntity requireStockEntity(String identifier) {
        return stockRepository.findById(identifier)
                .or(() -> stockRepository.findBySymbol(identifier))
                .or(() -> stockRepository.findByNameKrIgnoreCase(identifier))
                .orElseThrow(() -> ApiException.notFound("STOCK_NOT_FOUND", "종목을 찾을 수 없습니다: " + identifier));
    }

    public Map<String, Object> toMarketStock(StockEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getStockId());
        row.put("code", entity.getSymbol());
        row.put("name", entity.getNameKr());
        row.put("nameEn", entity.getNameEn());
        row.put("type", entity.getStockType());
        row.put("currency", entity.getCurrency());
        row.put("countryCode", entity.getCountryCode());
        row.put("price", entity.getLastPrice() == null ? 0.0 : entity.getLastPrice().doubleValue());
        row.put("changeRate", entity.getLastChangeRate() == null ? 0.0 : entity.getLastChangeRate().doubleValue());
        row.put("volume", 0);
        return row;
    }

    public Map<String, Object> resolveStock(String identifier) {
        return toMarketStock(requireStockEntity(identifier));
    }

    public Map<String, Object> stockSnapshot(String identifier) {
        return marketService.getStockSnapshot(resolveStock(identifier), EXCHANGE_RATE);
    }

    public Map<String, Object> stockSnapshot(StockEntity entity) {
        return marketService.getStockSnapshot(toMarketStock(entity), EXCHANGE_RATE);
    }

    public long resolvePriceKrw(StockEntity entity, double price) {
        if ("foreign".equals(Maps.str(toMarketStock(entity), "type"))) {
            return Math.round(price * EXCHANGE_RATE);
        }
        return Math.round(price);
    }

    public double roundPrice(StockEntity entity, double price) {
        return FinvibeUtils.roundStockPrice(price, entity.getStockType());
    }
}
