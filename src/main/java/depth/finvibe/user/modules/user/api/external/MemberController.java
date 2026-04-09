package depth.finvibe.user.modules.user.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.market.StockQueryService;
import depth.finvibe.shared.persistence.user.UserEntity;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.security.PasswordHasher;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.Validators;
import depth.finvibe.user.modules.user.application.service.UserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberController {
    private final UserService userService;
    private final AuthService authService;
    private final AppState state;
    private final StockQueryService stockQueryService;

    public MemberController(UserService userService, AuthService authService, AppState state, StockQueryService stockQueryService) {
        this.userService = userService;
        this.authService = authService;
        this.state = state;
        this.stockQueryService = stockQueryService;
    }

    @GetMapping("/members/check-login-id")
    public Object checkLoginId(@RequestParam(required = false, defaultValue = "") String loginId) {
        return Maps.of("isDuplicate", userService.existsLoginId(loginId));
    }

    @GetMapping("/members/check-email")
    public Object checkEmail(@RequestParam(required = false, defaultValue = "") String email) {
        return Maps.of("isDuplicate", userService.existsEmail(email));
    }

    @GetMapping("/members/check-nickname")
    public Object checkNickname(@RequestParam(required = false, defaultValue = "") String nickname) {
        return Maps.of("isDuplicate", userService.existsNickname(nickname));
    }

    @GetMapping("/members/me")
    public Object me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        UserEntity user = currentUserRecord(currentUser);
        return userService.toPublicUser(user);
    }

    @PatchMapping("/members")
    public Object updateMe(@RequestHeader(name = "Authorization", required = false) String authorization,
                           @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        return updateMeInternal(currentUser, body == null ? new LinkedHashMap<>() : body);
    }

    @PatchMapping("/members/{userId}")
    public Object updateMeById(@PathVariable String userId,
                               @RequestHeader(name = "Authorization", required = false) String authorization,
                               @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser currentUser = authService.requireUser(authorization);
        if (!userId.equals(currentUser.userId())) {
            throw ApiException.forbidden("CANNOT_UPDATE_OTHER_USER", "본인 프로필만 수정할 수 있습니다.");
        }
        return updateMeInternal(currentUser, body == null ? new LinkedHashMap<>() : body);
    }

    @GetMapping("/members/{userId}")
    public Object memberSummary(@PathVariable String userId) {
        UserEntity user = userService.getUserById(userId);
        if (user == null) {
            throw ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        Integer ranking = null;
        for (Map<String, Object> row : state.xpUserRanking()) {
            if (user.getNickname().equals(Maps.str(row, "nickname"))) {
                ranking = Maps.intVal(row, "rank");
                break;
            }
        }
        List<Map<String, Object>> earnedBadges = new ArrayList<>();
        for (Map<String, Object> badge : state.listBadges()) {
            if (Boolean.TRUE.equals(badge.get("earned"))) {
                earnedBadges.add(badge);
            }
        }
        return Maps.of(
                "userId", user.getUserId(),
                "nickname", user.getNickname(),
                "gamificationSummary", Maps.of(
                        "userId", user.getUserId(),
                        "badges", earnedBadges,
                        "ranking", ranking,
                        "totalXp", state.xpMe().get("totalXp"),
                        "currentReturnRate", state.currentReturnRateForNickname(user.getNickname())
                )
        );
    }

    @GetMapping("/members/favorite-stocks")
    public Object favoriteStocks(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String stockId : userService.listFavoriteStockIds(currentUser.userId())) {
            Map<String, Object> stock = stockQueryService.resolveStock(stockId);
            rows.add(Maps.of("stockId", Maps.str(stock, "id"), "name", Maps.str(stock, "name"), "userId", currentUser.userId()));
        }
        return rows;
    }

    @PostMapping("/members/favorite-stocks/{stockId}")
    public Object addFavorite(@PathVariable String stockId,
                              @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> stock = stockQueryService.resolveStock(stockId);
        String canonical = Maps.str(stock, "id");
        userService.addFavoriteStock(currentUser.userId(), canonical);
        return Maps.of("stockId", canonical, "name", Maps.str(stock, "name"), "userId", currentUser.userId());
    }

    @DeleteMapping("/members/favorite-stocks/{stockId}")
    public Object removeFavorite(@PathVariable String stockId,
                                 @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> stock = stockQueryService.resolveStock(stockId);
        String canonical = Maps.str(stock, "id");
        userService.removeFavoriteStock(currentUser.userId(), canonical);
        return Maps.of("stockId", canonical, "name", Maps.str(stock, "name"), "userId", currentUser.userId());
    }

    private UserEntity currentUserRecord(CurrentUser currentUser) {
        UserEntity user = userService.getUserById(currentUser.userId());
        if (user == null) {
            throw ApiException.unauthorized("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    private Object updateMeInternal(CurrentUser currentUser, Map<String, Object> body) {
        UserEntity user = currentUserRecord(currentUser);
        Map<String, Object> updates = new LinkedHashMap<>();

        if (body.containsKey("loginId")) {
            String loginId = Validators.validateLoginId(Validators.requireString(body.get("loginId"), "loginId"));
            updates.put("loginId", loginId);
        }
        if (body.containsKey("email")) {
            String email = Validators.validateEmail(Validators.requireString(body.get("email"), "email"));
            updates.put("email", email);
        }
        if (body.containsKey("nickname")) {
            String nickname = Validators.validateNickname(Validators.requireString(body.get("nickname"), "nickname"));
            updates.put("nickname", nickname);
        }
        if (body.containsKey("name")) {
            updates.put("name", Validators.validateName(Validators.requireString(body.get("name"), "name")));
        }
        if (body.containsKey("birthDate")) {
            LocalDate birthDate = Validators.validateBirthDate(Validators.requireString(body.get("birthDate"), "birthDate"));
            updates.put("birthDate", birthDate.toString());
        }
        if (body.containsKey("phoneNumber")) {
            updates.put("phoneNumber", Validators.validatePhone(Validators.requireString(body.get("phoneNumber"), "phoneNumber")));
        }
        if (body.containsKey("newPassword")) {
            String oldPassword = Validators.requireString(body.get("oldPassword"), "oldPassword");
            if (!PasswordHasher.verify(oldPassword, currentUser.passwordHash())) {
                throw ApiException.unauthorized("INVALID_PASSWORD", "기존 비밀번호가 올바르지 않습니다.");
            }
            String newPassword = Validators.validatePassword(Validators.requireString(body.get("newPassword"), "newPassword"), false);
            updates.put("passwordHash", PasswordHasher.hash(newPassword));
        }

        UserEntity updated = userService.updateUser(user.getUserId(), updates);
        return userService.toPublicUser(updated);
    }
}
