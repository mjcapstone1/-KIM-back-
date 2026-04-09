package depth.finvibe.gamification.modules.gamification.api.external;

import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChallengeController {
    private final AppState state;

    public ChallengeController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/learning/challenges")
    public Object challenges() {
        return Maps.of("items", state.listChallenges());
    }

    @GetMapping("/challenges/me")
    public Object challengesAlias() {
        return Maps.of("items", state.listChallenges());
    }

    @GetMapping("/api/v1/learning/challenges/completed")
    public Object completedChallenges() {
        return Maps.of("items", state.listCompletedChallenges());
    }

    @GetMapping("/challenges/completed")
    public Object completedChallengesAlias() {
        return Maps.of("items", state.listCompletedChallenges());
    }
}
