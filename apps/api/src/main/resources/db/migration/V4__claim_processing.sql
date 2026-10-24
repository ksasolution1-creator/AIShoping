-- 취소·반품 처리에 필요한 값
ALTER TABLE claims
    ADD COLUMN prev_item_status text,                       -- 반품 거절 시 되돌릴 주문상품 상태
    ADD COLUMN refund_amount    bigint CHECK (refund_amount >= 0),
    ADD COLUMN reject_reason    text,
    ADD COLUMN restock_sku_id   bigint REFERENCES skus (id), -- 검수 후 재입고한 SKU (폐기면 NULL)
    ADD COLUMN restock_location_id bigint REFERENCES locations (id),
    ADD COLUMN pickup_carrier_id bigint REFERENCES carriers (id),
    ADD COLUMN pickup_tracking_no text;

-- 검수 결과: 상태등급 코드 또는 폐기
ALTER TABLE claims
    ADD CONSTRAINT ck_claims_inspection_grade CHECK (inspection_grade IS NULL OR inspection_grade IN
        ('NEW', 'RETURN_UNOPENED', 'RETURN_OPENED', 'REFURB', 'USED_A', 'USED_B', 'USED_C', 'DISPLAY', 'DISPOSE'));

-- 한 주문상품에 진행 중인 클레임은 하나만
CREATE UNIQUE INDEX ux_claims_open_per_item ON claims (order_item_id)
    WHERE status NOT IN ('COMPLETED', 'REJECTED', 'WITHDRAWN');
