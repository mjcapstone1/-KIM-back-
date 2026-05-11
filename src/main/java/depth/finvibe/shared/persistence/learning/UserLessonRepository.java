package depth.finvibe.shared.persistence.learning;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLessonRepository extends JpaRepository<UserLessonEntity, UserLessonId> {
    long countByIdUserId(String userId);
    long countByIdUserIdAndCompletedTrue(String userId);
    List<UserLessonEntity> findAllByIdUserIdOrderByCourseIdAscSortOrderAsc(String userId);
    List<UserLessonEntity> findAllByIdUserIdAndCourseIdOrderBySortOrderAsc(String userId, String courseId);
    Optional<UserLessonEntity> findByIdUserIdAndIdLessonId(String userId, String lessonId);
}
