package depth.finvibe.user.modules.user.application.service;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.json.Json;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.security.PasswordHasher;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UserStore {
    private final Path file;
    private final Object lock = new Object();
    private Map<String, Object> database;

    public UserStore(Path dataDir) {
        try {
            Files.createDirectories(dataDir);
            this.file = dataDir.resolve("user-store.json");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        load();
    }

    public Map<String, Object> getUserById(String userId) {
        synchronized (lock) {
            Map<String, Object> user = findUserByIdRef(userId);
            return user == null ? null : Maps.map(Json.deepCopy(user));
        }
    }

    public Map<String, Object> getUserByLoginId(String loginId) {
        synchronized (lock) {
            Map<String, Object> user = findUserByLoginIdRef(loginId);
            return user == null ? null : Maps.map(Json.deepCopy(user));
        }
    }

    public Map<String, Object> getUserByEmail(String email) {
        synchronized (lock) {
            Map<String, Object> user = findUserByEmailRef(email);
            return user == null ? null : Maps.map(Json.deepCopy(user));
        }
    }

    public boolean existsLoginId(String loginId) {
        synchronized (lock) {
            return findUserByLoginIdRef(loginId) != null;
        }
    }

    public boolean existsEmail(String email) {
        synchronized (lock) {
            return findUserByEmailRef(email) != null;
        }
    }

    public boolean existsNickname(String nickname) {
        synchronized (lock) {
            return findUserByNicknameRef(nickname) != null;
        }
    }

    public Map<String, Object> createUser(
            String email,
            String loginId,
            String password,
            String nickname,
            String name,
            LocalDate birthDate,
            String phoneNumber
    ) {
        synchronized (lock) {
            if (findUserByLoginIdRef(loginId) != null) {
                throw ApiException.conflict("LOGIN_ID_ALREADY_EXISTS", "이미 사용 중인 로그인 ID입니다.");
            }
            if (findUserByEmailRef(email) != null) {
                throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
            }
            if (findUserByNicknameRef(nickname) != null) {
                throw ApiException.conflict("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.");
            }
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("userId", UUID.randomUUID().toString());
            user.put("email", email.toLowerCase());
            user.put("loginId", loginId.toLowerCase());
            user.put("passwordHash", PasswordHasher.hash(password));
            user.put("nickname", nickname);
            user.put("name", name);
            user.put("birthDate", birthDate.toString());
            user.put("phoneNumber", phoneNumber);
            user.put("createdAt", TimeUtil.nowSeoulIso());
            user.put("updatedAt", TimeUtil.nowSeoulIso());
            user.put("isActive", Boolean.TRUE);
            usersRef().add(user);
            save();
            return Maps.map(Json.deepCopy(user));
        }
    }

    public Map<String, Object> updateUser(String userId, Map<String, Object> updates) {
        synchronized (lock) {
            Map<String, Object> user = findUserByIdRef(userId);
            if (user == null) {
                return null;
            }
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("email".equals(key) && value != null) {
                    user.put(key, String.valueOf(value).toLowerCase());
                } else if ("loginId".equals(key) && value != null) {
                    user.put(key, String.valueOf(value).toLowerCase());
                } else if ("birthDate".equals(key) && value != null) {
                    user.put(key, value.toString());
                } else if (value != null) {
                    user.put(key, value);
                }
            }
            user.put("updatedAt", TimeUtil.nowSeoulIso());
            save();
            return Maps.map(Json.deepCopy(user));
        }
    }

    public Map<String, Object> getRefreshToken(String token) {
        synchronized (lock) {
            pruneExpiredRefreshTokens();
            for (Map<String, Object> item : refreshTokensRef()) {
                if (token.equals(Maps.str(item, "token"))) {
                    return Maps.map(Json.deepCopy(item));
                }
            }
            return null;
        }
    }

    public void replaceRefreshToken(String userId, String token, String expiresAt) {
        synchronized (lock) {
            refreshTokensRef().removeIf(item -> userId.equals(Maps.str(item, "userId")));
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("userId", userId);
            record.put("token", token);
            record.put("expiresAt", expiresAt);
            refreshTokensRef().add(record);
            save();
        }
    }

    public void deleteRefreshTokensByUser(String userId) {
        synchronized (lock) {
            refreshTokensRef().removeIf(item -> userId.equals(Maps.str(item, "userId")));
            save();
        }
    }

    public List<String> listFavoriteStockIds(String userId) {
        synchronized (lock) {
            List<String> result = new ArrayList<>();
            for (Map<String, Object> item : favoriteStocksRef()) {
                if (userId.equals(Maps.str(item, "userId"))) {
                    result.add(Maps.str(item, "stockId"));
                }
            }
            return result;
        }
    }

    public void addFavoriteStock(String userId, String stockId) {
        synchronized (lock) {
            for (Map<String, Object> item : favoriteStocksRef()) {
                if (userId.equals(Maps.str(item, "userId")) && stockId.equals(Maps.str(item, "stockId"))) {
                    return;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("stockId", stockId);
            favoriteStocksRef().add(row);
            save();
        }
    }

    public void removeFavoriteStock(String userId, String stockId) {
        synchronized (lock) {
            favoriteStocksRef().removeIf(item -> userId.equals(Maps.str(item, "userId")) && stockId.equals(Maps.str(item, "stockId")));
            save();
        }
    }

    public Map<String, Object> toPublicUser(Map<String, Object> user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", Maps.str(user, "userId"));
        result.put("email", Maps.str(user, "email"));
        result.put("nickname", Maps.str(user, "nickname"));
        result.put("name", Maps.str(user, "name"));
        result.put("birthDate", Maps.str(user, "birthDate"));
        result.put("phoneNumber", Maps.str(user, "phoneNumber"));
        return result;
    }

    public CurrentUser toCurrentUser(Map<String, Object> user) {
        return new CurrentUser(
                Maps.str(user, "userId"),
                Maps.str(user, "loginId"),
                Maps.str(user, "email"),
                Maps.str(user, "nickname"),
                Maps.str(user, "name"),
                Maps.str(user, "birthDate"),
                Maps.str(user, "phoneNumber"),
                Maps.str(user, "createdAt"),
                Maps.str(user, "updatedAt"),
                Boolean.TRUE.equals(user.get("isActive")),
                Maps.str(user, "passwordHash")
        );
    }

    private void load() {
        synchronized (lock) {
            if (Files.exists(file)) {
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    database = Maps.map(Json.parse(text));
                    ensureCollections();
                    pruneExpiredRefreshTokens();
                    return;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            database = new LinkedHashMap<>();
            database.put("users", new ArrayList<>());
            database.put("refreshTokens", new ArrayList<>());
            database.put("favoriteStocks", new ArrayList<>());
            save();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> usersRef() {
        return (List<Map<String, Object>>) (List<?>) database.computeIfAbsent("users", ignored -> new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> refreshTokensRef() {
        return (List<Map<String, Object>>) (List<?>) database.computeIfAbsent("refreshTokens", ignored -> new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> favoriteStocksRef() {
        return (List<Map<String, Object>>) (List<?>) database.computeIfAbsent("favoriteStocks", ignored -> new ArrayList<>());
    }

    private void ensureCollections() {
        database.computeIfAbsent("users", ignored -> new ArrayList<>());
        database.computeIfAbsent("refreshTokens", ignored -> new ArrayList<>());
        database.computeIfAbsent("favoriteStocks", ignored -> new ArrayList<>());
    }

    private Map<String, Object> findUserByIdRef(String userId) {
        for (Map<String, Object> user : usersRef()) {
            if (userId.equals(Maps.str(user, "userId"))) {
                return user;
            }
        }
        return null;
    }

    private Map<String, Object> findUserByLoginIdRef(String loginId) {
        String normalized = loginId == null ? null : loginId.toLowerCase();
        for (Map<String, Object> user : usersRef()) {
            if (normalized != null && normalized.equals(Maps.str(user, "loginId"))) {
                return user;
            }
        }
        return null;
    }

    private Map<String, Object> findUserByEmailRef(String email) {
        String normalized = email == null ? null : email.toLowerCase();
        for (Map<String, Object> user : usersRef()) {
            if (normalized != null && normalized.equals(Maps.str(user, "email"))) {
                return user;
            }
        }
        return null;
    }

    private Map<String, Object> findUserByNicknameRef(String nickname) {
        for (Map<String, Object> user : usersRef()) {
            if (nickname != null && nickname.equals(Maps.str(user, "nickname"))) {
                return user;
            }
        }
        return null;
    }

    private void pruneExpiredRefreshTokens() {
        refreshTokensRef().removeIf(item -> {
            try {
                return Instant.parse(Maps.str(item, "expiresAt")).isBefore(Instant.now());
            } catch (RuntimeException e) {
                return true;
            }
        });
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.stringify(database), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
