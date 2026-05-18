package depth.finvibe.shared.market;

import depth.finvibe.shared.persistence.market.ClosingPriceEntity;
import depth.finvibe.shared.persistence.market.ClosingPriceRepository;
import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClosingPriceCaptureScheduler {
    private static final Logger log = LoggerFactory.getLogger(ClosingPriceCaptureScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalTime DEFAULT_CAPTURE_TIME = LocalTime.of(15, 31);

    private final StockRepository stockRepository;
    private final ClosingPriceRepository closingPriceRepository;

    @Value("${finvibe.market.capture-close-recovery-enabled:true}")
    private boolean recoveryEnabled;

    public ClosingPriceCaptureScheduler(
            StockRepository stockRepository,
            ClosingPriceRepository closingPriceRepository
    ) {
        this.stockRepository = stockRepository;
        this.closingPriceRepository = closingPriceRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverMissedClosingPricesOnStartup() {
        if (!recoveryEnabled) {
            return;
        }

        LocalDate today = LocalDate.now(SEOUL);
        if (!isTradingDay(today)) {
            return;
        }

        if (LocalTime.now(SEOUL).isBefore(DEFAULT_CAPTURE_TIME)) {
            return;
        }

        List<StockEntity> stocks = stockRepository.findByActiveTrueAndStockTypeOrderByNameKrAsc("domestic");
        long expectedRows = stocks.stream()
                .filter(this::hasCapturePrice)
                .count();
        long capturedRows = closingPriceRepository.countByTradeDate(today);

        if (expectedRows == 0 || capturedRows >= expectedRows) {
            return;
        }

        log.info("누락된 종가 캡처 복구를 시작합니다. tradeDate={}, capturedRows={}, expectedRows={}",
                today, capturedRows, expectedRows);
        captureClosingPrices(today, stocks, "startup-recovery");
    }

    @Scheduled(cron = "${finvibe.market.capture-close-cron:0 31 15 * * MON-FRI}", zone = "Asia/Seoul")
    @Transactional
    public void captureClosingPrices() {
        LocalDate today = LocalDate.now(SEOUL);
        List<StockEntity> stocks = stockRepository.findByActiveTrueAndStockTypeOrderByNameKrAsc("domestic");
        captureClosingPrices(today, stocks, "schedule");
    }

    private void captureClosingPrices(LocalDate tradeDate, List<StockEntity> stocks, String trigger) {
        int eligible = 0;
        int saved = 0;

        for (StockEntity stock : stocks) {
            if (!hasCapturePrice(stock)) {
                continue;
            }

            eligible++;
            ClosingPriceEntity row = closingPriceRepository
                    .findByStockIdAndTradeDate(stock.getStockId(), tradeDate)
                    .orElseGet(ClosingPriceEntity::new);

            row.setStockId(stock.getStockId());
            row.setTradeDate(tradeDate);
            row.setClosePrice(stock.getLastPrice());
            row.setPrevClosePrice(resolvePreviousClosePrice(stock, tradeDate));
            row.setChangeRate(stock.getLastChangeRate() == null ? BigDecimal.ZERO : stock.getLastChangeRate());
            row.setVolume(stock.getLastVolume());
            row.setTradingValueKrw(stock.getLastTradeValueKrw());

            closingPriceRepository.save(row);
            saved++;
        }

        log.info("종가 캡처 완료 trigger={}, tradeDate={}, eligible={}, saved={}",
                trigger, tradeDate, eligible, saved);
    }

    private boolean hasCapturePrice(StockEntity stock) {
        return stock.getLastPrice() != null && stock.getLastPrice().doubleValue() > 0;
    }

    private BigDecimal resolvePreviousClosePrice(StockEntity stock, LocalDate tradeDate) {
        Optional<ClosingPriceEntity> previousRow =
                closingPriceRepository.findTopByStockIdAndTradeDateLessThanOrderByTradeDateDesc(stock.getStockId(), tradeDate);
        return previousRow
                .map(ClosingPriceEntity::getClosePrice)
                .orElse(stock.getLastPrice());
    }

    private boolean isTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}
