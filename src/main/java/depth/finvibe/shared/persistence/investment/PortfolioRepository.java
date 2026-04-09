package depth.finvibe.shared.persistence.investment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, String> {
    List<PortfolioEntity> findAllByUserIdOrderByCreatedAtAsc(String userId);
    Optional<PortfolioEntity> findByPortfolioIdAndUserId(String portfolioId, String userId);
}
