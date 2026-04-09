package depth.finvibe.shared.persistence.ranking;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfitSnapshotDailyRepository extends JpaRepository<UserProfitSnapshotDailyEntity, Long> {
    Optional<UserProfitSnapshotDailyEntity> findByUserIdAndSnapshotDate(String userId, LocalDate snapshotDate);
    Optional<UserProfitSnapshotDailyEntity> findTopByUserIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(String userId, LocalDate snapshotDate);
}