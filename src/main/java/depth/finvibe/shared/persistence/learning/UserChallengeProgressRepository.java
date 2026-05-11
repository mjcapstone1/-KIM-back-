package depth.finvibe.shared.persistence.learning;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserChallengeProgressRepository extends JpaRepository<UserChallengeProgressEntity, UserChallengeProgressId> {
    List<UserChallengeProgressEntity> findAllByIdUserIdOrderByUpdatedAtDesc(String userId);
    List<UserChallengeProgressEntity> findAllByIdUserIdAndStatusOrderByUpdatedAtDesc(String userId, String status);
    Optional<UserChallengeProgressEntity> findByIdUserIdAndIdChallengeId(String userId, String challengeId);
}
