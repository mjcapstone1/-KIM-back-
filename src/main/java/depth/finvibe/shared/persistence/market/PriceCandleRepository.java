package depth.finvibe.shared.persistence.market;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceCandleRepository extends JpaRepository<PriceCandleEntity, Long> {
    Optional<PriceCandleEntity> findByStockIdAndTimeframeAndCandleAt(String stockId, String timeframe, LocalDateTime candleAt);

    List<PriceCandleEntity> findByStockIdAndTimeframeOrderByCandleAtAsc(String stockId, String timeframe);

    Optional<PriceCandleEntity> findTopByStockIdAndTimeframeOrderByCandleAtDesc(String stockId, String timeframe);

    long countByStockIdAndTimeframe(String stockId, String timeframe);
}
