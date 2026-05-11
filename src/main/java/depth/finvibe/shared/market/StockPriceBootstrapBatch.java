package depth.finvibe.shared.market;

import depth.finvibe.shared.persistence.market.StockEntity;
import depth.finvibe.shared.persistence.market.StockRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockPriceBootstrapBatch {
    private static final Logger log = LoggerFactory.getLogger(StockPriceBootstrapBatch.class);
    private static final int EXCHANGE_RATE = 1300;

    private final StockRepository stockRepository;
    private final StockQueryService stockQueryService;
    private final AtomicBoolean startupExecuted = new AtomicBoolean(false);

    @Value("${finvibe.market.bootstrap-prices.enabled:true}")
    private boolean enabled;

    @Value("${finvibe.market.bootstrap-prices.run-on-startup:false}")
    private boolean runOnStartup;

    @Value("${finvibe.market.bootstrap-prices.schedule-enabled:false}")
    private boolean scheduleEnabled;

    @Value("${finvibe.market.bootstrap-prices.page-size:50}")
    private int pageSize;

    @Value("${finvibe.market.bootstrap-prices.max-pages-per-run:4}")
    private int maxPagesPerRun;

    @Value("${finvibe.market.bootstrap-prices.request-delay-ms:150}")
    private long requestDelayMs;

    @Value("${finvibe.market.bootstrap-prices.refresh-zero-only:true}")
    private boolean refreshZeroOnly;

    public StockPriceBootstrapBatch(StockRepository stockRepository, StockQueryService stockQueryService) {
        this.stockRepository = stockRepository;
        this.stockQueryService = stockQueryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        if (!enabled || !runOnStartup || !startupExecuted.compareAndSet(false, true)) {
            return;
        }
        bootstrapActiveDomesticPrices("startup");
    }

    @Scheduled(cron = "${finvibe.market.bootstrap-prices.cron:0 0/20 9-15 * * MON-FRI}", zone = "Asia/Seoul")
    public void bootstrapOnSchedule() {
        if (!enabled || !scheduleEnabled) {
            return;
        }
        bootstrapActiveDomesticPrices("schedule");
    }

    public void bootstrapActiveDomesticPrices(String trigger) {
        int processed = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        Sort sort = Sort.by(Sort.Order.asc("marketSegment"), Sort.Order.asc("symbol"));

        for (int pageNumber = 0; pageNumber < Math.max(1, maxPagesPerRun); pageNumber++) {
            Pageable pageable = PageRequest.of(pageNumber, Math.max(1, pageSize), sort);
            Page<StockEntity> page = stockRepository.findByActiveTrueAndStockTypeAndMarket(
                    "domestic",
                    "KRX",
                    pageable
            );

            if (page.isEmpty()) {
                break;
            }

            for (StockEntity stock : page.getContent()) {
                if (!shouldRefresh(stock)) {
                    continue;
                }

                processed++;
                try {
                    Map<String, Object> snapshot = stockQueryService.stockSnapshot(stock);
                    String source = String.valueOf(snapshot.getOrDefault("dataSource", "unknown"));
                    double price = toDouble(snapshot.get("price"));
                    double changeRate = toDouble(snapshot.get("changeRate"));

                    if (!"kis".equals(source) || price <= 0) {
                        skipped++;
                        continue;
                    }

                    updateLastPrice(stock.getStockId(), price, changeRate);
                    updated++;
                } catch (Exception e) {
                    failed++;
                    log.warn("가격 초기화 실패 stockId={}, symbol={}, message={}",
                            stock.getStockId(), stock.getSymbol(), e.getMessage());
                }

                if (requestDelayMs > 0) {
                    LockSupport.parkNanos(Duration.ofMillis(requestDelayMs).toNanos());
                }
            }

            if (!page.hasNext()) {
                break;
            }
        }

        log.info("KRX 가격 초기화 완료 trigger={}, processed={}, updated={}, skipped={}, failed={}, refreshZeroOnly={}",
                trigger, processed, updated, skipped, failed, refreshZeroOnly);
    }

    @Transactional
    protected void updateLastPrice(String stockId, double price, double changeRate) {
        StockEntity entity = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("종목이 없습니다: " + stockId));
        entity.setLastPrice(BigDecimal.valueOf(price));
        entity.setLastChangeRate(BigDecimal.valueOf(changeRate));
        stockRepository.save(entity);
    }

    private boolean shouldRefresh(StockEntity stock) {
        if (stock == null || !stock.isActive()) {
            return false;
        }
        if (!"domestic".equalsIgnoreCase(stock.getStockType())) {
            return false;
        }
        if (!"KRX".equalsIgnoreCase(stock.getMarket())) {
            return false;
        }
        if (!refreshZeroOnly) {
            return true;
        }
        return stock.getLastPrice() == null || stock.getLastPrice().compareTo(BigDecimal.ZERO) <= 0;
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0.0;
        }
    }
}
