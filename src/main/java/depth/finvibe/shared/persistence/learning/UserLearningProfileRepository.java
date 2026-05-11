package depth.finvibe.shared.persistence.learning;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLearningProfileRepository extends JpaRepository<UserLearningProfileEntity, String> {
    List<UserLearningProfileEntity> findAllByOrderByTotalXpDescUpdatedAtAsc();
}
