package depth.finvibe.shared.security;

public record CurrentUser(
        String userId,
        String loginId,
        String email,
        String nickname,
        String name,
        String birthDate,
        String phoneNumber,
        String createdAt,
        String updatedAt,
        boolean isActive,
        String passwordHash
) {
}
