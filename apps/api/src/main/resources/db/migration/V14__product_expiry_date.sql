-- 소비기한(식품·건강기능식품 등). 재고·반품 상품은 남은 기한이 구매 판단에 중요해서 따로 받아 쇼핑몰에 크게 보여 준다
ALTER TABLE products
    ADD COLUMN expiry_date date;
