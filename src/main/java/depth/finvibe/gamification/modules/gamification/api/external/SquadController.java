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
public class SquadController {
    private final AuthService authService;
    private final LearningService learningService;

    public SquadController(AuthService authService, LearningService learningService) {
        this.authService = authService;
        this.learningService = learningService;
    }

    @GetMapping("/api/v1/learning/squads")
    public Object squads(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", learningService.listSquads(currentUser.userId()));
    }

    @GetMapping("/squads")
    public Object squadsAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return squads(authorization);
    }

    @GetMapping("/api/v1/learning/squads/me")
    public Object squadMe(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return learningService.squadMe(currentUser.userId());
    }

    @GetMapping("/squads/me")
    public Object squadMeAlias(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return squadMe(authorization);
    }

    @PostMapping("/api/v1/learning/squads/{squadId}/join")
    public Object joinSquad(@PathVariable String squadId,
                            @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("message", "스쿼드에 참가했습니다.", "squad", learningService.joinSquad(currentUser.userId(), squadId));
    }

    @PostMapping("/squads/{squadId}/join")
    public Object joinSquadAlias(@PathVariable String squadId,
                                 @RequestHeader(name = "Authorization", required = false) String authorization) {
        return joinSquad(squadId, authorization);
    }
}
