package depth.finvibe.shared.security;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.user.modules.user.application.service.UserStore;
import java.util.Map;

public final class AuthService {
    private final JwtService jwtService;
    private final UserStore userStore;

    public AuthService(JwtService jwtService, UserStore userStore) {
        this.jwtService = jwtService;
        this.userStore = userStore;
    }

    public Map<String, Object> issueTokenPair(String userId) {
        Map<String, Object> tokens = jwtService.createTokenPair(userId);
        userStore.replaceRefreshToken(userId, String.valueOf(tokens.get("refreshToken")), String.valueOf(tokens.get("refreshExpiresAt")));
        return tokens;
    }

    public CurrentUser requireUser(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("UNAUTHORIZED", "인증이 필요합니다.");
        }
        return resolveUserByAccessToken(token);
    }

    public CurrentUser optionalUser(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null || token.isBlank()) {
            return null;
        }
        return resolveUserByAccessToken(token);
    }

    public CurrentUser resolveUserByAccessToken(String token) {
        Map<String, Object> claims = jwtService.decodeAndValidate(token, "access");
        Map<String, Object> user = userStore.getUserById(String.valueOf(claims.get("sub")));
        if (user == null || !Boolean.TRUE.equals(user.get("isActive"))) {
            throw ApiException.unauthorized("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        return userStore.toCurrentUser(user);
    }

    public Map<String, Object> validateRefreshToken(String refreshToken) {
        Map<String, Object> stored = userStore.getRefreshToken(refreshToken);
        if (stored == null) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다.");
        }
        return jwtService.decodeAndValidate(refreshToken, "refresh");
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7).trim();
        }
        return authorizationHeader.trim();
    }
}
