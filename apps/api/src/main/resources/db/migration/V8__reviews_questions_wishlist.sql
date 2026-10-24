-- 구매확정 이후 후기, 상품문의, 찜

-- 구매후기: 주문 상품 하나에 하나. 구매확정한 상품만 쓸 수 있다
CREATE TABLE reviews (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_item_id   bigint      NOT NULL UNIQUE REFERENCES order_items (id),
    product_id      bigint      NOT NULL REFERENCES products (id),
    user_id         bigint      NOT NULL REFERENCES users (id),
    rating          smallint    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content         text        NOT NULL,
    is_hidden       boolean     NOT NULL DEFAULT false,      -- 운영자가 숨긴 후기 (욕설·광고 등)
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_reviews_product ON reviews (product_id, created_at DESC) WHERE NOT is_hidden;
CREATE TRIGGER trg_reviews_updated_at BEFORE UPDATE ON reviews FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 상품문의: 비밀글은 작성자와 운영자만 본다
CREATE TABLE product_questions (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      bigint      NOT NULL REFERENCES products (id),
    user_id         bigint      NOT NULL REFERENCES users (id),
    question        text        NOT NULL,
    is_secret       boolean     NOT NULL DEFAULT false,
    answer          text,
    answered_by     bigint      REFERENCES users (id),
    answered_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_product_questions_product ON product_questions (product_id, created_at DESC);
CREATE INDEX ix_product_questions_unanswered ON product_questions (created_at) WHERE answer IS NULL;

CREATE TABLE wishlists (
    user_id     bigint      NOT NULL REFERENCES users (id),
    product_id  bigint      NOT NULL REFERENCES products (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id)
);

-- 자동 구매확정 대상 찾기 (배송완료 후 8일)
CREATE INDEX ix_order_items_delivered ON order_items (delivered_at) WHERE status = 'DELIVERED';
