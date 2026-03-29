package depth.finvibe.gamification.modules.gamification.api.external;

import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BadgeController {
    private final AppState state;

    public BadgeController(AppState state) {
        this.state = state;
    }

    @GetMapping("/api/v1/learning/badges")
    public Object badges() {
        return Maps.of("items", state.listBadges());
    }

    @GetMapping("/badges")
    public Object badgesAlias() {
        return Maps.of("items", state.listBadges());
    }

    @GetMapping("/badges/me")
    public Object myBadges() {
        return Maps.of("items", state.listBadges());
    }
}
