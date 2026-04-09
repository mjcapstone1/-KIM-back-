package depth.finvibe.shared.persistence.user;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByTokenAndRevokedAtIsNull(String token);
    void deleteByUserId(String userId);
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
