# AIShoping 시스템 구조 (초안 v0.1)

재고·반품 상품을 온라인(자사몰, 외부 마켓)과 오프라인(매장)에서 판매하고, 이후 입점 판매자를 받는 오픈마켓으로 확장하는 것을 전제로 한 구조.

## 1. 설계 원칙

1. **판매자(seller)를 처음부터 둔다.** 1단계에서는 판매자가 우리 회사 하나(seller_id = 1)뿐이지만, 상품·주문상품·배송·정산이 모두 seller_id를 가진다. 오픈마켓 전환 시 구조 변경이 없다.
2. **주문 처리 단위는 주문상품(order_item)이다.** 부분 취소, 부분 반품, 판매자별 배송, 판매자별 정산이 모두 주문상품 단위로 일어난다.
3. **채널이 달라도 주문은 한 테이블에 모은다.** 자사몰, 스마트스토어, 쿠팡, 오프라인 매장 주문이 `orders.channel`로 구분될 뿐 같은 흐름으로 처리된다.
4. **상품 상태(등급)는 필수 속성이다.** 새상품, 반품(미개봉/개봉), 리퍼, 중고(A/B/C), 전시상품을 SKU 단위로 구분하고 실물 사진을 붙인다.
5. **재고는 원장(ledger)으로 남긴다.** 수량만 덮어쓰지 않고, 모든 입출고를 이동 기록으로 남겨 재고가 맞지 않을 때 추적할 수 있게 한다.
6. **돈은 정수(원)로 저장한다.** 소수점 금액 계산 오류를 막는다.
7. **개인정보는 최소 수집, 민감정보는 암호화, 조회는 기록한다.**

## 2. 기술 스택

| 영역 | 선택 | 이유 |
|---|---|---|
| 언어 | 서버 Java 21, 웹 TypeScript | 서버는 국내 커머스·결제 자료가 가장 많은 Java, 웹 화면은 TypeScript |
| 저장소 구조 | 한 저장소에 서버(Gradle)·웹(pnpm) 폴더 분리 | 혼자 관리하기 쉽게 한 곳에 둠 |
| 쇼핑몰 (apps/web) | Next.js (App Router), 반응형 | PC·모바일 한 코드, 서버 렌더링으로 검색 노출(SEO) 유리 |
| 관리자 (apps/admin) | Next.js | 운영자 판매관리 + 판매자센터를 권한으로 분리 |
| API (apps/api) | Spring Boot + Spring Security | 주문·결제·재고처럼 규칙이 많은 도메인에 검증된 구조, 트랜잭션 관리 |
| DB | PostgreSQL + jOOQ, 마이그레이션은 Flyway | SQL 우선 방식. Flyway로 만든 실제 스키마에서 jOOQ 코드를 생성해, SQL 기능을 제약 없이 쓰면서 컬럼 오타·타입 오류는 컴파일 시점에 잡음. JPA는 쓰지 않음 |
| 백그라운드 작업 | Spring Scheduler (+ 필요 시 Redis 분산 락) | 배송추적 조회, 외부 마켓 주문 수집, 정산 배치, 알림 발송 |
| 테스트 | JUnit 5 + Testcontainers(PostgreSQL) | 결제·주문·재고 로직을 실제 DB로 테스트 |
| 결제 | 토스페이먼츠 직접 연동 (결제위젯 SDK v2 + 승인 API) | 이미 계약된 PG. 결제위젯 하나에 카드·네이버페이·카카오페이·토스페이 노출, 3단계에서 지급대행으로 판매자 정산 |
| 매장 | 토스 매장 단말기/POS (기존 사용) | 매장 결제는 그대로 두고 판매 내역만 재고에 반영 (12 참고) |
| 이미지 | AWS S3 + CloudFront | 상품 실물 사진이 많음 |
| 알림 | 카카오 알림톡 + SMS (발송대행사) | 주문·발송·반품 안내 |
| 로그인 | 이메일/비밀번호 + 카카오·네이버 소셜 로그인 | 가입 장벽 낮춤 |
| 오류 모니터링 | Sentry | 결제·주문 오류 즉시 파악 |
| 호스팅 | AWS 서울 리전 (EC2 또는 ECS + RDS) | 초기는 작게 시작, 트래픽 늘면 확장 |
| 로컬 개발 | Docker Compose (Postgres, Redis) | |

## 3. 시스템 구성

```
                 ┌──────────────┐     ┌──────────────┐
  고객 (PC/모바일) │  apps/web    │     │  apps/admin  │  운영자 · 판매자 · 매장직원
                 │  쇼핑몰       │     │  판매관리     │  (바코드 스캐너, 라벨 프린터)
                 └──────┬───────┘     └──────┬───────┘
                        │      HTTPS / REST   │
                        └─────────┬───────────┘
                           ┌──────▼───────┐
                           │   apps/api   │──── PostgreSQL
                           │ (Spring Boot)│──── Redis (세션·캐시·락), 스케줄러
                           └──────┬───────┘
          ┌───────────────┬───────┼─────────────┬───────────────┐
   토스페이먼츠      통합 송장 API    배송추적 API     외부 마켓 API     알림톡/SMS
  (카드·N페이·카카오페이) (굿스플로 등)  (스마트택배 등)  (네이버 커머스API·쿠팡 Wing)
```

## 4. 모듈 (API 기준)

| 모듈 | 하는 일 | 단계 |
|---|---|---|
| auth / users | 회원가입, 로그인, 소셜 로그인, 권한 | 1 |
| sellers | 판매자 정보, 수수료율, 정산계좌 | 1 (회사 1개) → 3 |
| catalog | 카테고리, 상품, SKU(옵션·상태등급), 이미지, 상품정보고시 | 1 |
| cart / checkout | 장바구니, 주문서, 배송비 계산 | 1 |
| orders | 주문 생성, 상태 변경, 채널 통합 | 1 |
| payments | 결제 요청·검증·웹훅, 취소·환불 | 1 |
| claims | 취소, 반품, 교환 요청과 처리 | 1 |
| inventory | 재고, 위치(창고/매장), 입출고 원장, 개체 바코드 | 2 (기본 수량은 1단계) |
| purchasing | 매입처, 매입, 매입 증빙 첨부, 원가 | 2 |
| shipping | 택배사, 송장 발급·출력, 배송추적, 반품 회수 | 2 |
| channels | 외부 마켓 상품 등록·주문 수집·송장 전송 | 추가 |
| pos | 토스 매장 판매 내역을 주문(channel=POS)으로 가져와 재고 차감 | 2 |
| settlements | 판매자 정산, 수수료, 판매자별 매출 리포트 | 3 |
| audit | 관리자 개인정보 조회 기록, 주요 변경 이력 | 1 |

## 5. 데이터 모델 (핵심 테이블)

### 계정·판매자
| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| users | id, email, password_hash, name, phone_enc, type(CUSTOMER/STAFF), status | 휴대폰 등은 암호화 |
| social_accounts | user_id, provider(KAKAO/NAVER), provider_user_id | |
| addresses | user_id, recipient, phone_enc, zipcode, address1, address2, is_default | |
| sellers | id, business_name, business_no, ceo_name, commission_rate, payout_account_enc, status | 1단계는 1행 |
| seller_members | seller_id, user_id, role(OWNER/MANAGER/STAFF) | 판매자센터 권한 |

### 상품
| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| categories | id, parent_id, name, notice_template | 카테고리별 상품정보고시 양식 |
| products | id, seller_id, category_id, name, brand, description, notice_info(JSON), kc_cert_no, status | 상품 마스터 |
| skus | id, product_id, option_values(JSON), **condition_grade**, condition_note, barcode, price, list_price, status | 판매 단위. 같은 상품이라도 상태등급이 다르면 다른 SKU |
| product_images | id, product_id, sku_id(nullable), url, sort | sku_id가 있으면 그 상태의 실물 사진 |

`condition_grade`: NEW, RETURN_UNOPENED, RETURN_OPENED, REFURB, USED_A, USED_B, USED_C, DISPLAY

### 재고·매입
| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| locations | id, seller_id, name, type(WAREHOUSE/STORE) | 창고, 매장 |
| inventories | sku_id, location_id, on_hand, reserved | 판매가능 = on_hand − reserved |
| inventory_units | id, sku_id, location_id, unit_barcode, status, purchase_item_id | 개체별 관리가 필요한 상품만 사용 |
| inventory_movements | id, sku_id, location_id, qty(+/−), type(PURCHASE_IN/SALE_OUT/RETURN_IN/ADJUST/TRANSFER), ref_type, ref_id, created_by | 재고 원장 |
| suppliers | id, name, business_no, contact | 매입처 (개인 포함) |
| purchases | id, supplier_id, purchased_at, evidence_type(TAX_INVOICE/CASH_RECEIPT/TRANSFER/CONTRACT/NONE), total_amount | |
| purchase_items | id, purchase_id, sku_id, qty, unit_cost | 원가 계산 기준 |
| attachments | id, ref_type, ref_id, file_url | 매입 증빙, 계약서 사진 등 |

### 주문·결제
| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| orders | id, order_no, channel(OWN/SMARTSTORE/COUPANG/POS), external_order_id, user_id, orderer_*, receiver_*(암호화), items_total, shipping_fee, discount_total, paid_total, status, ordered_at | 채널 통합 |
| order_items | id, order_id, **seller_id**, sku_id, product_name_snapshot, condition_snapshot, qty, unit_price, discount, status, confirmed_at | 상태·정산·배송의 기준 단위 |
| payments | id, order_id, provider(PORTONE), pg, method(CARD/NAVERPAY/KAKAOPAY/TOSSPAY/TRANSFER), pg_tx_id, amount, status, paid_at, raw(JSON) | |
| refunds | id, payment_id, claim_id, amount, pg_cancel_tx_id, status | 부분 환불 지원 |
| claims | id, order_item_id, type(CANCEL/RETURN/EXCHANGE), reason_code, reason_detail, fault(CUSTOMER/SELLER), return_shipping_fee, status | 반품 귀책에 따라 배송비 부담 |
| carts / cart_items | user_id, sku_id, qty | |

### 배송
| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| carriers | id, code, name, is_contracted, contract_info(JSON), api_type | 제휴 택배사 관리 |
| shipments | id, seller_id, carrier_id, tracking_no, type(OUTBOUND/RETURN_PICKUP/EXCHANGE), from_location_id, status, shipped_at, delivered_at | 판매자별 묶음 배송 |
| shipment_items | shipment_id, order_item_id, qty | |
| tracking_events | shipment_id, occurred_at, status, location, description | 배송추적 이력 |

### 정산·기록 (3단계 중심)
| 테이블 | 주요 컬럼 | 비고 |
|---|---|---|
| settlements | id, seller_id, period_start, period_end, gross, commission, shipping, adjustments, payout, status, paid_at | |
| settlement_items | settlement_id, order_item_id, amount, commission | 구매확정된 주문상품 기준 |
| audit_logs | id, actor_user_id, action, target_type, target_id, ip, created_at | 개인정보 조회 기록 1년 이상 보관 |

## 6. 주문상품 상태 흐름

```
PENDING_PAYMENT ──결제완료──▶ PAID ──발주확인──▶ PREPARING ──송장등록──▶ SHIPPED ──배송완료──▶ DELIVERED ──구매확정──▶ CONFIRMED
      │                     │                    │                                          │
      └─결제실패/만료─▶ CANCELLED   └─취소요청─▶ CANCEL_REQUESTED ─▶ CANCELLED          └─반품요청─▶ RETURN_REQUESTED ─▶ RETURNING ─▶ RETURNED
                                                                                          └─교환요청─▶ EXCHANGE_REQUESTED ─▶ ...
```

- 구매확정은 고객이 누르거나, 배송완료 후 일정 기간(예: 7일)이 지나면 자동 처리한다.
- 정산은 CONFIRMED 상태의 주문상품만 대상으로 한다.

## 7. 재고 처리 규칙

| 시점 | 처리 |
|---|---|
| 주문 생성 | reserved 증가 (판매가능 수량 감소) |
| 결제 실패·만료 | reserved 감소 |
| 출고(송장 등록) | on_hand 감소, reserved 감소, movement(SALE_OUT) 기록 |
| 반품 입고 | 검수 후 상태등급 재판정 → 같은 SKU 또는 다른 등급 SKU로 on_hand 증가, movement(RETURN_IN) |
| 매장 판매 | on_hand 즉시 감소, movement(SALE_OUT) |
| 외부 마켓 주문 수집 | 자사몰과 같은 규칙 적용, 재고 변경 시 외부 마켓 재고도 갱신 |

## 8. 결제 처리 규칙

1. 주문서에서 서버가 주문을 PENDING_PAYMENT로 생성하고 결제 금액을 확정한다.
2. 브라우저에서 토스페이먼츠 결제위젯을 띄운다 (orderId = 우리 order_no, amount = 서버가 확정한 금액).
3. 결제 인증이 끝나면 successUrl로 paymentKey, orderId, amount가 돌아온다. **서버는 amount가 DB에 저장한 주문 금액과 같은지 먼저 확인**한 뒤 승인 API(`POST /v1/payments/confirm`)를 호출한다. 승인 API를 호출해야 실제 결제가 완료된다.
4. 승인 응답의 paymentKey를 payments.pg_tx_id로 저장한다. 승인·취소 호출에는 Idempotency-Key를 붙여 중복 처리를 막는다.
5. 웹훅(결제 상태 변경, 가상계좌 입금)은 토스 API로 결제를 다시 조회해 상태를 맞춘다.
6. 취소·부분취소는 `POST /v1/payments/{paymentKey}/cancel`에 cancelAmount를 넘긴다.
7. 시크릿 키는 서버에만 둔다. 카드번호는 저장하지 않는다.
8. 간편결제(네이버페이·카카오페이)는 토스페이먼츠 상점관리자에서 결제수단 추가 신청 후 심사를 받아야 노출된다. 새 쇼핑몰 도메인용으로 상점(MID)을 별도로 받을지 토스페이먼츠에 확인한다.

## 9. 보안 기본 사항

- 전 구간 HTTPS, 비밀번호 bcrypt/argon2 해시
- 휴대폰·주소·정산계좌 등은 애플리케이션 레벨 암호화 (`*_enc` 컬럼)
- 관리자: 2단계 인증, 역할별 권한, 가능하면 IP 제한
- 관리자가 고객 개인정보를 조회하면 audit_logs에 기록
- 비밀키는 코드에 두지 않고 환경변수/Secrets Manager로 관리

## 10. 단계별 범위

| 단계 | 범위 |
|---|---|
| 1단계 쇼핑몰 | 회원, 상품(상태등급·실물사진), 장바구니, 주문서, 결제(카드 → 간편결제 추가), 주문조회, 취소·반품 신청, 관리자 주문처리, 기본 재고 수량, 송장번호 수동 입력 |
| 2단계 판매관리 | 위치별 재고, 개체 바코드, 매입·증빙, 송장 API 발급과 라벨 출력, 배송추적 자동화, 택배사 관리, 매장 판매 |
| 3단계 오픈마켓 | 판매자 입점·심사, 판매자센터, 수수료, 토스페이먼츠 지급대행으로 판매자 정산, 판매자별 매출 리포트 |
| 추가 | 스마트스토어·쿠팡 주문 수집, 재고 동기화 |

## 11. 모바일 앱 (하이브리드)

- Android(Kotlin) / iOS(Swift) 네이티브 껍데기 + WebView로 apps/web을 띄운다. 화면과 기능은 웹 하나로 관리한다.
- 네이티브가 맡는 것: 푸시 알림(FCM/APNs), 딥링크(App Links/Universal Links), 결제 앱 호출 처리, 소셜 로그인 SDK, 카메라·사진 첨부, 생체인증, 앱 버전 체크.
- 웹 ↔ 앱 통신은 JS 브릿지 하나로 정의하고 두 앱이 같은 인터페이스를 구현한다 (예: `getPushToken`, `share`, `openExternal`, `socialLogin`, `scanBarcode`).
- 결제: WebView에서 카드사·간편결제 앱을 여는 URL(Android `intent://`, iOS 커스텀 스킴)을 직접 처리해야 한다. 토스페이먼츠 WebView 연동 가이드의 앱 스킴 목록과 처리 방법을 따른다.
- iOS 심사 대비: 단순 웹 포장만으로는 반려될 수 있으므로 푸시·딥링크 등 앱 고유 기능을 넣는다. 카카오·네이버 로그인을 넣으면 Apple 로그인도 함께 제공한다. 앱 안에서 회원 탈퇴가 가능해야 한다. 실물 상품이라 인앱결제는 필요 없다.
- 웹 도메인에 `/.well-known/assetlinks.json`, `/.well-known/apple-app-site-association`을 배포한다 (딥링크용).
- 앱 실행 시 접근권한(알림, 카메라, 사진) 고지와 동의를 받는다.

## 12. 매장(토스) 연동

매장 결제는 기존 토스 단말기/POS를 그대로 쓴다. 필요한 것은 **매장에서 팔린 상품을 재고에 반영하는 것**이다.

| 방법 | 내용 | 비고 |
|---|---|---|
| A. API 연동 | 토스 POS 쪽에 판매 내역 조회 API가 있으면 주기적으로 가져와 주문(channel=POS)으로 저장 | 제공 여부 확인 필요 |
| B. 엑셀 업로드 | POS에서 판매 내역을 내려받아 관리자 화면에 업로드, 상품 바코드로 매칭해 재고 차감 | 확실하게 가능, 하루 1회 등 |
| C. 관리자 판매 등록 | 결제는 토스 단말기, 판매 상품은 관리자 화면에서 바코드 스캔으로 등록 | 개체별 재고(반품·중고)에 가장 정확 |

1차는 C(+B 보조)로 시작하고, A가 가능하면 전환한다. 어느 방법이든 POS 상품의 바코드와 우리 SKU 바코드를 같게 맞추는 것이 핵심이다.

## 13. 1인 개발 운영 원칙

- 기능은 단계 범위를 넘기지 않는다. 1단계는 "주문받고, 결제받고, 발송하고, 환불한다"까지만.
- 직접 만들 필요가 없는 것은 외부 서비스를 쓴다 (결제위젯, 통합 송장, 배송추적, 알림톡, 이미지 CDN).
- 관리자 화면은 디자인보다 처리 속도 우선 (표, 일괄 처리, 바코드 입력).
- 결제·주문·재고 로직은 자동 테스트를 반드시 둔다. 나머지 화면은 수동 확인으로 충분하다.
- 배포는 처음부터 자동화(GitHub Actions)하고, DB는 매일 자동 백업한다.

## 14. 저장소 구조 (예정)

```
AIShoping/
├─ apps/
│  ├─ web/        # 쇼핑몰 (Next.js)
│  ├─ admin/      # 판매관리·판매자센터 (Next.js)
│  ├─ api/        # API + 스케줄러 (Spring Boot, Java 21, Gradle)
│  │  └─ src/main/resources/db/migration/   # Flyway SQL 마이그레이션
│  ├─ android/    # Kotlin WebView 앱
│  └─ ios/        # Swift WebView 앱
├─ docs/
│  └─ api/        # API 명세 (springdoc OpenAPI로 생성, 웹은 여기서 타입 생성)
└─ docker-compose.yml
```
