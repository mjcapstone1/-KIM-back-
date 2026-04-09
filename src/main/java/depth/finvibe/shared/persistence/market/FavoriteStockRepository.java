package depth.finvibe.shared.persistence.market;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteStockRepository extends JpaRepository<FavoriteStockEntity, FavoriteStockId> {
    List<FavoriteStockEntity> findAllByIdUserId(String userId);
    boolean existsByIdUserIdAndIdStockId(String userId, String stockId);
    void deleteByIdUserIdAndIdStockId(String userId, String stockId);
}
