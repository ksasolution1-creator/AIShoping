-- 입금 기한 임박 알림을 보낸 시각 (같은 주문에 두 번 보내지 않는다)
ALTER TABLE orders ADD COLUMN deposit_reminded_at timestamptz;
