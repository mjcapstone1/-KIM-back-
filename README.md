# FinVibe Spring Boot Java 21 Backend

업로드된 프론트(`frontend_api_connected_no_mock.zip`)에 맞춘 Java 21 + Spring Boot 백엔드 소스다.

## 구성
- Java 21
- Spring Boot 4.0.2
- Gradle Wrapper 포함
- Swagger UI: `/docs`
- 프론트 개발 서버 CORS 허용: `localhost:3000`, `localhost:5173`
- 사용자/토큰/관심종목/프로필/시뮬레이터 상태: `runtime/*.json` 파일에 저장
- 국내주식 시세/차트/장상태: 한국투자 Open API 연동 코드 포함

## 포함된 주요 기능
- 로그인 / 회원가입 / 토큰 재발급 / 로그아웃
- 로그인 ID / 이메일 / 닉네임 중복 확인
- 실제 사용자 프로필 저장
- 홈 화면 API
- 검색 API
- 시뮬레이터 API
- 학습 / XP / 배지 / 챌린지 / 스쿼드 API
- KIS 국내주식 현재가 / 일·주·월·년 차트 / 분봉 / 장 상태 연동

## 실행 방법
### 1) JDK 21 준비
로컬에 Java 21이 설치되어 있어야 한다.

### 2) 실행 전 환경변수 준비
```bash
cp .env.example .env
```

`.env`를 쓰려면 아래처럼 실행한다.
```bash
./run-local.sh
```

또는 환경변수 없이 바로 실행해도 된다.
```bash
./gradlew bootRun
```

### 3) 확인
- 헬스체크: `http://localhost:8080/health`
- Swagger: `http://localhost:8080/docs`

## 프론트 연동
프론트 `.env`:
```env
VITE_API_BASE_URL=http://localhost:8080
```

## 런타임 저장 파일
실행 후 아래 파일들이 생성된다.
- `runtime/app-state.json`
- `runtime/user-store.json`

## KIS 키 처리
- 현재 `src/main/resources/application.yml` 기본값에 사용자 요청대로 KIS App Key / Secret이 포함되어 있다.
- 보안상 실제 사용 시에는 `.env` 또는 서버 환경변수로 덮어쓰는 방식을 권장한다.
- 채팅에 한 번 노출된 키는 재발급 후 교체하는 편이 안전하다.

## 이 환경에서 확인한 범위
- 프로젝트 전체 자바 소스는 로컬 stub 기반 `javac` 문법 검증을 통과했다.
- 다만 이 실행 환경은 외부 의존성 다운로드가 막혀 있어, 여기서 `./gradlew bootRun` 전체 빌드는 직접 수행하지 못했다.
- 따라서 최종 Spring Boot 실행 검증은 로컬 머신 또는 CI 환경에서 `./gradlew bootRun` / `./gradlew test`로 진행해야 한다.
# -KIM-s-
