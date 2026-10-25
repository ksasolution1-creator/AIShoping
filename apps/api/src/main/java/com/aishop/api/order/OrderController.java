package com.aishop.api.order;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.auth.AuthUser;
import com.aishop.api.common.crypto.FieldCipher;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.order.OrderViews.CancelOrder;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.ConfirmPayment;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.order.OrderViews.OrderDetail;
import com.aishop.api.order.OrderViews.OrderSummary;
import com.aishop.api.payment.BankAccount;
import com.aishop.api.payment.PaymentService;
import com.aishop.api.payment.PaymentService.Outcome;
import com.aishop.api.payment.TossPaymentsClient;

/** 고객 주문·결제 (로그인 필요) */
@RestController
public class OrderController {

	private final OrderService orderService;
	private final PaymentService paymentService;
	private final TossPaymentsClient toss;
	private final FieldCipher cipher;
	private final BankAccount bank;
	private final boolean cardEnabled;

	public OrderController(OrderService orderService, PaymentService paymentService, TossPaymentsClient toss,
			FieldCipher cipher, BankAccount bank, @Value("${aishop.payment.card-enabled:false}") boolean cardEnabled) {
		this.orderService = orderService;
		this.paymentService = paymentService;
		this.toss = toss;
		this.cipher = cipher;
		this.bank = bank;
		this.cardEnabled = cardEnabled;
	}

	private boolean cardReady() {
		return cardEnabled && toss.isConfigured();
	}

	/**
	 * 주문서의 결제 수단 설정. cardReady면 토스 결제위젯을, bankReady면 무통장입금 계좌를 보여준다.
	 * customerKey는 회원 ID를 감춘 고객 식별값
	 */
	@GetMapping("/api/checkout/config")
	public Map<String, Object> checkoutConfig(@AuthenticationPrincipal AuthUser user) {
		return Map.of("cardReady", cardReady(), "clientKey", toss.clientKey(), "customerKey",
				cipher.opaqueId("toss-customer", user.id()), "bankReady", bank.isConfigured(), "bank",
				Map.of("bankName", bank.bankName(), "accountNo", bank.accountNo(), "accountHolder", bank.holder(),
						"depositHours", bank.depositWindow().toHours()));
	}

	public record PreviewRequest(@NotEmpty @Size(max = 50) @Valid List<OrderLineRequest> items) {
	}

	@PostMapping("/api/checkout/preview")
	public OrderService.Preview preview(@AuthenticationPrincipal AuthUser user,
			@Valid @RequestBody PreviewRequest body) {
		return orderService.preview(user == null ? null : user.id(), body.items());
	}

	@PostMapping("/api/orders")
	@ResponseStatus(HttpStatus.CREATED)
	public CheckoutInfo create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateOrder body) {
		boolean ready = body.bankTransfer() ? bank.isConfigured() : cardReady();
		if (!ready) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_READY", "선택한 결제 수단은 지금 사용할 수 없습니다.");
		}
		return orderService.createOrder(user, body);
	}

	@PostMapping("/api/payments/confirm")
	public OrderDetail confirm(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ConfirmPayment body) {
		Outcome outcome = paymentService.confirm(user.id(), body.paymentKey(), body.orderId(), body.amount());
		if (!outcome.success()) {
			HttpStatus status = "PAYMENT_PENDING".equals(outcome.code()) ? HttpStatus.ACCEPTED : HttpStatus.BAD_REQUEST;
			throw new ApiException(status, outcome.code(), outcome.message());
		}
		return orderService.myOrder(user.id(), outcome.orderNo());
	}

	/** 결제창에서 취소·실패했을 때 재고 예약을 바로 푼다 */
	@PostMapping("/api/orders/{orderNo}/abandon")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void abandon(@AuthenticationPrincipal AuthUser user, @PathVariable String orderNo) {
		paymentService.abandon(user.id(), orderNo);
	}

	@GetMapping("/api/orders")
	public PageResponse<OrderSummary> myOrders(@AuthenticationPrincipal AuthUser user,
			@RequestParam(defaultValue = "0") int page) {
		return orderService.myOrders(user.id(), Math.max(page, 0), 20);
	}

	@GetMapping("/api/orders/{orderNo}")
	public OrderDetail myOrder(@AuthenticationPrincipal AuthUser user, @PathVariable String orderNo) {
		return orderService.myOrder(user.id(), orderNo);
	}

	@PostMapping("/api/orders/{orderNo}/items/{itemId}/confirm")
	public OrderDetail confirmPurchase(@AuthenticationPrincipal AuthUser user, @PathVariable String orderNo,
			@PathVariable long itemId) {
		return orderService.confirmPurchase(user.id(), orderNo, itemId);
	}

	@PostMapping("/api/orders/{orderNo}/cancel")
	public OrderDetail cancel(@AuthenticationPrincipal AuthUser user, @PathVariable String orderNo,
			@Valid @RequestBody(required = false) CancelOrder body) {
		paymentService.cancelByCustomer(user.id(), orderNo, body == null ? null : body.reason());
		return orderService.myOrder(user.id(), orderNo);
	}

}
