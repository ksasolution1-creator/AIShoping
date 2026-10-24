-- 사업자 회원: 사업자 인증 신청·승인, 사업자 전용 상품, 사업자 회원가, 상세이미지 사용 허용

CREATE TABLE business_profiles (
    user_id         bigint      PRIMARY KEY REFERENCES users (id),
    business_no     text        NOT NULL,           -- 숫자 10자리 (하이픈 없이)
    business_name   text        NOT NULL,           -- 상호
    ceo_name        text        NOT NULL,           -- 대표자
    business_item   text,                           -- 업태·종목 (선택)
    status          text        NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reject_reason   text,
    reviewed_by     bigint      REFERENCES users (id),
    reviewed_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
-- 같은 사업자번호로 승인된 회원은 한 명만
CREATE UNIQUE INDEX ux_business_profiles_approved_no ON business_profiles (business_no) WHERE status = 'APPROVED';
CREATE INDEX ix_business_profiles_status ON business_profiles (status, created_at);
CREATE TRIGGER trg_business_profiles_updated_at BEFORE UPDATE ON business_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE products
    ADD COLUMN business_only        boolean NOT NULL DEFAULT false,   -- 사업자 회원만 구매
    ADD COLUMN image_reuse_allowed  boolean NOT NULL DEFAULT false;   -- 상세이미지를 재판매에 써도 되는지

-- 사업자 회원에게 적용하는 판매가 (없으면 일반 판매가)
ALTER TABLE skus ADD COLUMN business_price bigint CHECK (business_price >= 0);
