-- 재고·매입: 매입처, 매입서(입고 전표), SKU 매입단가

CREATE TABLE suppliers (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id       bigint      NOT NULL REFERENCES sellers (id),
    name            text        NOT NULL,
    business_no     text,                           -- 사업자등록번호 (없는 개인 매입처도 있음)
    contact_name    text,
    phone           text,
    memo            text,
    is_active       boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_suppliers_seller ON suppliers (seller_id, is_active);
CREATE TRIGGER trg_suppliers_updated_at BEFORE UPDATE ON suppliers FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 매입서: 작성(DRAFT) → 입고완료(RECEIVED, 재고 반영) / 취소(CANCELLED, 작성 중일 때만)
CREATE TABLE purchases (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id       bigint      NOT NULL REFERENCES sellers (id),
    purchase_no     text        NOT NULL UNIQUE,
    supplier_id     bigint      REFERENCES suppliers (id),
    location_id     bigint      NOT NULL REFERENCES locations (id),   -- 입고 위치
    status          text        NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'RECEIVED', 'CANCELLED')),
    purchased_on    date        NOT NULL DEFAULT current_date,
    memo            text,
    total_cost      bigint      NOT NULL DEFAULT 0 CHECK (total_cost >= 0),
    created_by      bigint      REFERENCES users (id),
    received_by     bigint      REFERENCES users (id),
    received_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_purchases_seller ON purchases (seller_id, purchased_on DESC, id DESC);
CREATE TRIGGER trg_purchases_updated_at BEFORE UPDATE ON purchases FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE purchase_items (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id     bigint      NOT NULL REFERENCES purchases (id) ON DELETE CASCADE,
    sku_id          bigint      NOT NULL REFERENCES skus (id),
    qty             integer     NOT NULL CHECK (qty > 0),
    unit_cost       bigint      NOT NULL CHECK (unit_cost >= 0)
);
CREATE INDEX ix_purchase_items_purchase ON purchase_items (purchase_id);
CREATE INDEX ix_purchase_items_sku ON purchase_items (sku_id);

-- 최근 매입단가 (마진 계산·재고 금액용). 입고할 때마다 갱신
ALTER TABLE skus ADD COLUMN cost bigint CHECK (cost >= 0);
