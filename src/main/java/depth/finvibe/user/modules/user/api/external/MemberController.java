package depth.finvibe.user.modules.user.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.security.PasswordHasher;
import depth.finvibe.shared.state.AppState;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.Validators;
import depth.finvibe.user.modules.user.application.service.UserStore;
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
    private final UserStore userStore;
    private final AuthService authService;
    private final AppState state;

    public MemberController(UserStore userStore, AuthService authService, AppState state) {
        this.userStore = userStore;
        this.authService = authService;
        this.state = state;
    }

    @GetMapping("/members/check-login-id")
    public Object checkLoginId(@RequestParam(required = false, defaultValue = "") String loginId) {
        return Maps.of("isDuplicate", userStore.existsLoginId(loginId));
    }

    @GetMapping("/members/check-email")
    public Object checkEmail(@RequestParam(required = false, defaultValue = "") String email) {
        return Maps.of("isDuplicate", userStore.existsEmail(email));
    }

    @GetMapping("/members/check-nickname")
    public Object checkNickname(@RequestParam(required = false, defaultValue = "") String nickname) {
        return Maps.of("isDuplicate", userStore.existsNickname(nickname));
    }

    @GetMapping("/members/me")
    public Object me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> user = currentUserRecord(currentUser);
        return userStore.toPublicUser(user);
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
        Map<String, Object> user = userStore.getUserById(userId);
        if (user == null) {
            throw ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        Integer ranking = null;
        for (Map<String, Object> row : state.xpUserRanking()) {
            if (Maps.str(user, "nickname").equals(Maps.str(row, "nickname"))) {
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
                "userId", Maps.str(user, "userId"),
                "nickname", Maps.str(user, "nickname"),
                "gamificationSummary", Maps.of(
                        "userId", Maps.str(user, "userId"),
                        "badges", earnedBadges,
                        "ranking", ranking,
                        "totalXp", state.xpMe().get("totalXp"),
                        "currentReturnRate", state.currentReturnRateForNickname(Maps.str(user, "nickname"))
                )
        );
    }

    @GetMapping("/members/favorite-stocks")
    public Object favoriteStocks(@RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String stockId : userStore.listFavoriteStockIds(currentUser.userId())) {
            Map<String, Object> stock = state.resolveStock(stockId);
            rows.add(Maps.of("stockId", Maps.str(stock, "id"), "name", Maps.str(stock, "name"), "userId", currentUser.userId()));
        }
        return rows;
    }

    @PostMapping("/members/favorite-stocks/{stockId}")
    public Object addFavorite(@PathVariable String stockId,
                              @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> stock = state.resolveStock(stockId);
        String canonical = Maps.str(stock, "id");
        userStore.addFavoriteStock(currentUser.userId(), canonical);
        return Maps.of("stockId", canonical, "name", Maps.str(stock, "name"), "userId", currentUser.userId());
    }

    @DeleteMapping("/members/favorite-stocks/{stockId}")
    public Object removeFavorite(@PathVariable String stockId,
                                 @RequestHeader(name = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authService.requireUser(authorization);
        Map<String, Object> stock = state.resolveStock(stockId);
        String canonical = Maps.str(stock, "id");
        userStore.removeFavoriteStock(currentUser.userId(), canonical);
        return Maps.of("stockId", canonical, "name", Maps.str(stock, "name"), "userId", currentUser.userId());
    }

    private Map<String, Object> currentUserRecord(CurrentUser currentUser) {
        Map<String, Object> user = userStore.getUserById(currentUser.userId());
        if (user == null) {
            throw ApiException.unauthorized("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        return user;
    }

    private Object updateMeInternal(CurrentUser currentUser, Map<String, Object> body) {
        Map<String, Object> user = currentUserRecord(currentUser);
        Map<String, Object> updates = new LinkedHashMap<>();

        if (body.containsKey("loginId")) {
            String loginId = Validators.validateLoginId(Validators.requireString(body.get("loginId"), "loginId"));
            if (!loginId.equals(Maps.str(user, "loginId")) && userStore.existsLoginId(loginId)) {
                throw ApiException.conflict("LOGIN_ID_ALREADY_EXISTS", "이미 사용 중인 로그인 ID입니다.");
            }
            updates.put("loginId", loginId);
        }
        if (body.containsKey("email")) {
            String email = Validators.validateEmail(Validators.requireString(body.get("email"), "email"));
            if (!email.equalsIgnoreCase(Maps.str(user, "email")) && userStore.existsEmail(email)) {
                throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
            }
            updates.put("email", email);
        }
        if (body.containsKey("nickname")) {
            String nickname = Validators.validateNickname(Validators.requireString(body.get("nickname"), "nickname"));
            if (!nickname.equals(Maps.str(user, "nickname")) && userStore.existsNickname(nickname)) {
                throw ApiException.conflict("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.");
            }
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

        Map<String, Object> updated = userStore.updateUser(currentUser.userId(), updates);
        if (updated == null) {
            throw ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        return userStore.toPublicUser(updated);
    }
}
