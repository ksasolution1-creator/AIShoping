package com.aishop.api.payment;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.aishop.api.order.OrderRepository;

/** 결제하지 않고 30분이 지난 주문을 취소하고 재고 예약을 푼다 */
@Component
public class PendingOrderExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(PendingOrderExpiryJob.class);

	private final OrderRepository orders;
	private final PaymentService paymentService;
	private final TransactionTemplate tx;

	public PendingOrderExpiryJob(OrderRepository orders, PaymentService paymentService, TransactionTemplate tx) {
		this.orders = orders;
		this.paymentService = paymentService;
		this.tx = tx;
	}

	@Scheduled(fixedDelayString = "${aishop.payment.expiry-check-ms:60000}", initialDelay = 30_000)
	public void run() {
		List<String> expired = tx.execute(status -> orders.lockExpiredPending(PaymentService.PAYMENT_TTL, 50));
		if (expired == null) {
			return;
		}
		for (String orderNo : expired) {
			try {
				paymentService.expire(orderNo);
			}
			catch (RuntimeException ex) {
				log.error("결제 대기 주문 만료 처리 실패: {}", orderNo, ex);
			}
		}
	}

}
