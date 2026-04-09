package depth.finvibe.shared.persistence.investment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLedgerRepository extends JpaRepository<WalletLedgerEntity, Long> {
    List<WalletLedgerEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);
}
