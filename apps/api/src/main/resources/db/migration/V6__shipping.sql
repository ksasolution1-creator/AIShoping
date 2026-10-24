-- 배송·송장: 판매자의 보내는 사람·출고지·반품지 (송장 엑셀과 상품 상세 반품 안내에 쓴다)

ALTER TABLE sellers
    ADD COLUMN sender_name       text,          -- 송장에 찍히는 보내는 분 (비우면 상호)
    ADD COLUMN sender_phone      text,          -- 비우면 대표 연락처
    ADD COLUMN ship_from_zipcode text,
    ADD COLUMN ship_from_address text,
    ADD COLUMN return_zipcode    text,
    ADD COLUMN return_address    text;

-- 배송 현황 목록 (판매자별 상태·출고일 순)
CREATE INDEX ix_shipments_seller_status ON shipments (seller_id, status, shipped_at DESC);
