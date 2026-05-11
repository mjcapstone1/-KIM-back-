package depth.finvibe.shared.persistence.learning;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSquadMembershipRepository extends JpaRepository<UserSquadMembershipEntity, String> {
    long countBySquadId(String squadId);
    List<UserSquadMembershipEntity> findAllBySquadId(String squadId);
}
