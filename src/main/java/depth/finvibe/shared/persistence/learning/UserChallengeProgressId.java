package depth.finvibe.shared.persistence.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserChallengeProgressId implements Serializable {
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "challenge_id", nullable = false, length = 64)
    private String challengeId;

    public UserChallengeProgressId() {}

    public UserChallengeProgressId(String userId, String challengeId) {
        this.userId = userId;
        this.challengeId = challengeId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserChallengeProgressId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(challengeId, that.challengeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, challengeId);
    }
}
