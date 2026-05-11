package depth.finvibe.gamification.modules.gamification.api.external;

import depth.finvibe.gamification.modules.study.application.service.LearningService;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChallengeController {
    private final AuthService authService;
    private final LearningService learningService;

    public ChallengeController(AuthService authService, LearningService learningService) {
        this.authService = authService;
        this.learningService = learningService;
    }

    @GetMapping("/api/v1/learning/challenges")
    public Object challenges(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", learningService.listChallenges(currentUser.userId()));
    }

    @GetMapping("/challenges/me")
    public Object challengesAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return challenges(authorization);
    }

    @GetMapping("/api/v1/learning/challenges/completed")
    public Object completedChallenges(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", learningService.completedChallenges(currentUser.userId()));
    }

    @GetMapping("/challenges/completed")
    public Object completedChallengesAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return completedChallenges(authorization);
    }

    @PostMapping("/api/v1/learning/challenges/{challengeId}/complete")
    public Object completeChallenge(@PathVariable String challengeId,
                                    @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("message", "챌린지를 완료했습니다.", "challenge", learningService.completeChallenge(currentUser.userId(), challengeId));
    }

    @PostMapping("/challenges/{challengeId}/complete")
    public Object completeChallengeAlias(@PathVariable String challengeId,
                                         @RequestHeader(name = "Authorization", required = false) String authorization) {
        return completeChallenge(challengeId, authorization);
    }
}
