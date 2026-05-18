package depth.finvibe.shared.persistence.investment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<AssetEntity, Long> {
    List<AssetEntity> findAllByUserIdOrderByCreatedAtAsc(String userId);
    List<AssetEntity> findAllByUserIdAndFolderIdOrderByCreatedAtAsc(String userId, String folderId);
    Optional<AssetEntity> findByUserIdAndStockId(String userId, String stockId);
    long countByUserIdAndFolderId(String userId, String folderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from AssetEntity a
            where a.userId = :userId
              and a.stockId = :stockId
            """)
    Optional<AssetEntity> lockByUserIdAndStockId(@Param("userId") String userId, @Param("stockId") String stockId);
}
