package depth.finvibe.gamification.modules.gamification.api.external;

import depth.finvibe.gamification.modules.study.application.service.LearningService;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class XpController {
    private final AuthService authService;
    private final LearningService learningService;
    public XpController(AuthService authService, LearningService learningService) {
        this.authService = authService;
        this.learningService = learningService;
    }

    @GetMapping("/api/v1/learning/xp")
    public Object xp(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return learningService.xpMe(currentUser.userId());
    }

    @GetMapping("/xp/me")
    public Object xpAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return xp(authorization);
    }

    @GetMapping("/api/v1/learning/xp/users/ranking")
    public Object xpUsersRanking() {
        return Maps.of("items", learningService.xpUserRanking());
    }

    @GetMapping("/xp/users/ranking")
    public Object xpUsersRankingAlias() {
        return Maps.of("items", learningService.xpUserRanking());
    }

    @GetMapping("/api/v1/learning/xp/squads/ranking")
    public Object xpSquadsRanking() {
        return Maps.of("items", learningService.xpSquadsRanking());
    }

    @GetMapping("/xp/squads/ranking")
    public Object xpSquadsRankingAlias() {
        return Maps.of("items", learningService.xpSquadsRanking());
    }

    @GetMapping("/api/v1/learning/xp/squads/contributions/me")
    public Object squadContribution(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return learningService.squadContributionMe(currentUser.userId());
    }

    @GetMapping("/xp/squads/contributions/me")
    public Object squadContributionAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return squadContribution(authorization);
    }
}
