-- 무통장입금 환불 계좌: 입금 후 취소·반품을 신청할 때 고객이 직접 입력한다. 계좌번호는 암호화해서 저장
ALTER TABLE orders
    ADD COLUMN refund_bank_name      text,
    ADD COLUMN refund_account_no_enc text,
    ADD COLUMN refund_account_holder text;
