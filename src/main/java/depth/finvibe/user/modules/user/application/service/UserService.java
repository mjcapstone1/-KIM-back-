package depth.finvibe.user.modules.user.application.service;

import depth.finvibe.shared.http.ApiException;
import depth.finvibe.shared.persistence.investment.WalletEntity;
import depth.finvibe.shared.persistence.investment.WalletLedgerEntity;
import depth.finvibe.shared.persistence.investment.WalletLedgerRepository;
import depth.finvibe.shared.persistence.investment.WalletRepository;
import depth.finvibe.shared.persistence.market.FavoriteStockEntity;
import depth.finvibe.shared.persistence.market.FavoriteStockId;
import depth.finvibe.shared.persistence.market.FavoriteStockRepository;
import depth.finvibe.shared.persistence.user.RefreshTokenEntity;
import depth.finvibe.shared.persistence.user.RefreshTokenRepository;
import depth.finvibe.shared.persistence.user.UserEntity;
import depth.finvibe.shared.persistence.user.UserRepository;
import depth.finvibe.shared.security.CurrentUser;
import depth.finvibe.shared.security.PasswordHasher;
import depth.finvibe.shared.util.Maps;
import depth.finvibe.shared.util.TimeUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final long SIGNUP_BONUS_KRW = 50_000_000L;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FavoriteStockRepository favoriteStockRepository;
    private final WalletRepository walletRepository;
    private final WalletLedgerRepository walletLedgerRepository;

    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            FavoriteStockRepository favoriteStockRepository,
            WalletRepository walletRepository,
            WalletLedgerRepository walletLedgerRepository
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.favoriteStockRepository = favoriteStockRepository;
        this.walletRepository = walletRepository;
        this.walletLedgerRepository = walletLedgerRepository;
    }

    public boolean existsLoginId(String loginId) {
        return userRepository.existsByLoginId(loginId.toLowerCase());
    }

    public boolean existsEmail(String email) {
        return userRepository.existsByEmail(email.toLowerCase());
    }

    public boolean existsNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    public UserEntity getUserById(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public UserEntity getUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId.toLowerCase()).orElse(null);
    }

    @Transactional
    public UserEntity createUser(String email, String loginId, String password, String nickname, String name, LocalDate birthDate, String phoneNumber) {
        if (existsLoginId(loginId)) {
            throw ApiException.conflict("LOGIN_ID_ALREADY_EXISTS", "이미 사용 중인 로그인 ID입니다.");
        }
        if (existsEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
        }
        if (existsNickname(nickname)) {
            throw ApiException.conflict("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.");
        }

        UserEntity user = new UserEntity();
        user.setUserId(UUID.randomUUID().toString());
        user.setEmail(email.toLowerCase());
        user.setLoginId(loginId.toLowerCase());
        user.setPasswordHash(PasswordHasher.hash(password));
        user.setNickname(nickname);
        user.setName(name);
        user.setBirthDate(birthDate);
        user.setPhoneNumber(phoneNumber);
        user.setActive(true);
        userRepository.save(user);

        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(user.getUserId());
        wallet.setCashBalanceKrw(SIGNUP_BONUS_KRW);
        wallet.setReservedCashKrw(0L);
        wallet.setWithdrawableCashKrw(SIGNUP_BONUS_KRW);
        walletRepository.save(wallet);

        WalletLedgerEntity ledger = new WalletLedgerEntity();
        ledger.setWalletId(wallet.getWalletId());
        ledger.setUserId(user.getUserId());
        ledger.setEntryType("DEPOSIT");
        ledger.setDirection("IN");
        ledger.setAmountKrw(SIGNUP_BONUS_KRW);
        ledger.setBalanceAfterKrw(SIGNUP_BONUS_KRW);
        ledger.setReferenceType("SIGNUP");
        ledger.setReferenceId(user.getUserId());
        ledger.setMemo("회원가입 기본 투자금 지급");
        walletLedgerRepository.save(ledger);

        return user;
    }

    @Transactional
    public UserEntity updateUser(String userId, Map<String, Object> updates) {
        UserEntity user = requireUser(userId);
        if (updates.containsKey("loginId")) {
            String loginId = String.valueOf(updates.get("loginId")).toLowerCase();
            if (!loginId.equals(user.getLoginId()) && existsLoginId(loginId)) {
                throw ApiException.conflict("LOGIN_ID_ALREADY_EXISTS", "이미 사용 중인 로그인 ID입니다.");
            }
            user.setLoginId(loginId);
        }
        if (updates.containsKey("email")) {
            String email = String.valueOf(updates.get("email")).toLowerCase();
            if (!email.equalsIgnoreCase(user.getEmail()) && existsEmail(email)) {
                throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
            }
            user.setEmail(email);
        }
        if (updates.containsKey("nickname")) {
            String nickname = String.valueOf(updates.get("nickname"));
            if (!nickname.equals(user.getNickname()) && existsNickname(nickname)) {
                throw ApiException.conflict("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.");
            }
            user.setNickname(nickname);
        }
        if (updates.containsKey("name")) {
            user.setName(String.valueOf(updates.get("name")));
        }
        if (updates.containsKey("birthDate")) {
            user.setBirthDate(LocalDate.parse(String.valueOf(updates.get("birthDate"))));
        }
        if (updates.containsKey("phoneNumber")) {
            user.setPhoneNumber(String.valueOf(updates.get("phoneNumber")));
        }
        if (updates.containsKey("passwordHash")) {
            user.setPasswordHash(String.valueOf(updates.get("passwordHash")));
        }
        return userRepository.save(user);
    }

    public Map<String, Object> toPublicUser(UserEntity user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getUserId());
        result.put("email", user.getEmail());
        result.put("nickname", user.getNickname());
        result.put("name", user.getName());
        result.put("birthDate", user.getBirthDate() == null ? null : user.getBirthDate().toString());
        result.put("phoneNumber", user.getPhoneNumber());
        return result;
    }

    public CurrentUser toCurrentUser(UserEntity user) {
        return new CurrentUser(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getNickname(),
                user.getName(),
                user.getBirthDate() == null ? null : user.getBirthDate().toString(),
                user.getPhoneNumber(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().atOffset(ZoneOffset.ofHours(9)).toString(),
                user.getUpdatedAt() == null ? null : user.getUpdatedAt().atOffset(ZoneOffset.ofHours(9)).toString(),
                user.isActive(),
                user.getPasswordHash()
        );
    }

    @Transactional
    public void replaceRefreshToken(String userId, String token, String expiresAt) {
        refreshTokenRepository.deleteByUserId(userId);
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(userId);
        entity.setToken(token);
        entity.setExpiresAt(LocalDateTime.ofInstant(Instant.parse(expiresAt), TimeUtil.SEOUL));
        refreshTokenRepository.save(entity);
    }

    @Transactional
    public void deleteRefreshTokensByUser(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public void requireValidRefreshToken(String refreshToken) {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(TimeUtil.SEOUL));
        RefreshTokenEntity entity = refreshTokenRepository.findByTokenAndRevokedAtIsNull(refreshToken)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다."));
        if (entity.getExpiresAt().isBefore(LocalDateTime.now(TimeUtil.SEOUL))) {
            throw ApiException.unauthorized("TOKEN_EXPIRED", "리프레시 토큰이 만료되었습니다.");
        }
    }

    public List<String> listFavoriteStockIds(String userId) {
        return favoriteStockRepository.findAllByIdUserId(userId)
                .stream()
                .map(item -> item.getId().getStockId())
                .toList();
    }

    @Transactional
    public void addFavoriteStock(String userId, String stockId) {
        if (favoriteStockRepository.existsByIdUserIdAndIdStockId(userId, stockId)) {
            return;
        }
        FavoriteStockEntity entity = new FavoriteStockEntity();
        entity.setId(new FavoriteStockId(userId, stockId));
        favoriteStockRepository.save(entity);
    }

    @Transactional
    public void removeFavoriteStock(String userId, String stockId) {
        favoriteStockRepository.deleteByIdUserIdAndIdStockId(userId, stockId);
    }

    public UserEntity requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }
}
