-- 기본 데이터: 우리 회사(판매자 1), 기본 창고·매장, 택배사 목록
-- 사업자 정보는 실제 값으로 관리자 화면에서 수정한다.

INSERT INTO sellers (business_name, business_no, ceo_name, status)
VALUES ('AIShop (본사)', '000-00-00000', '대표자', 'ACTIVE');

INSERT INTO locations (seller_id, name, type)
VALUES (1, '기본 창고', 'WAREHOUSE'),
       (1, '오프라인 매장', 'STORE');

-- tracking_code: 스마트택배(스윗트래커) 택배사 코드
INSERT INTO carriers (code, name, tracking_code)
VALUES ('CJ',     'CJ대한통운', '04'),
       ('HANJIN', '한진택배',   '05'),
       ('LOTTE',  '롯데택배',   '08'),
       ('EPOST',  '우체국택배', '01'),
       ('LOGEN',  '로젠택배',   '06');
