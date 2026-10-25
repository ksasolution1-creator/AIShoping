-- 무통장입금: 회사 계좌로 직접 입금받고, 운영자가 입금을 확인하면 결제완료가 된다.
-- 결제 행(payments)은 입금 확인 때 provider = 'BANK_TRANSFER'로 만든다.
ALTER TABLE orders
    ADD COLUMN pay_method     text NOT NULL DEFAULT 'TOSS' CHECK (pay_method IN ('TOSS', 'BANK_TRANSFER')),
    ADD COLUMN depositor_name text,          -- 입금자명
    ADD COLUMN deposit_due_at timestamptz;   -- 이 시각까지 입금하지 않으면 자동 취소

CREATE INDEX ix_orders_deposit_due ON orders (deposit_due_at)
    WHERE status = 'PENDING_PAYMENT' AND pay_method = 'BANK_TRANSFER';

-- 무통장입금 환불은 운영자가 직접 송금한다: REQUESTED(송금 대기) → DONE(송금 완료)
ALTER TABLE refunds ADD COLUMN completed_at timestamptz;
