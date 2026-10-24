-- 도매 구매조건: 최소구매수량, 출고 예정일, 수량별 할인 단가

ALTER TABLE products
    ADD COLUMN min_order_qty    integer NOT NULL DEFAULT 1 CHECK (min_order_qty BETWEEN 1 AND 10000),
    ADD COLUMN ship_within_days integer NOT NULL DEFAULT 3 CHECK (ship_within_days BETWEEN 0 AND 30);

-- 같은 상품(등급·옵션 무관)을 min_qty개 이상 사면 단가를 discount_bp만큼 깎는다 (1bp = 0.01%)
CREATE TABLE product_price_tiers (
    product_id  bigint  NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    min_qty     integer NOT NULL CHECK (min_qty >= 2),
    discount_bp integer NOT NULL CHECK (discount_bp BETWEEN 1 AND 9000),
    PRIMARY KEY (product_id, min_qty)
);
