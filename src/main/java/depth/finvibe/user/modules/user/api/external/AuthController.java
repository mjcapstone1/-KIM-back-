package depth.finvibe.user.modules.user.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.persistence.user.UserEntity;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.PasswordHasher;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.Validators;
import depth.finvibe.user.modules.user.application.service.UserService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/auth/signup")
    public Object signup(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        String email = Validators.validateEmail(Validators.requireString(request.get("email"), "email"));
        String loginId = Validators.validateLoginId(Validators.requireString(request.get("loginId"), "loginId"));
        String password = Validators.validatePassword(Validators.requireString(request.get("password"), "password"), false);
        String nickname = Validators.validateNickname(Validators.requireString(request.get("nickname"), "nickname"));
        String name = Validators.validateName(Validators.requireString(request.get("name"), "name"));
        LocalDate birthDate = Validators.validateBirthDate(Validators.requireString(request.get("birthDate"), "birthDate"));
        String phoneNumber = Validators.validatePhone(Validators.requireString(request.get("phoneNumber"), "phoneNumber"));

        UserEntity user = userService.createUser(email, loginId, password, nickname, name, birthDate, phoneNumber);
        Map<String, Object> tokens = authService.issueTokenPair(user.getUserId());
        return Maps.of("user", userService.toPublicUser(user), "tokens", tokens);
    }

    @PostMapping("/auth/login")
    public Object login(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        String loginId = Validators.validateLoginId(Validators.requireString(request.get("loginId"), "loginId"));
        String password = Validators.validatePassword(Validators.requireString(request.get("password"), "password"), true);
        UserEntity user = userService.getUserByLoginId(loginId);
        if (user == null || !PasswordHasher.verify(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "로그인 ID 또는 비밀번호가 올바르지 않습니다.");
        }
        if (!user.isActive()) {
            throw ApiException.unauthorized("USER_INACTIVE", "비활성화된 사용자입니다.");
        }
        return authService.issueTokenPair(user.getUserId());
    }

    @PostMapping("/auth/refresh")
    public Object refresh(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : body;
        String refreshToken = Validators.requireString(request.get("refreshToken"), "refreshToken");
        Map<String, Object> claims = authService.validateRefreshToken(refreshToken);
        UserEntity user = userService.getUserById(String.valueOf(claims.get("sub")));
        if (user == null || !user.isActive()) {
            throw ApiException.unauthorized("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        return authService.issueTokenPair(user.getUserId());
    }

    @PostMapping("/auth/logout")
    public Object logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUser(authorization).userId();
        userService.deleteRefreshTokensByUser(userId);
        return Maps.of("message", "로그아웃되었습니다.");
    }
}
