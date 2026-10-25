# 떨고래 마켓 (AIShop)

재고·반품·땡처리 상품 종합 쇼핑몰 "떨고래 마켓 - 재고 · 땡처리 도매방". 설계는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 참고.

## 구성

| 경로 | 내용 | 실행 포트 |
|---|---|---|
| `apps/api` | Spring Boot 4 · Java 21 · jOOQ · Flyway | 18080 |
| `apps/web` | 쇼핑몰 (Next.js) | 3000 |
| `apps/admin` | 판매관리 (Next.js) | 3001 |
| `docker-compose.yml` | PostgreSQL 18 (5433), Redis 8 (6379) | |

## 처음 실행

```bash
docker compose up -d          # DB, Redis 실행
pnpm install                  # 웹 의존성 설치

cd apps/api
./gradlew bootRun             # API 서버 (시작 시 Flyway가 DB 스키마 적용)

pnpm dev:web                  # 쇼핑몰  http://localhost:3000
pnpm dev:admin                # 관리자  http://localhost:3001
```

필요 도구: JDK 21, Node.js 20+, pnpm 10, Docker Desktop

## API

| 경로 | 내용 |
|---|---|
| `GET /api/products?q=&categoryId=&sort=NEWEST\|PRICE_ASC\|PRICE_DESC&page=&size=` | 쇼핑몰 상품 목록 (하위 카테고리 포함) |
| `GET /api/products/{id}` | 쇼핑몰 상품 상세 (SKU별 판매가능 수량, 사진) |
| `GET /api/categories`, `GET /api/condition-grades` | 카테고리, 상태등급 목록 |
| `GET/POST /api/admin/products`, `GET/PUT /api/admin/products/{id}` | 관리자 상품 목록·등록·조회·수정 |
| `PATCH /api/admin/products/{id}/status` | 상품 상태 변경 (판매중은 판매 SKU 필요) |
| `POST /api/admin/products/{id}/skus`, `PUT /api/admin/skus/{skuId}` | SKU 추가·수정 |
| `POST /api/admin/skus/{skuId}/stock-adjustments` | 재고 수동 조정 (원장 기록) |
| `POST/DELETE /api/admin/products/{id}/images[/{imageId}]` | 사진 업로드·삭제 (`skuId` 지정 시 상태등급 실물 사진) |
| `POST /api/admin/categories` | 카테고리 추가 |
| `GET/POST /api/admin/products/ai-draft` | 사진으로 상품 정보 초안 만들기 (Claude, 저장하지 않음). GET은 사용 가능 여부 |
| `GET /api/admin/members[/{id}]`, `POST .../{id}/status` | 회원 목록(검색·가입 방법·가입일 필터)·상세(연락처 열람 기록)·이용 정지/해제 |
| `GET/POST /api/admin/staff`, `PATCH /api/admin/staff/{id}`, `POST .../{id}/password` | 운영자 계정 추가·권한·정지·비밀번호 초기화 (관리자만) |

| `GET/POST/PATCH/DELETE /api/cart[/items]` | 장바구니 조회·담기·수량 변경·삭제 |
| `POST /api/checkout/preview`, `GET /api/checkout/config` | 주문서 금액 미리보기, 결제위젯 설정 |
| `POST /api/orders` | 결제 대기 주문 생성 (서버가 금액 확정, 재고 예약) |
| `POST /api/payments/confirm` | 토스 결제 승인 (금액 대조 후 승인 API 호출) |
| `POST /api/orders/{orderNo}/abandon`, `POST /api/orders/{orderNo}/cancel` | 결제 포기(예약 해제), 고객 취소(환불) |
| `GET /api/orders[/{orderNo}]` | 내 주문 목록·상세 |
| `POST /api/payments/webhook` | 토스 웹훅 (토스에 재조회해서 상태 반영) |
| `GET /api/admin/orders[/{orderNo}]`, `POST .../accept`, `.../ship`, `.../cancel` | 운영자 주문 조회·발주확인·송장 등록(출고)·취소 |
| `GET /api/auth/csrf` | CSRF 토큰 쿠키(`XSRF-TOKEN`) 발급 |
| `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` | 회원가입·로그인·로그아웃·내 정보 |
| `POST /api/me/withdraw` | 회원 탈퇴 (비밀번호 확인, 개인정보 삭제) |

오류는 `{status, detail, code, fieldErrors}` 형식으로 응답한다.

## 주문·결제 흐름

1. 주문서: `POST /api/orders`가 금액을 계산하고 재고를 예약한 결제 대기 주문을 만든다.
2. 결제위젯 `requestPayment` → 토스 인증 → `/checkout/success`로 돌아옴
3. `POST /api/payments/confirm`: 돌아온 금액과 주문 금액을 대조한 뒤 토스 승인 API를 호출한다.
   - 실패하면 주문 취소·예약 해제, 통신이 끊기면 토스에 조회해서 결과를 확인한다.
4. 결제하지 않고 30분이 지난 주문은 1분마다 도는 작업이 취소하고 예약을 푼다.
5. 운영자: 발주확인 → 송장 등록(이때 재고에서 실제 차감, 재고 이동 기록 `SALE_OUT`)

로컬에서는 쇼핑몰(3000)과 관리자(3001)가 같은 `localhost`라 로그인 쿠키를 함께 쓴다. 한쪽에서 로그인하면 다른 쪽도 같은 계정이 된다. 운영에서는 도메인을 나누면 분리된다.

## 로그인과 보안

- 세션 쿠키(`AISHOP_SESSION`, HttpOnly) 방식. 세션은 Redis에 저장한다.
- 변경 요청(POST/PUT/PATCH/DELETE)은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보내야 한다. 쿠키가 없으면 `GET /api/auth/csrf`로 먼저 받는다.
- `/api/admin/**`은 운영자(ADMIN, OPERATOR 권한)만 접근할 수 있다.
- 같은 이메일로 로그인을 5번 실패하면 15분 동안 잠근다.
- 최초 관리자 계정은 `ADMIN_EMAIL`, `ADMIN_PASSWORD` 환경변수를 넣고 서버를 시작하면 만들어진다.

  ```bash
  ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=10자이상비밀번호 ./gradlew bootRun
  ```

## DB 스키마 변경 방법

1. `apps/api/src/main/resources/db/migration/`에 새 파일 추가 (`V3__설명.sql`). 이미 적용된 파일은 수정하지 않는다.
2. `cd apps/api && ./gradlew jooqCodegen` 실행. DB에 마이그레이션을 적용하고 `src/generated/jooq`의 jOOQ 코드를 다시 만든다.
3. 생성된 코드도 함께 커밋한다. 컴파일 에러가 나는 곳이 스키마 변경 영향을 받는 코드다.

## 테스트

```bash
cd apps/api && ./gradlew test   # Testcontainers로 PostgreSQL을 띄워 실행 (Docker 필요)
```
