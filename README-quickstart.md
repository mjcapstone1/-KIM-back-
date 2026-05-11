# FinVibe 백엔드 실행 빠른 시작

## 로컬 개발
```bash
cp .env.example .env
# .env의 JWT_SECRET_KEY를 32자 이상 랜덤 문자열로 교체
# demo 유저/거래/지갑 데이터가 필요하면 SPRING_PROFILES_ACTIVE=dev 로 설정
# 운영과 같은 seed 구성을 확인하려면 SPRING_PROFILES_ACTIVE=prod 유지

docker compose up -d mariadb mongodb redis kafka
./run-local.sh
```

## Docker 배포형 실행
```bash
cp .env.example .env
# .env의 JWT_SECRET_KEY, CORS_ALLOWED_ORIGINS, DB/KIS 값을 배포 환경에 맞게 수정
# 운영에서는 SPRING_PROFILES_ACTIVE=prod 유지

docker compose up -d --build app
```

## 확인
- Health: `http://localhost:8080/health`
- Readiness: `http://localhost:8080/actuator/health/readiness`
- Swagger: `http://localhost:8080/docs`

## 종료
```bash
docker compose down
```

## 데이터까지 완전히 삭제
```bash
docker compose down -v
```
