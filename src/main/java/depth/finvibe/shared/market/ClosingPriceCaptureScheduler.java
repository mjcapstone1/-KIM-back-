package depth.finvibe.shared.market;

import depth.finvibe.shared.persistence.market.ClosingPriceEntity;
import depth.finvibe.shared.persistence.market.ClosingPriceRepository;
import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClosingPriceCaptureScheduler {
    private final StockRepository stockRepository;
    private final ClosingPriceRepository closingPriceRepository;

    public ClosingPriceCaptureScheduler(
            StockRepository stockRepository,
            ClosingPriceRepository closingPriceRepository
    ) {
        this.stockRepository = stockRepository;
        this.closingPriceRepository = closingPriceRepository;
    }

    @Scheduled(cron = "${finvibe.market.capture-close-cron:0 31 15 * * MON-FRI}", zone = "Asia/Seoul")
    @Transactional
    public void captureClosingPrices() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<StockEntity> stocks = stockRepository.findByActiveTrueAndStockTypeOrderByNameKrAsc("domestic");

        for (StockEntity stock : stocks) {
            if (stock.getLastPrice() == null || stock.getLastPrice().doubleValue() <= 0) {
                continue;
            }

            ClosingPriceEntity row = closingPriceRepository
                    .findByStockIdAndTradeDate(stock.getStockId(), today)
                    .orElseGet(ClosingPriceEntity::new);

            row.setStockId(stock.getStockId());
            row.setTradeDate(today);
            row.setClosePrice(stock.getLastPrice());
            row.setPrevClosePrice(stock.getLastPrice());
            row.setChangeRate(stock.getLastChangeRate() == null ? BigDecimal.ZERO : stock.getLastChangeRate());
            row.setVolume(0L);

            closingPriceRepository.save(row);
        }
    }
}
