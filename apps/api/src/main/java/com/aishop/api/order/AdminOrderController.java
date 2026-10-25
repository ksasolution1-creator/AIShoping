package com.aishop.api.order;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.order.OrderRepository.CarrierOption;
import com.aishop.api.order.OrderViews.CancelOrder;
import com.aishop.api.order.OrderViews.OrderDetail;
import com.aishop.api.order.OrderViews.OrderSummary;
import com.aishop.api.order.OrderViews.ShipOrder;
import com.aishop.api.payment.PaymentService;

/** 운영자 주문 처리: 조회 → (무통장입금 확인) → 발주확인 → 송장 등록(출고) / 취소 */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

	private final OrderService orderService;
	private final PaymentService paymentService;
	private final AdminContext admin;

	public AdminOrderController(OrderService orderService, PaymentService paymentService, AdminContext admin) {
		this.orderService = orderService;
		this.paymentService = paymentService;
		this.admin = admin;
	}

	@GetMapping("/orders")
	public PageResponse<OrderSummary> list(@RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
		return orderService.sellerOrders(admin.sellerId(), status == null || status.isBlank() ? null : status, q,
				Math.max(page, 0), 30);
	}

	@GetMapping("/orders/{orderNo}")
	public OrderDetail detail(@PathVariable String orderNo, HttpServletRequest request) {
		return orderService.sellerOrder(admin.user(), admin.sellerId(), orderNo, request.getRemoteAddr());
	}

	/** 회사 계좌에 입금된 것을 확인하고 결제완료로 바꾼다 */
	@PostMapping("/orders/{orderNo}/confirm-deposit")
	public OrderDetail confirmDeposit(@PathVariable String orderNo, HttpServletRequest request) {
		paymentService.confirmDeposit(admin.sellerId(), orderNo);
		return orderService.sellerOrder(admin.user(), admin.sellerId(), orderNo, request.getRemoteAddr());
	}

	/** 무통장입금 환불을 고객 계좌로 보낸 뒤 송금 완료로 표시한다 */
	@PostMapping("/orders/{orderNo}/refunds/{refundId}/complete")
	public OrderDetail completeRefund(@PathVariable String orderNo, @PathVariable long refundId,
			HttpServletRequest request) {
		paymentService.completeManualRefund(admin.sellerId(), orderNo, refundId);
		return orderService.sellerOrder(admin.user(), admin.sellerId(), orderNo, request.getRemoteAddr());
	}

	@PostMapping("/orders/{orderNo}/accept")
	public OrderDetail accept(@PathVariable String orderNo) {
		return orderService.acceptOrder(admin.sellerId(), orderNo);
	}

	@PostMapping("/orders/{orderNo}/ship")
	public OrderDetail ship(@PathVariable String orderNo, @Valid @RequestBody ShipOrder body) {
		return orderService.ship(admin.user().id(), admin.sellerId(), orderNo, body.carrierId(), body.trackingNo());
	}

	@PostMapping("/orders/{orderNo}/cancel")
	public OrderDetail cancel(@PathVariable String orderNo, @Valid @RequestBody(required = false) CancelOrder body,
			HttpServletRequest request) {
		paymentService.cancelBySeller(admin.sellerId(), orderNo, body == null ? null : body.reason());
		return orderService.sellerOrder(admin.user(), admin.sellerId(), orderNo, request.getRemoteAddr());
	}

	@GetMapping("/carriers")
	public List<CarrierOption> carriers() {
		return orderService.carriers();
	}

}
