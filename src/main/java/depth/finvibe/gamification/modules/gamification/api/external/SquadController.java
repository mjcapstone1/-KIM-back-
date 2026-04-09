package depth.finvibe.gamification.modules.gamification.api.external;

import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SquadController {
    private final AppState state;

    public SquadController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/learning/squads")
    public Object squads() {
        return Maps.of("items", state.listSquads());
    }

    @GetMapping("/squads")
    public Object squadsAlias() {
        return Maps.of("items", state.listSquads());
    }

    @GetMapping("/api/v1/learning/squads/me")
    public Object squadMe() {
        return state.squadMe();
    }

    @GetMapping("/squads/me")
    public Object squadMeAlias() {
        return state.squadMe();
    }

    @PostMapping("/squads/{squadId}/join")
    public Object joinSquad(@PathVariable String squadId) {
        Map<String, Object> squad = state.joinSquad(squadId);
        if (squad == null) {
            return Maps.of("message", "스쿼드를 찾지 못했습니다.", "squadId", squadId);
        }
        return Maps.of("message", "스쿼드에 참가했습니다.", "squad", squad);
    }
}
