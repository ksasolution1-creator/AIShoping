-- 약관·개인정보·마케팅 동의 이력. 동의/철회할 때마다 한 줄씩 쌓는다 (분쟁 시 증빙)
CREATE TABLE user_agreements (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    type        text        NOT NULL CHECK (type IN ('TERMS', 'PRIVACY', 'MARKETING', 'AGE_14')),
    version     text        NOT NULL,           -- 약관 버전 (예: 2026-10-01)
    agreed      boolean     NOT NULL,
    ip          inet,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_user_agreements_user ON user_agreements (user_id, type, created_at DESC);
