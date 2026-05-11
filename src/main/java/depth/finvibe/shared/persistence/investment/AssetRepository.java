package depth.finvibe.shared.persistence.investment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<AssetEntity, Long> {
    List<AssetEntity> findAllByUserIdOrderByCreatedAtAsc(String userId);
    List<AssetEntity> findAllByUserIdAndFolderIdOrderByCreatedAtAsc(String userId, String folderId);
    Optional<AssetEntity> findByUserIdAndStockId(String userId, String stockId);
    long countByUserIdAndFolderId(String userId, String folderId);
}
