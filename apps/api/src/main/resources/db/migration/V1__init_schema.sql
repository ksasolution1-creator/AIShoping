-- =====================================================================
-- V1: 1단계(쇼핑몰) 핵심 스키마
-- 규칙
--   - PK: bigint identity
--   - 금액: bigint (원 단위 정수)
--   - 시각: timestamptz
--   - 상태값: text + CHECK (값 추가는 마이그레이션으로 CHECK 교체)
--   - *_enc 컬럼: 애플리케이션에서 암호화한 값
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 계정
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           text,
    password_hash   text,
    name            text        NOT NULL,
    phone_enc       text,
    type            text        NOT NULL DEFAULT 'CUSTOMER'
                    CHECK (type IN ('CUSTOMER', 'STAFF')),
    status          text        NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    marketing_agreed_at timestamptz,
    last_login_at   timestamptz,
    withdrawn_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email)) WHERE email IS NOT NULL AND status <> 'WITHDRAWN';

-- 운영자 권한 (STAFF 전용): ADMIN, OPERATOR, CS, WAREHOUSE, STORE
CREATE TABLE user_roles (
    user_id     bigint NOT NULL REFERENCES users (id),
    role        text   NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE social_accounts (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             bigint      NOT NULL REFERENCES users (id),
    provider            text        NOT NULL CHECK (provider IN ('KAKAO', 'NAVER', 'APPLE')),
    provider_user_id    text        NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);
CREATE INDEX ix_social_accounts_user ON social_accounts (user_id);

CREATE TABLE addresses (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint      NOT NULL REFERENCES users (id),
    label           text,
    recipient_name  text        NOT NULL,
    phone_enc       text        NOT NULL,
    zipcode         text        NOT NULL,
    address1_enc    text        NOT NULL,
    address2_enc    text,
    is_default      boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_addresses_user ON addresses (user_id);
CREATE UNIQUE INDEX ux_addresses_default ON addresses (user_id) WHERE is_default;

-- ---------------------------------------------------------------------
-- 판매자 (1단계는 우리 회사 1곳)
-- ---------------------------------------------------------------------
CREATE TABLE sellers (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_name       text        NOT NULL,
    business_no         text        NOT NULL UNIQUE,
    ceo_name            text        NOT NULL,
    mail_order_no       text,                       -- 통신판매업 신고번호
    contact_phone       text,
    contact_email       text,
    commission_rate_bp  integer     NOT NULL DEFAULT 0 CHECK (commission_rate_bp BETWEEN 0 AND 10000), -- 1bp = 0.01%
    payout_bank_code    text,
    payout_account_enc  text,
    payout_holder       text,
    status              text        NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE seller_members (
    seller_id   bigint      NOT NULL REFERENCES sellers (id),
    user_id     bigint      NOT NULL REFERENCES users (id),
    role        text        NOT NULL CHECK (role IN ('OWNER', 'MANAGER', 'STAFF')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (seller_id, user_id)
);
CREATE INDEX ix_seller_members_user ON seller_members (user_id);

-- ---------------------------------------------------------------------
-- 상품
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id       bigint      REFERENCES categories (id),
    name            text        NOT NULL,
    sort_order      integer     NOT NULL DEFAULT 0,
    notice_template text,                           -- 상품정보제공고시 양식 코드
    is_active       boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_categories_parent ON categories (parent_id, sort_order);

CREATE TABLE products (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id       bigint      NOT NULL REFERENCES sellers (id),
    category_id     bigint      REFERENCES categories (id),
    name            text        NOT NULL,
    brand           text,
    model_name      text,
    description     text,
    notice_info     jsonb       NOT NULL DEFAULT '{}'::jsonb,   -- 상품정보제공고시
    kc_cert_no      text,
    shipping_fee    bigint      NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    free_shipping_threshold bigint CHECK (free_shipping_threshold >= 0),
    status          text        NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'HIDDEN', 'DELETED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_products_seller ON products (seller_id, status);
CREATE INDEX ix_products_category_on_sale ON products (category_id, created_at DESC) WHERE status = 'ON_SALE';
CREATE INDEX ix_products_name_trgm ON products USING gin (name gin_trgm_ops);

-- 판매 단위. 같은 상품이라도 상태등급이 다르면 다른 SKU
CREATE TABLE skus (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      bigint      NOT NULL REFERENCES products (id),
    sku_code        text        NOT NULL UNIQUE,
    option_values   jsonb       NOT NULL DEFAULT '{}'::jsonb,   -- {"색상":"블랙","사이즈":"M"}
    condition_grade text        NOT NULL
                    CHECK (condition_grade IN ('NEW', 'RETURN_UNOPENED', 'RETURN_OPENED', 'REFURB',
                                               'USED_A', 'USED_B', 'USED_C', 'DISPLAY')),
    condition_note  text,                           -- 하자, 구성품 누락 등 상태 설명
    barcode         text,
    price           bigint      NOT NULL CHECK (price >= 0),
    list_price      bigint      CHECK (list_price >= 0),        -- 정가(비교용)
    status          text        NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_skus_product ON skus (product_id);
CREATE INDEX ix_skus_barcode ON skus (barcode) WHERE barcode IS NOT NULL;

CREATE TABLE product_images (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  bigint      NOT NULL REFERENCES products (id),
    sku_id      bigint      REFERENCES skus (id),   -- 있으면 해당 상태등급의 실물 사진
    url         text        NOT NULL,
    sort_order  integer     NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_product_images_product ON product_images (product_id, sort_order);
CREATE INDEX ix_product_images_sku ON product_images (sku_id) WHERE sku_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 재고
-- ---------------------------------------------------------------------
CREATE TABLE locations (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id   bigint      NOT NULL REFERENCES sellers (id),
    name        text        NOT NULL,
    type        text        NOT NULL CHECK (type IN ('WAREHOUSE', 'STORE')),
    is_active   boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- 판매가능 = on_hand - reserved
CREATE TABLE inventories (
    sku_id      bigint      NOT NULL REFERENCES skus (id),
    location_id bigint      NOT NULL REFERENCES locations (id),
    on_hand     integer     NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
    reserved    integer     NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (sku_id, location_id)
);
CREATE INDEX ix_inventories_location ON inventories (location_id);

-- 재고 원장: 모든 입출고를 기록
CREATE TABLE inventory_movements (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku_id      bigint      NOT NULL REFERENCES skus (id),
    location_id bigint      NOT NULL REFERENCES locations (id),
    qty         integer     NOT NULL CHECK (qty <> 0),          -- 입고 +, 출고 -
    type        text        NOT NULL
                CHECK (type IN ('PURCHASE_IN', 'SALE_OUT', 'RETURN_IN', 'ADJUST', 'TRANSFER_IN', 'TRANSFER_OUT', 'GRADE_CHANGE')),
    ref_type    text,                               -- ORDER_ITEM, CLAIM, PURCHASE ...
    ref_id      bigint,
    memo        text,
    created_by  bigint      REFERENCES users (id),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_inventory_movements_sku ON inventory_movements (sku_id, created_at DESC);
CREATE INDEX ix_inventory_movements_ref ON inventory_movements (ref_type, ref_id);

-- ---------------------------------------------------------------------
-- 장바구니
-- ---------------------------------------------------------------------
CREATE TABLE cart_items (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    sku_id      bigint      NOT NULL REFERENCES skus (id),
    qty         integer     NOT NULL CHECK (qty > 0),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, sku_id)
);

-- ---------------------------------------------------------------------
-- 주문
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no            text        NOT NULL UNIQUE,    -- 토스페이먼츠 orderId로 사용
    channel             text        NOT NULL DEFAULT 'OWN'
                        CHECK (channel IN ('OWN', 'SMARTSTORE', 'COUPANG', 'POS')),
    external_order_id   text,
    user_id             bigint      REFERENCES users (id),  -- 비회원·외부채널·매장은 NULL
    orderer_name        text        NOT NULL,
    orderer_phone_enc   text,
    orderer_email       text,
    receiver_name       text,
    receiver_phone_enc  text,
    receiver_zipcode    text,
    receiver_address1_enc text,
    receiver_address2_enc text,
    delivery_memo       text,
    items_total         bigint      NOT NULL CHECK (items_total >= 0),
    shipping_fee        bigint      NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    discount_total      bigint      NOT NULL DEFAULT 0 CHECK (discount_total >= 0),
    pay_amount          bigint      NOT NULL CHECK (pay_amount >= 0),   -- 결제해야 할 금액 (서버 확정값)
    status              text        NOT NULL DEFAULT 'PENDING_PAYMENT'
                        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'COMPLETED', 'CANCELLED')),
    ordered_at          timestamptz NOT NULL DEFAULT now(),
    paid_at             timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_orders_external ON orders (channel, external_order_id) WHERE external_order_id IS NOT NULL;
CREATE INDEX ix_orders_user ON orders (user_id, ordered_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX ix_orders_status ON orders (status, ordered_at DESC);
CREATE INDEX ix_orders_pending ON orders (created_at) WHERE status = 'PENDING_PAYMENT';

-- 상태·배송·정산의 기준 단위
CREATE TABLE order_items (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id            bigint      NOT NULL REFERENCES orders (id),
    seller_id           bigint      NOT NULL REFERENCES sellers (id),
    sku_id              bigint      REFERENCES skus (id),   -- 외부채널 미매칭 상품은 NULL 가능
    product_name        text        NOT NULL,               -- 주문 시점 스냅샷
    option_text         text,
    condition_grade     text,
    qty                 integer     NOT NULL CHECK (qty > 0),
    unit_price          bigint      NOT NULL CHECK (unit_price >= 0),
    discount            bigint      NOT NULL DEFAULT 0 CHECK (discount >= 0),
    status              text        NOT NULL DEFAULT 'PENDING_PAYMENT'
                        CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CONFIRMED',
                                          'CANCEL_REQUESTED', 'CANCELLED',
                                          'RETURN_REQUESTED', 'RETURNING', 'RETURNED',
                                          'EXCHANGE_REQUESTED', 'EXCHANGING', 'EXCHANGED')),
    fulfill_location_id bigint      REFERENCES locations (id),
    delivered_at        timestamptz,
    confirmed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_seller_status ON order_items (seller_id, status, created_at DESC);
CREATE INDEX ix_order_items_confirmed ON order_items (seller_id, confirmed_at) WHERE status = 'CONFIRMED';
CREATE INDEX ix_order_items_sku ON order_items (sku_id);

-- ---------------------------------------------------------------------
-- 결제 (토스페이먼츠)
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        bigint      NOT NULL REFERENCES orders (id),
    provider        text        NOT NULL DEFAULT 'TOSSPAYMENTS',
    payment_key     text        UNIQUE,                 -- 토스 paymentKey
    method          text,                               -- 카드, 간편결제, 가상계좌 ...
    easy_pay_provider text,                             -- 네이버페이, 카카오페이, 토스페이 ...
    amount          bigint      NOT NULL CHECK (amount >= 0),
    balance_amount  bigint      NOT NULL CHECK (balance_amount >= 0),   -- 취소 후 남은 금액
    status          text        NOT NULL DEFAULT 'READY'
                    CHECK (status IN ('READY', 'WAITING_FOR_DEPOSIT', 'DONE', 'PARTIAL_CANCELED', 'CANCELED', 'ABORTED', 'EXPIRED')),
    approved_at     timestamptz,
    raw_response    jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_order ON payments (order_id);

-- ---------------------------------------------------------------------
-- 취소·반품·교환
-- ---------------------------------------------------------------------
CREATE TABLE claims (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_item_id       bigint      NOT NULL REFERENCES order_items (id),
    type                text        NOT NULL CHECK (type IN ('CANCEL', 'RETURN', 'EXCHANGE')),
    qty                 integer     NOT NULL CHECK (qty > 0),
    reason_code         text        NOT NULL,
    reason_detail       text,
    fault               text        CHECK (fault IN ('CUSTOMER', 'SELLER')),
    return_shipping_fee bigint      NOT NULL DEFAULT 0 CHECK (return_shipping_fee >= 0),
    exchange_sku_id     bigint      REFERENCES skus (id),
    inspection_grade    text,                       -- 반품 검수 후 재판정한 상태등급
    status              text        NOT NULL DEFAULT 'REQUESTED'
                        CHECK (status IN ('REQUESTED', 'APPROVED', 'COLLECTING', 'INSPECTING', 'COMPLETED', 'REJECTED', 'WITHDRAWN')),
    requested_by        bigint      REFERENCES users (id),
    processed_by        bigint      REFERENCES users (id),
    requested_at        timestamptz NOT NULL DEFAULT now(),
    completed_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_claims_order_item ON claims (order_item_id);
CREATE INDEX ix_claims_open ON claims (status, requested_at) WHERE status NOT IN ('COMPLETED', 'REJECTED', 'WITHDRAWN');

CREATE TABLE claim_images (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id    bigint      NOT NULL REFERENCES claims (id),
    url         text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_claim_images_claim ON claim_images (claim_id);

CREATE TABLE refunds (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id          bigint      NOT NULL REFERENCES payments (id),
    claim_id            bigint      REFERENCES claims (id),
    amount              bigint      NOT NULL CHECK (amount > 0),
    reason              text        NOT NULL,
    idempotency_key     text        NOT NULL UNIQUE,
    status              text        NOT NULL DEFAULT 'REQUESTED'
                        CHECK (status IN ('REQUESTED', 'DONE', 'FAILED')),
    pg_transaction_key  text,
    raw_response        jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_refunds_payment ON refunds (payment_id);

-- ---------------------------------------------------------------------
-- 배송
-- ---------------------------------------------------------------------
CREATE TABLE carriers (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            text        NOT NULL UNIQUE,
    name            text        NOT NULL,
    tracking_code   text,                           -- 배송추적 API의 택배사 코드
    is_contracted   boolean     NOT NULL DEFAULT false,
    contract_info   jsonb       NOT NULL DEFAULT '{}'::jsonb,   -- 계약 단가, 고객코드 등
    is_active       boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE shipments (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id           bigint      NOT NULL REFERENCES sellers (id),
    order_id            bigint      NOT NULL REFERENCES orders (id),
    claim_id            bigint      REFERENCES claims (id),
    type                text        NOT NULL CHECK (type IN ('OUTBOUND', 'RETURN_PICKUP', 'EXCHANGE_OUTBOUND')),
    carrier_id          bigint      REFERENCES carriers (id),
    tracking_no         text,
    from_location_id    bigint      REFERENCES locations (id),
    status              text        NOT NULL DEFAULT 'READY'
                        CHECK (status IN ('READY', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED')),
    shipped_at          timestamptz,
    delivered_at        timestamptz,
    last_tracked_at     timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_shipments_order ON shipments (order_id);
CREATE UNIQUE INDEX ux_shipments_tracking ON shipments (carrier_id, tracking_no) WHERE tracking_no IS NOT NULL;
CREATE INDEX ix_shipments_to_track ON shipments (last_tracked_at NULLS FIRST)
    WHERE status IN ('SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY');

CREATE TABLE shipment_items (
    shipment_id     bigint  NOT NULL REFERENCES shipments (id),
    order_item_id   bigint  NOT NULL REFERENCES order_items (id),
    qty             integer NOT NULL CHECK (qty > 0),
    PRIMARY KEY (shipment_id, order_item_id)
);
CREATE INDEX ix_shipment_items_order_item ON shipment_items (order_item_id);

CREATE TABLE tracking_events (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shipment_id bigint      NOT NULL REFERENCES shipments (id),
    occurred_at timestamptz NOT NULL,
    status      text        NOT NULL,
    location    text,
    description text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (shipment_id, occurred_at, status)
);

-- ---------------------------------------------------------------------
-- 감사 로그 (개인정보 조회 기록 등, 최소 1년 보관)
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id   bigint      REFERENCES users (id),
    action          text        NOT NULL,           -- VIEW_PERSONAL_INFO, EXPORT, UPDATE ...
    target_type     text        NOT NULL,
    target_id       bigint,
    detail          jsonb,
    ip              inet,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_logs_target ON audit_logs (target_type, target_id);

-- ---------------------------------------------------------------------
-- updated_at 자동 갱신
-- ---------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['users', 'addresses', 'sellers', 'categories', 'products', 'skus',
                             'inventories', 'cart_items', 'orders', 'order_items', 'payments',
                             'claims', 'refunds', 'carriers', 'shipments']
    LOOP
        EXECUTE format('CREATE TRIGGER trg_%1$s_updated_at BEFORE UPDATE ON %1$I
                        FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t);
    END LOOP;
END $$;
