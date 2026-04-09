package depth.finvibe.shared.persistence.trade;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeExecutionRepository extends JpaRepository<TradeExecutionEntity, String> {
}
