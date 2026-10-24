package com.aishop.api.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * 토스페이먼츠 웹훅 (개발자센터 > 웹훅에 이 주소를 등록한다).
 * 내용은 믿지 않고 paymentKey로 토스에 다시 조회해서 상태를 맞춘다. 항상 200을 돌려줘야 재전송이 멈춘다.
 */
@RestController
public class PaymentWebhookController {

	private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

	private final PaymentService paymentService;

	public PaymentWebhookController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/api/payments/webhook")
	public void receive(@RequestBody JsonNode body) {
		String paymentKey = body.path("data").path("paymentKey").asString(null);
		if (paymentKey == null) {
			return;
		}
		try {
			paymentService.syncFromToss(paymentKey);
		}
		catch (RuntimeException ex) {
			log.error("웹훅 처리 실패: {}", paymentKey, ex);
		}
	}

}
