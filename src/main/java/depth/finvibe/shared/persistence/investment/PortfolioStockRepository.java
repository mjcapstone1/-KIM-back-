package depth.finvibe.shared.persistence.investment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioStockRepository extends JpaRepository<PortfolioStockEntity, PortfolioStockId> {
    List<PortfolioStockEntity> findAllByIdPortfolioId(String portfolioId);
    void deleteAllByIdPortfolioId(String portfolioId);
}
