# FinVibe 팀원용 로컬 실행 빠른 시작

## 1) 파일 위치
- `docker-compose.yml` -> 프로젝트 루트
- `.env.example` -> 프로젝트 루트
- `.env` -> `.env.example` 복사 후 생성

## 2) 실행 순서
```bash
cp .env.example .env
docker compose up -d
./run-local.sh
```

## 3) 종료
```bash
docker compose down
```

## 4) 데이터까지 완전히 삭제
```bash
docker compose down -v
```
