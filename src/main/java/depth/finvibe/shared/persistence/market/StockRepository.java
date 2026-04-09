package depth.finvibe.shared.persistence.market;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<StockEntity, String> {
    Optional<StockEntity> findBySymbol(String symbol);
    Optional<StockEntity> findByNameKrIgnoreCase(String nameKr);
}
