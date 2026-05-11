package depth.finvibe.gamification.modules.gamification.api.external;

import depth.finvibe.gamification.modules.study.application.service.LearningService;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BadgeController {
    private final AuthService authService;
    private final LearningService learningService;

    public BadgeController(AuthService authService, LearningService learningService) {
        this.authService = authService;
        this.learningService = learningService;
    }

    @GetMapping("/api/v1/learning/badges")
    public Object badges(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", learningService.listBadges(currentUser.userId()));
    }

    @GetMapping("/badges")
    public Object badgesAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return badges(authorization);
    }

    @GetMapping("/badges/me")
    public Object myBadges(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return badges(authorization);
    }
}
