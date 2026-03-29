package depth.finvibe.shared.util;

import depth.finvibe.shared.http.ApiException;
import java.time.LocalDate;
import java.util.regex.Pattern;

public final class Validators {
    public static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-z0-9]{5,20}$");
    public static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$");
    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z가-힣]{2,10}$");
    public static final Pattern NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9가-힣]{1,10}$");
    public static final Pattern PHONE_PATTERN = Pattern.compile("^010-\\d{4}-\\d{4}$");

    private Validators() {
    }

    public static String requireString(Object value, String field) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest("INVALID_" + field.toUpperCase(), field + " 값이 필요합니다.");
        }
        return text;
    }

    public static String validateLoginId(String loginId) {
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw ApiException.badRequest("INVALID_LOGIN_ID", "loginId는 영문 소문자/숫자 5~20자여야 합니다.");
        }
        return loginId;
    }

    public static String validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw ApiException.badRequest("INVALID_EMAIL", "이메일 형식이 올바르지 않습니다.");
        }
        return email;
    }

    public static String validateNickname(String nickname) {
        if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw ApiException.badRequest("INVALID_NICKNAME", "nickname은 한글/영문/숫자 1~10자여야 합니다.");
        }
        return nickname;
    }

    public static String validateName(String name) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw ApiException.badRequest("INVALID_NAME", "name은 한글/영문 2~10자여야 합니다.");
        }
        return name;
    }

    public static String validatePhone(String phone) {
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw ApiException.badRequest("INVALID_PHONE_NUMBER", "phoneNumber는 010-0000-0000 형식이어야 합니다.");
        }
        return phone;
    }

    public static String validatePassword(String password, boolean allowShort) {
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("INVALID_PASSWORD", "password가 필요합니다.");
        }
        if (!allowShort && password.length() < 8) {
            throw ApiException.badRequest("INVALID_PASSWORD", "password는 최소 8자 이상이어야 합니다.");
        }
        return password;
    }

    public static LocalDate validateBirthDate(String text) {
        try {
            LocalDate value = LocalDate.parse(text);
            LocalDate today = LocalDate.now();
            LocalDate earliest = today.minusYears(120);
            if (value.isAfter(today) || value.isBefore(earliest)) {
                throw ApiException.badRequest("INVALID_BIRTH_DATE", "birthDate 범위가 올바르지 않습니다.");
            }
            return value;
        } catch (RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw ApiException.badRequest("INVALID_BIRTH_DATE", "birthDate는 YYYY-MM-DD 형식이어야 합니다.");
        }
    }
}
