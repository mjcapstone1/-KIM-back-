package depth.finvibe.gamification.modules.gamification.api.external;

import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class XpController {
    private final AppState state;

    public XpController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/learning/xp")
    public Object xp() {
        return state.xpMe();
    }

    @GetMapping("/xp/me")
    public Object xpAlias() {
        return state.xpMe();
    }

    @GetMapping("/api/v1/learning/xp/users/ranking")
    public Object xpUsersRanking() {
        return Maps.of("items", state.xpUserRanking());
    }

    @GetMapping("/xp/users/ranking")
    public Object xpUsersRankingAlias() {
        return Maps.of("items", state.xpUserRanking());
    }

    @GetMapping("/api/v1/learning/xp/squads/ranking")
    public Object xpSquadsRanking() {
        return Maps.of("items", state.xpSquadRanking());
    }

    @GetMapping("/xp/squads/ranking")
    public Object xpSquadsRankingAlias() {
        return Maps.of("items", state.xpSquadRanking());
    }

    @GetMapping("/api/v1/learning/xp/squads/contributions/me")
    public Object squadContribution() {
        return state.squadContributionMe();
    }

    @GetMapping("/xp/squads/contributions/me")
    public Object squadContributionAlias() {
        return state.squadContributionMe();
    }
}
