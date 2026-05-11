package depth.finvibe.manifest.modules.feed.api.external;

import depth.finvibe.shared.persistence.mongo.feed.UserActivityFeedRepository;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.util.Maps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController {
    private final AuthService authService;
    private final UserActivityFeedRepository userActivityFeedRepository;

    public FeedController(AuthService authService, UserActivityFeedRepository userActivityFeedRepository) {
        this.authService = authService;
        this.userActivityFeedRepository = userActivityFeedRepository;
    }

    @GetMapping("/api/v1/feed/me")
    public Object myFeed(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return Maps.of("items", userActivityFeedRepository.findTop50ByUserIdOrderByCreatedAtDesc(currentUser.userId()));
    }
}