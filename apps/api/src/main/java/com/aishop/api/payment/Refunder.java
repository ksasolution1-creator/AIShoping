package com.aishop.api.payment;

import org.springframework.stereotype.Component;

import com.aishop.api.jooq.tables.records.PaymentsRecord;
import com.aishop.api.payment.TossPaymentsClient.TossPayment;

/**
 * 결제 취소(환불). 토스 결제는 토스에 취소를 요청하고,
 * 무통장입금은 돌려줄 길이 없으니 운영자가 직접 송금하도록 '송금 대기'(REQUESTED)로 남긴다.
 */
@Component
public class Refunder {

	public static final String BANK_TRANSFER = "BANK_TRANSFER";

	private final TossPaymentsClient toss;

	public Refunder(TossPaymentsClient toss) {
		this.toss = toss;
	}

	/** @param refundStatus refunds.status 값. 토스는 DONE, 무통장입금은 REQUESTED */
	public record Refunded(String paymentStatus, long balanceAmount, String refundStatus, String transactionKey,
			String raw) {
	}

	/**
	 * @param amount 환불 금액. null이면 남은 금액 전부
	 * @throws TossPaymentsClient.TossApiException 토스 취소가 실패했을 때
	 */
	public Refunded refund(PaymentsRecord payment, String reason, Long amount, String idempotencyKey) {
		if (BANK_TRANSFER.equals(payment.getProvider())) {
			long balance = payment.getBalanceAmount() - (amount == null ? payment.getBalanceAmount() : amount);
			return new Refunded(balance == 0 ? "CANCELED" : "PARTIAL_CANCELED", balance, "REQUESTED", null, null);
		}
		TossPayment canceled = toss.cancel(payment.getPaymentKey(), reason, amount, idempotencyKey);
		return new Refunded(canceled.status(), canceled.balanceAmount(), "DONE", canceled.lastTransactionKey(),
				canceled.raw());
	}

}
