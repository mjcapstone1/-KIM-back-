package depth.finvibe.shared.persistence.market;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingPriceRepository extends JpaRepository<ClosingPriceEntity, Long> {
    Optional<ClosingPriceEntity> findByStockIdAndTradeDate(String stockId, LocalDate tradeDate);
    Optional<ClosingPriceEntity> findTopByStockIdOrderByTradeDateDesc(String stockId);
}
