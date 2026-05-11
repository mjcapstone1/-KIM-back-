package depth.finvibe.user.modules.user.api.external;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.persistence.user.UserEntity;
import depth.finvibe.shared.security.AuthService;
import depth.finvibe.shared.security.PasswordHasher;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.Validators;
import depth.finvibe.user.modules.user.application.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        String email = Validators.validateEmail(Validators.requireString(request.get("email"), "email")).toLowerCase();
        String loginId = resolveSignupLoginId(request, email);
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
        String password = Validators.validatePassword(Validators.requireString(request.get("password"), "password"), true);
        UserEntity user = resolveLoginUser(request);
        if (user == null || !PasswordHasher.verify(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "이메일/로그인 ID 또는 비밀번호가 올바르지 않습니다.");
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

    @GetMapping("/auth/sessions")
    public Object sessions(@RequestHeader(name = "Authorization", required = false) String authorization,
                           HttpServletRequest request) {
        String userId = authService.requireUser(authorization).userId();
        List<Map<String, Object>> sessions = userService.listActiveSessions(userId, request.getHeader("User-Agent"), clientIp(request));
        return sessions;
    }

    @DeleteMapping("/auth/sessions/{tokenFamilyId}")
    public Object revokeSession(@PathVariable String tokenFamilyId,
                                @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUser(authorization).userId();
        userService.revokeSession(userId, tokenFamilyId);
        return Maps.of("message", "세션이 해제되었습니다.");
    }

    private String resolveSignupLoginId(Map<String, Object> request, String email) {
        Object loginIdValue = request.get("loginId");
        if (loginIdValue == null || String.valueOf(loginIdValue).isBlank()) {
            return userService.generateAvailableLoginId(email);
        }
        return Validators.validateLoginId(Validators.requireString(loginIdValue, "loginId"));
    }

    private UserEntity resolveLoginUser(Map<String, Object> request) {
        Object emailValue = request.get("email");
        if (emailValue != null && !String.valueOf(emailValue).isBlank()) {
            String email = Validators.validateEmail(Validators.requireString(emailValue, "email"));
            return userService.getUserByEmail(email);
        }

        Object loginIdValue = request.get("loginId");
        if (loginIdValue != null && !String.valueOf(loginIdValue).isBlank()) {
            String loginId = Validators.validateLoginId(Validators.requireString(loginIdValue, "loginId"));
            return userService.getUserByLoginId(loginId);
        }

        throw ApiException.badRequest("INVALID_LOGIN_IDENTIFIER", "email 또는 loginId 값이 필요합니다.");
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
