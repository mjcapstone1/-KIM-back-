package depth.finvibe.shared.persistence.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeOrderRepository extends JpaRepository<TradeOrderEntity, String> {
    List<TradeOrderEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);
    Optional<TradeOrderEntity> findByOrderIdAndUserId(String orderId, String userId);
    List<TradeOrderEntity> findAllByOrderStatusOrderByAcceptedAtAscCreatedAtAscOrderIdAsc(String orderStatus, Pageable pageable);
    long countByOrderStatus(String orderStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from TradeOrderEntity o
            where o.orderId = :orderId
              and o.orderStatus = 'pending'
            """)
    Optional<TradeOrderEntity> lockPendingByOrderId(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from TradeOrderEntity o
            where o.orderId = :orderId
              and o.userId = :userId
            """)
    Optional<TradeOrderEntity> lockByOrderIdAndUserId(@Param("orderId") String orderId, @Param("userId") String userId);

    @Query("""
            select coalesce(sum(o.remainingQuantity), 0)
            from TradeOrderEntity o
            where o.userId = :userId
              and o.stockId = :stockId
              and o.side = 'sell'
              and o.orderStatus = 'pending'
              and o.orderId <> :excludedOrderId
            """)
    BigDecimal sumPendingSellRemainingQuantityExcludingOrder(
            @Param("userId") String userId,
            @Param("stockId") String stockId,
            @Param("excludedOrderId") String excludedOrderId
    );

    @Query("""
            select coalesce(sum(o.remainingQuantity), 0)
            from TradeOrderEntity o
            where o.userId = :userId
              and o.stockId = :stockId
              and o.side = 'sell'
              and o.orderStatus = 'pending'
              and o.orderId <> :orderId
              and (
                    o.acceptedAt < :acceptedAt
                 or (o.acceptedAt = :acceptedAt and o.createdAt < :createdAt)
                 or (o.acceptedAt = :acceptedAt and o.createdAt = :createdAt and o.orderId < :orderId)
              )
            """)
    BigDecimal sumEarlierPendingSellRemainingQuantity(
            @Param("userId") String userId,
            @Param("stockId") String stockId,
            @Param("acceptedAt") LocalDateTime acceptedAt,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("orderId") String orderId
    );
}
