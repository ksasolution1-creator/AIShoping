package com.aishop.api.payment;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.common.web.ApiException;
import com.aishop.api.inventory.InventoryRepository;
import com.aishop.api.jooq.tables.records.OrderItemsRecord;
import com.aishop.api.jooq.tables.records.OrdersRecord;
import com.aishop.api.jooq.tables.records.PaymentsRecord;
import com.aishop.api.order.OrderRepository;
import com.aishop.api.order.OrderService;
import com.aishop.api.payment.TossPaymentsClient.TossApiException;
import com.aishop.api.payment.TossPaymentsClient.TossPayment;

/**
 * 결제 승인·취소·만료. 결제 수단은 토스(카드·간편결제)와 무통장입금 두 가지다.
 * 무통장입금은 운영자가 입금을 확인하면 결제완료가 되고, 환불은 운영자가 직접 송금한다.
 * 승인 실패는 주문 취소와 재고 예약 해제까지 커밋해야 하므로 예외 대신 결과(Outcome)로 돌려준다.
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

	public static final Duration PAYMENT_TTL = Duration.ofMinutes(30);

	private final OrderRepository orders;
	private final InventoryRepository inventory;
	private final TossPaymentsClient toss;
	private final Refunder refunder;

	public PaymentService(OrderRepository orders, InventoryRepository inventory, TossPaymentsClient toss,
			Refunder refunder) {
		this.orders = orders;
		this.inventory = inventory;
		this.toss = toss;
		this.refunder = refunder;
	}

	/** 승인 결과. success가 false면 code·message로 화면에 안내한다 */
	public record Outcome(boolean success, String orderNo, String code, String message) {

		static Outcome ok(String orderNo) {
			return new Outcome(true, orderNo, null, null);
		}

		static Outcome fail(String orderNo, String code, String message) {
			return new Outcome(false, orderNo, code, message);
		}

	}

	/**
	 * 결제창 인증 후 successUrl로 돌아온 값으로 결제를 승인한다.
	 * amount는 화면에서 넘어온 값이므로 DB의 주문 금액과 반드시 대조한다.
	 */
	@Transactional
	public Outcome confirm(long userId, String paymentKey, String orderNo, long amount) {
		OrdersRecord order = orders.lockByOrderNo(orderNo)
			.filter(o -> o.getUserId() != null && o.getUserId() == userId)
			.orElseThrow(() -> ApiException.notFound("주문"));

		if ("PAID".equals(order.getStatus())) {
			// 새로고침 등으로 같은 요청이 다시 온 경우
			boolean samePayment = orders.findPaymentByKey(paymentKey)
				.map(p -> p.getOrderId().equals(order.getId()))
				.orElse(false);
			return samePayment ? Outcome.ok(orderNo)
					: Outcome.fail(orderNo, "ALREADY_PAID", "이미 결제가 끝난 주문입니다.");
		}
		if (isBankTransfer(order)) {
			return Outcome.fail(orderNo, "ORDER_NOT_PAYABLE", "무통장입금 주문은 카드로 결제할 수 없습니다.");
		}
		if (!"PENDING_PAYMENT".equals(order.getStatus())) {
			return Outcome.fail(orderNo, "ORDER_NOT_PAYABLE", "결제 시간이 지나 주문이 취소되었습니다. 다시 주문해 주세요.");
		}
		if (amount != order.getPayAmount()) {
			log.warn("결제 금액 불일치: 주문 {} 주문금액 {} 요청금액 {}", orderNo, order.getPayAmount(), amount);
			cancelPendingOrder(order);
			return Outcome.fail(orderNo, "AMOUNT_MISMATCH", "결제 금액이 주문 금액과 달라 주문을 취소했습니다. 다시 주문해 주세요.");
		}

		TossPayment payment;
		try {
			payment = toss.confirm(paymentKey, orderNo, order.getPayAmount(), "confirm-" + orderNo);
		}
		catch (TossApiException ex) {
			if (ex.isUnknownResult()) {
				// 승인됐는지 모르는 상태: 토스에 다시 물어본다
				TossPayment found = findQuietly(orderNo);
				if (found != null && found.isDone()) {
					markPaid(order, found);
					return Outcome.ok(orderNo);
				}
				return Outcome.fail(orderNo, "PAYMENT_PENDING",
						"결제 결과를 확인하지 못했습니다. 잠시 후 주문 내역에서 확인해 주세요.");
			}
			cancelPendingOrder(order);
			orders.insertPayment(order.getId(), paymentKey, null, null, order.getPayAmount(), 0, "ABORTED", null, null);
			return Outcome.fail(orderNo, ex.getCode(), ex.getMessage());
		}

		if (!payment.isDone() || payment.totalAmount() != order.getPayAmount()) {
			log.error("승인 응답 이상: 주문 {} 상태 {} 금액 {}", orderNo, payment.status(), payment.totalAmount());
			return Outcome.fail(orderNo, "PAYMENT_NOT_DONE", "결제가 완료되지 않았습니다. 고객센터에 문의해 주세요.");
		}
		markPaid(order, payment);
		return Outcome.ok(orderNo);
	}

	/** 고객 취소: 입금 전이거나 발주확인 전(결제완료)까지만 가능 */
	@Transactional
	public void cancelByCustomer(long userId, String orderNo, String reason) {
		OrdersRecord order = orders.lockByOrderNo(orderNo)
			.filter(o -> o.getUserId() != null && o.getUserId() == userId)
			.orElseThrow(() -> ApiException.notFound("주문"));
		if (isAwaitingDeposit(order)) {
			cancelPendingOrder(order);
			return;
		}
		cancelPaidOrder(order, OrderService.CUSTOMER_CANCELLABLE, reason == null ? "고객 요청 취소" : reason);
	}

	/** 운영자 취소: 입금 전(미입금 취소)이거나 출고 전까지 가능. 다른 판매자 상품이 섞인 주문은 아직 부분 취소를 지원하지 않는다 */
	@Transactional
	public void cancelBySeller(long sellerId, String orderNo, String reason) {
		OrdersRecord order = lockOwnOrder(sellerId, orderNo, "부분 취소를");
		if (isAwaitingDeposit(order)) {
			cancelPendingOrder(order);
			return;
		}
		cancelPaidOrder(order, OrderService.ADMIN_CANCELLABLE, reason == null ? "판매자 취소" : reason);
	}

	/** 운영자 입금 확인: 무통장입금 대기 주문을 결제완료로 바꾼다 */
	@Transactional
	public void confirmDeposit(long sellerId, String orderNo) {
		OrdersRecord order = lockOwnOrder(sellerId, orderNo, "입금 확인을");
		if (!isAwaitingDeposit(order)) {
			throw ApiException.conflict("NOT_AWAITING_DEPOSIT", "입금을 기다리는 주문이 아닙니다.");
		}
		orders.insertBankPayment(order.getId(), order.getPayAmount());
		completePayment(order);
	}

	/** 운영자가 무통장입금 환불을 고객 계좌로 직접 보낸 뒤 송금 완료로 표시한다 */
	@Transactional
	public void completeManualRefund(long sellerId, String orderNo, long refundId) {
		OrdersRecord order = lockOwnOrder(sellerId, orderNo, "환불 처리를");
		if (!orders.completeManualRefund(order.getId(), refundId)) {
			throw ApiException.conflict("NOT_PENDING_REFUND", "송금 대기 중인 환불이 아닙니다.");
		}
	}

	/** 판매자 상품만 들어 있는 주문을 잠근다. 다른 판매자 상품이 섞여 있으면 거절 */
	private OrdersRecord lockOwnOrder(long sellerId, String orderNo, String action) {
		OrdersRecord order = orders.lockByOrderNo(orderNo).orElseThrow(() -> ApiException.notFound("주문"));
		List<OrderItemsRecord> items = orders.findItems(order.getId());
		if (items.stream().noneMatch(i -> i.getSellerId() == sellerId)) {
			throw ApiException.notFound("주문");
		}
		if (items.stream().anyMatch(i -> i.getSellerId() != sellerId)) {
			throw ApiException.conflict("MULTI_SELLER", "다른 판매자 상품이 함께 있는 주문은 아직 " + action + " 지원하지 않습니다.");
		}
		return order;
	}

	/** 결제 완료 주문 전체 취소(환불). allowedItemStatuses 상태인 상품만 있을 때 가능하다 */
	private void cancelPaidOrder(OrdersRecord lockedOrder, Set<String> allowedItemStatuses, String reason) {
		List<OrderItemsRecord> items = orders.findItems(lockedOrder.getId());
		if (items.isEmpty() || !items.stream().allMatch(i -> allowedItemStatuses.contains(i.getStatus()))) {
			throw ApiException.conflict("NOT_CANCELLABLE", "이미 준비 중이거나 발송된 상품이 있어 바로 취소할 수 없습니다. 반품으로 신청해 주세요.");
		}
		PaymentsRecord payment = orders.findDonePayment(lockedOrder.getId())
			.orElseThrow(() -> ApiException.conflict("NO_PAYMENT", "결제 정보를 찾을 수 없습니다."));

		String idempotencyKey = "cancel-" + lockedOrder.getOrderNo();
		Refunder.Refunded refunded;
		try {
			refunded = refunder.refund(payment, reason, null, idempotencyKey);
		}
		catch (TossApiException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "CANCEL_FAILED", "환불하지 못했습니다: " + ex.getMessage());
		}

		orders.insertRefund(payment.getId(), payment.getBalanceAmount(), reason, idempotencyKey,
				refunded.refundStatus(), refunded.transactionKey(), refunded.raw());
		orders.updatePayment(payment.getId(), refunded.paymentStatus(), refunded.balanceAmount(), refunded.raw());
		releaseReservations(items);
		orders.updateItemStatus(lockedOrder.getId(), null, allowedItemStatuses, "CANCELLED");
		orders.updateOrderStatus(lockedOrder.getId(), "CANCELLED", false);
	}

	/** 결제 시간이 지난 주문 하나를 정리한다 (스케줄러에서 주문마다 호출) */
	@Transactional
	public void expire(String orderNo) {
		OrdersRecord order = orders.lockByOrderNo(orderNo).orElse(null);
		if (order == null || !"PENDING_PAYMENT".equals(order.getStatus())) {
			return;
		}
		if (isBankTransfer(order)) {
			// 입금 기한까지 입금 확인이 안 된 주문
			cancelPendingOrder(order);
			return;
		}
		// 승인 요청 도중 연결이 끊겼을 수 있으니, 토스에서 이미 완료된 결제인지 확인한다
		TossPayment found = findQuietly(orderNo);
		if (found != null && found.isDone() && found.totalAmount() == order.getPayAmount()) {
			log.warn("만료 대상 주문이 토스에서 결제 완료 상태라 결제완료로 처리: {}", orderNo);
			markPaid(order, found);
			return;
		}
		cancelPendingOrder(order);
	}

	/** 결제창에서 고객이 취소했거나 실패했을 때 바로 예약을 푼다 */
	@Transactional
	public void abandon(long userId, String orderNo) {
		orders.lockByOrderNo(orderNo)
			.filter(o -> o.getUserId() != null && o.getUserId() == userId)
			.filter(o -> "PENDING_PAYMENT".equals(o.getStatus()) && !isBankTransfer(o))
			.ifPresent(this::cancelPendingOrder);
	}

	/** 웹훅: 토스에서 결제 상태가 바뀌면 다시 조회해서 맞춘다 (웹훅 내용은 믿지 않는다) */
	@Transactional
	public void syncFromToss(String paymentKey) {
		PaymentsRecord payment = orders.findPaymentByKey(paymentKey).orElse(null);
		if (payment == null) {
			return;
		}
		TossPayment latest = toss.findByPaymentKey(paymentKey);
		orders.updatePayment(payment.getId(), latest.status(), latest.balanceAmount(), latest.raw());
	}

	// ------------------------------------------------------------------

	private void markPaid(OrdersRecord order, TossPayment payment) {
		orders.insertPayment(order.getId(), payment.paymentKey(), payment.method(), payment.easyPayProvider(),
				payment.totalAmount(), payment.balanceAmount(), payment.status(), payment.approvedAt(), payment.raw());
		completePayment(order);
	}

	/** 결제 행을 만든 뒤: 주문·상품을 결제완료로 바꾸고 장바구니에서 뺀다 */
	private void completePayment(OrdersRecord order) {
		orders.updateOrderStatus(order.getId(), "PAID", true);
		orders.updateItemStatus(order.getId(), null, Set.of("PENDING_PAYMENT"), "PAID");
		List<OrderItemsRecord> items = orders.findItems(order.getId());
		if (order.getUserId() != null) {
			orders.deleteCartItems(order.getUserId(),
					items.stream().map(OrderItemsRecord::getSkuId).filter(java.util.Objects::nonNull).toList());
		}
	}

	private static boolean isBankTransfer(OrdersRecord order) {
		return Refunder.BANK_TRANSFER.equals(order.getPayMethod());
	}

	private static boolean isAwaitingDeposit(OrdersRecord order) {
		return isBankTransfer(order) && "PENDING_PAYMENT".equals(order.getStatus());
	}

	private void cancelPendingOrder(OrdersRecord order) {
		releaseReservations(orders.findItems(order.getId()));
		orders.updateItemStatus(order.getId(), null, Set.of("PENDING_PAYMENT"), "CANCELLED");
		orders.updateOrderStatus(order.getId(), "CANCELLED", false);
	}

	private void releaseReservations(List<OrderItemsRecord> items) {
		for (OrderItemsRecord item : items) {
			if (item.getSkuId() != null && item.getFulfillLocationId() != null) {
				inventory.release(item.getSkuId(), item.getFulfillLocationId(), item.getQty());
			}
		}
	}

	private TossPayment findQuietly(String orderNo) {
		if (!toss.isConfigured()) {
			return null;
		}
		try {
			return toss.findByOrderId(orderNo);
		}
		catch (TossApiException ex) {
			return null;
		}
	}

}
