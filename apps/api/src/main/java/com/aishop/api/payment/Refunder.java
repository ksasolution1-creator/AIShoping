package com.aishop.api.payment;

import org.springframework.stereotype.Component;

import com.aishop.api.common.web.ApiException;
import com.aishop.api.jooq.tables.records.OrdersRecord;
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

	/**
	 * 입금 후 무통장입금 주문을 고객이 취소·반품하려면 돌려받을 계좌가 있어야 한다.
	 * 입금 전 주문과 카드 주문은 확인하지 않는다.
	 */
	public static void requireRefundAccount(OrdersRecord order) {
		boolean paidByBank = BANK_TRANSFER.equals(order.getPayMethod()) && !"PENDING_PAYMENT".equals(order.getStatus());
		if (paidByBank && order.getRefundAccountNoEnc() == null) {
			throw ApiException.badRequest("REFUND_ACCOUNT_REQUIRED", "환불받을 계좌를 먼저 입력해 주세요.");
		}
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
