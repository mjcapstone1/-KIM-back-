# Source Mapping

참고한 업로드 소스:
- Finvibe_Backend_User-main.zip
- Finvibe_Backend_Investment.zip
- Finvibe_Backend_Gamification-main.zip
- Finvibe_Backend_Insight.zip
- Finvibe_Backend_Manifest-main.zip
- frontend_api_connected_no_mock.zip

## 반영 방식
- 패키지 구조는 참고 백엔드들의 도메인 분리 방식을 유지했다.
- 프론트에서 실제 호출하는 API 경로를 우선 기준으로 맞췄다.
- KIS 연동은 시세/차트/장상태만 우선 적용했다.
- 인증/프로필/관심종목은 파일 기반 영속 저장으로 구현했다.
- 프론트 디자인은 전혀 건드리지 않고 API 계약에 맞춰 백엔드를 구성했다.

## 도메인별 매핑
- `user` → 인증, 회원, 중복확인, 프로필, 관심종목
- `investment` → 종목, 상세, 차트, 호가, 지갑, 주문, 자동주문, 포트폴리오
- `insight` → 테마, 뉴스
- `manifest` → 홈 화면, 검색
- `gamification` → 학습 대시보드, 코스, 레슨 완료, XP, 배지, 챌린지, 스쿼드
