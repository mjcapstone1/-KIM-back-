package depth.finvibe.shared.persistence.market;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<StockEntity, String> {
    Optional<StockEntity> findBySymbol(String symbol);
    Optional<StockEntity> findByNameKrIgnoreCase(String nameKr);

    @Query(value = """
        SELECT *
        FROM stocks
        WHERE is_active = 1
        ORDER BY name_kr ASC
        """, nativeQuery = true)
    List<StockEntity> findByActiveTrueOrderByNameKrAsc();

    @Query(value = """
        SELECT *
        FROM stocks
        WHERE is_active = 1
          AND stock_type = :stockType
        ORDER BY name_kr ASC
        """, nativeQuery = true)
    List<StockEntity> findByActiveTrueAndStockTypeOrderByNameKrAsc(@Param("stockType") String stockType);

    @Query(value = """
        SELECT *
        FROM stocks
        WHERE is_active = 1
          AND stock_type = :stockType
          AND market_segment = :marketSegment
        ORDER BY name_kr ASC
        """, nativeQuery = true)
    List<StockEntity> findByActiveTrueAndStockTypeAndMarketSegmentOrderByNameKrAsc(
            @Param("stockType") String stockType,
            @Param("marketSegment") String marketSegment
    );

    @Query(
        value = """
            SELECT *
            FROM stocks
            WHERE is_active = 1
              AND stock_type = :stockType
              AND market = :market
            ORDER BY symbol ASC
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM stocks
            WHERE is_active = 1
              AND stock_type = :stockType
              AND market = :market
            """,
        nativeQuery = true
    )
    Page<StockEntity> findByActiveTrueAndStockTypeAndMarket(
            @Param("stockType") String stockType,
            @Param("market") String market,
            Pageable pageable
    );
}
