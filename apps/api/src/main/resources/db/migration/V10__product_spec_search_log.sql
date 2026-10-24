-- 상품 정보 보강(원산지·제조사·포장 부피/무게)과 인기검색어

ALTER TABLE products
    ADD COLUMN origin            text,      -- 원산지 (예: 중국, 국산)
    ADD COLUMN manufacturer      text,      -- 제조사·수입원
    ADD COLUMN package_size      text,      -- 포장 부피 (예: 30x20x10cm)
    ADD COLUMN package_weight_g  integer CHECK (package_weight_g >= 0);

-- 쇼핑몰 검색어 기록 (인기검색어 집계용, 회원 정보는 남기지 않는다)
CREATE TABLE search_logs (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keyword     text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_search_logs_created ON search_logs (created_at);

-- 베스트(최근 판매량) 정렬
CREATE INDEX ix_order_items_sku_created ON order_items (sku_id, created_at);
