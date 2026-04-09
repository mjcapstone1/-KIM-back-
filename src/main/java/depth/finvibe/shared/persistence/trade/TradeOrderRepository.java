package depth.finvibe.shared.persistence.trade;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrderEntity, String> {
    List<TradeOrderEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);
    Optional<TradeOrderEntity> findByOrderIdAndUserId(String orderId, String userId);
}
