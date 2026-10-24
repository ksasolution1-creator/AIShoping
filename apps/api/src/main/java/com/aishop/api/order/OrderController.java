package com.aishop.api.order;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

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

	public OrderController(OrderService orderService, PaymentService paymentService, TossPaymentsClient toss,
			FieldCipher cipher) {
		this.orderService = orderService;
		this.paymentService = paymentService;
		this.toss = toss;
		this.cipher = cipher;
	}

	/** 결제위젯을 띄우는 데 필요한 값. customerKey는 회원 ID를 감춘 고객 식별값 */
	@GetMapping("/api/checkout/config")
	public Map<String, Object> checkoutConfig(@AuthenticationPrincipal AuthUser user) {
		return Map.of("paymentReady", toss.isConfigured(), "clientKey", toss.clientKey(), "customerKey",
				cipher.opaqueId("toss-customer", user.id()));
	}

	public record PreviewRequest(@NotEmpty @Size(max = 50) @Valid List<OrderLineRequest> items) {
	}

	@PostMapping("/api/checkout/preview")
	public OrderService.Preview preview(@Valid @RequestBody PreviewRequest body) {
		return orderService.preview(body.items());
	}

	@PostMapping("/api/orders")
	@ResponseStatus(HttpStatus.CREATED)
	public CheckoutInfo create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateOrder body) {
		if (!toss.isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_READY", "결제 준비가 아직 끝나지 않았습니다.");
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

	@PostMapping("/api/orders/{orderNo}/cancel")
	public OrderDetail cancel(@AuthenticationPrincipal AuthUser user, @PathVariable String orderNo,
			@Valid @RequestBody(required = false) CancelOrder body) {
		paymentService.cancelByCustomer(user.id(), orderNo, body == null ? null : body.reason());
		return orderService.myOrder(user.id(), orderNo);
	}

}
