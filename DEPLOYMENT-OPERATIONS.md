# FinVibe 배포 운영 메모

이 문서는 보안 체계나 테스트 코드가 아니라, 배포 안정성과 데이터 운영을 위한 항목만 정리합니다.

## 1. 프로필별 seed 정책

- `prod` 기본 프로필은 운영 DB에서 demo 유저 데이터를 제거합니다.
- `dev` 프로필은 `classpath:db/seed/dev`를 추가로 사용해서 demo 유저 데이터를 다시 넣습니다.

```bash
# 운영 기본값
SPRING_PROFILES_ACTIVE=prod

# 개발/시연용 demo 데이터 포함
SPRING_PROFILES_ACTIVE=dev
```

운영 배포에서는 demo 로그인/거래/포트폴리오 seed가 남지 않도록 `V15__operational_user_scoped_fixes.sql`에서 정리합니다.

## 2. 데이터 보존

Docker 볼륨을 삭제하면 MariaDB/MongoDB/Redis/Kafka 데이터가 사라질 수 있습니다.

```bash
# 데이터 유지
docker compose down

# 데이터 삭제. 운영에서는 사용하지 마세요.
docker compose down -v
```

## 3. MariaDB 백업/복구 예시

백업 파일은 컨테이너 외부 서버 디스크에 보관하세요.

```bash
# 백업
mkdir -p backups
docker compose exec -T mariadb mariadb-dump \
  -uroot -p"$MARIADB_ROOT_PASSWORD" \
  --single-transaction --routines --triggers finvibe \
  > backups/finvibe_$(date +%Y%m%d_%H%M%S).sql

# 복구
cat backups/finvibe_YYYYMMDD_HHMMSS.sql | docker compose exec -T mariadb mariadb \
  -uroot -p"$MARIADB_ROOT_PASSWORD" finvibe
```

## 4. 헬스체크

앱 컨테이너 헬스체크는 단순 포트 체크가 아니라 Actuator readiness endpoint를 확인합니다.

```text
GET /actuator/health/readiness
```

## 5. ID 생성 정책

운영 중 여러 유저가 동시에 생성해도 충돌하지 않도록 다음 ID를 UUID 기반으로 바꿨습니다.

- `portfolio_id`
- `folder_id`
- `order_id`
- `execution_id`

기존 numeric/demo ID는 그대로 조회할 수 있고, 신규 생성분부터 UUID 기반 ID가 사용됩니다.

## 6. 유저별 gamification 저장

스쿼드 참가와 챌린지 완료 상태는 더 이상 전역 `AppState` 변경값으로만 처리하지 않습니다.

- 스쿼드 참가: `user_squad_memberships`
- 챌린지 완료: `user_challenge_progress`

새 테이블은 `V14__create_user_challenge_progress.sql`, `V15__operational_user_scoped_fixes.sql`, `V16__widen_user_squad_membership_id.sql`에서 생성/보정됩니다.
