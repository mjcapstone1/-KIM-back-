# FinVibe Spring Boot Java 21 Backend

Java 21 + Spring Boot 기반 FinVibe 백엔드입니다. 이번 수정본은 업로드된 프론트엔드 인증 계약에 맞춰 로그인/회원가입을 이메일 기준으로 동작하게 하고, Docker 배포 시 필요한 CORS·환경변수·JWT 보안 설정을 보강했습니다.

## 주요 수정 사항

- `POST /auth/signup`에서 프론트가 보내지 않는 `loginId`를 더 이상 필수로 요구하지 않습니다.
  - 요청에 `loginId`가 없으면 이메일 앞부분을 기반으로 내부용 `loginId`를 자동 생성합니다.
  - 기존 방식처럼 `loginId`를 보내는 클라이언트도 계속 사용할 수 있습니다.
- `POST /auth/login`은 `email + password`와 `loginId + password`를 모두 지원합니다.
- Refresh Token은 DB에 원문 대신 SHA-256 해시로 저장합니다.
- `/auth/sessions`, `/auth/sessions/{tokenFamilyId}`를 추가해 프론트의 로그인 기기 관리 API 호출이 404가 나지 않도록 했습니다.
- 회원 중복 확인 응답은 프론트 타입(`duplicate`)과 기존 필드(`isDuplicate`)를 모두 반환합니다.
- `/members/nickname`, `DELETE /members/{userId}`를 추가해 마이페이지 API와 충돌을 줄였습니다.
- 운영 프로필(`SPRING_PROFILES_ACTIVE=prod`)에서는 `JWT_SECRET_KEY`가 32자 미만이거나 기본값이면 기동을 막습니다.
- `application.yml`에 있던 KIS 키 기본값을 제거했습니다. 실제 키는 `.env` 또는 서버 환경변수로만 주입하세요.
- `Dockerfile`, `.dockerignore`, 앱 서비스 포함 `docker-compose.yml`을 추가/정리했습니다.

## 인증 API 계약

### 회원가입

```http
POST /auth/signup
```

```json
{
  "email": "student@universion.local",
  "password": "finvest1234!",
  "name": "테스트사용자",
  "nickname": "테스터",
  "birthDate": "2001-01-01",
  "phoneNumber": "010-0000-0000"
}
```

응답:

```json
{
  "user": {
    "userId": "uuid",
    "email": "student@universion.local",
    "nickname": "테스터",
    "name": "테스트사용자",
    "birthDate": "2001-01-01",
    "phoneNumber": "010-0000-0000"
  },
  "tokens": {
    "accessToken": "...",
    "accessExpiresAt": "2026-05-01T12:00:00Z",
    "refreshToken": "...",
    "refreshExpiresAt": "2026-05-15T12:00:00Z"
  }
}
```

### 로그인

```http
POST /auth/login
```

```json
{
  "email": "student@universion.local",
  "password": "finvest1234!"
}
```

또는:

```json
{
  "loginId": "student01",
  "password": "finvest1234!"
}
```

응답은 토큰 객체입니다.

### 토큰 갱신

```http
POST /auth/refresh
```

```json
{ "refreshToken": "..." }
```

### 로그아웃

```http
POST /auth/logout
Authorization: Bearer {accessToken}
```

## 로컬 실행

```bash
cp .env.example .env
# .env의 JWT_SECRET_KEY를 32자 이상 랜덤 문자열로 교체

docker compose up -d mariadb mongodb redis kafka
./run-local.sh
```

프론트 `.env` 예시:

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCKS=false
```

프론트가 `/api` 프록시를 사용할 경우에는 리버스 프록시에서 `/api`를 백엔드 루트로 strip 하거나, 프론트 환경변수를 `http://도메인` 형태로 맞추세요.

## Docker 배포

```bash
cp .env.example .env
# 필수: JWT_SECRET_KEY, CORS_ALLOWED_ORIGINS를 실제 값으로 변경

docker compose up -d --build app
```

배포 전 최소 점검:

- `JWT_SECRET_KEY`: 32자 이상 랜덤 문자열
- `CORS_ALLOWED_ORIGINS`: 실제 프론트 도메인, 예: `https://finvibe.space,https://www.finvibe.space`
- `DB_PASSWORD`, `MARIADB_ROOT_PASSWORD`, `MONGO_INITDB_ROOT_PASSWORD`: 운영 비밀번호로 변경
- KIS 연동이 필요하면 `KIS_ENABLED=true`, `KIS_APP_KEY`, `KIS_APP_SECRET` 설정

## 확인 URL

- Health: `/health`
- Actuator Health: `/actuator/health`
- Swagger UI: `/docs`

## 주의

- `.env`는 `.gitignore`에 포함되어 있습니다. 배포 서버에만 보관하세요.
- 기존 Refresh Token은 이번 수정 후 DB 조회 방식이 해시 기반으로 바뀌기 때문에 재로그인이 필요할 수 있습니다.
