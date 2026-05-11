package depth.finvibe.shared.persistence.learning;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCourseRepository extends JpaRepository<UserCourseEntity, UserCourseId> {
    long countByIdUserId(String userId);
    List<UserCourseEntity> findAllByIdUserIdOrderBySortOrderAsc(String userId);
    Optional<UserCourseEntity> findByIdUserIdAndIdCourseId(String userId, String courseId);
}
