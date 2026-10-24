package com.aishop.api.claim;

import static com.aishop.api.jooq.Tables.CARRIERS;
import static com.aishop.api.jooq.Tables.CLAIMS;
import static com.aishop.api.jooq.Tables.ORDERS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;

import java.util.List;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.catalog.ProductRepository;
import com.aishop.api.claim.ClaimViews.ClaimView;
import com.aishop.api.claim.ClaimViews.CompleteReturn;
import com.aishop.api.claim.ClaimViews.ReturnReason;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.inventory.InventoryRepository;
import com.aishop.api.jooq.tables.records.ClaimsRecord;
import com.aishop.api.jooq.tables.records.OrderItemsRecord;
import com.aishop.api.jooq.tables.records.OrdersRecord;
import com.aishop.api.jooq.tables.records.PaymentsRecord;
import com.aishop.api.jooq.tables.records.SkusRecord;
import com.aishop.api.order.OrderRepository;
import com.aishop.api.payment.TossPaymentsClient;
import com.aishop.api.payment.TossPaymentsClient.TossApiException;
import com.aishop.api.payment.TossPaymentsClient.TossPayment;

/**
 * 상품별 취소와 반품.
 * - 취소: 출고 전 상품을 바로 취소하고 그 금액만 부분 환불, 재고 예약 해제
 * - 반품: 신청 → (승인·회수) → 검수 후 상태등급을 다시 매겨 재입고 → 부분 환불
 * 반품 배송비는 단순변심이면 고객 부담(환불액에서 차감), 그 외 사유는 판매자 부담.
 */
@Service
public class ClaimService {

	/** 고객이 직접 상품을 취소할 수 있는 상태 (발주확인 전) */
	public static final Set<String> CUSTOMER_CANCELLABLE = Set.of("PAID");
	/** 운영자가 상품을 취소할 수 있는 상태 (출고 전) */
	public static final Set<String> SELLER_CANCELLABLE = Set.of("PAID", "PREPARING");
	/** 반품 신청이 가능한 상태 */
	/** 구매확정 전까지만 온라인으로 반품 신청을 받는다 (확정 후 하자는 고객센터로) */
	public static final Set<String> RETURNABLE = Set.of("SHIPPED", "DELIVERED");

	private static final Set<String> CLOSED_ITEM = Set.of("CANCELLED", "RETURNED");

	private final ClaimRepository claims;
	private final OrderRepository orders;
	private final ProductRepository products;
	private final InventoryRepository inventory;
	private final TossPaymentsClient toss;

	public ClaimService(ClaimRepository claims, OrderRepository orders, ProductRepository products,
			InventoryRepository inventory, TossPaymentsClient toss) {
		this.claims = claims;
		this.orders = orders;
		this.products = products;
		this.inventory = inventory;
		this.toss = toss;
	}

	// ------------------------------------------------------------------
	// 상품별 취소
	// ------------------------------------------------------------------

	@Transactional
	public ClaimView cancelItemByCustomer(long userId, String orderNo, long itemId, String reason) {
		OrdersRecord order = lockCustomerOrder(userId, orderNo);
		OrderItemsRecord item = requireItem(order, itemId, null);
		return cancelItem(order, item, CUSTOMER_CANCELLABLE, "CUSTOMER", userId, blankOr(reason, "고객 요청 취소"));
	}

	@Transactional
	public ClaimView cancelItemBySeller(long actorId, long sellerId, String orderNo, long itemId, String reason) {
		OrdersRecord order = orders.lockByOrderNo(orderNo).orElseThrow(() -> ApiException.notFound("주문"));
		OrderItemsRecord item = requireItem(order, itemId, sellerId);
		return cancelItem(order, item, SELLER_CANCELLABLE, "SELLER", actorId, blankOr(reason, "판매자 취소"));
	}

	private ClaimView cancelItem(OrdersRecord order, OrderItemsRecord item, Set<String> allowed, String fault,
			long actorId, String reason) {
		if (!allowed.contains(item.getStatus())) {
			throw ApiException.conflict("NOT_CANCELLABLE", "'" + item.getProductName()
					+ "'은(는) 이미 준비 중이거나 발송되어 바로 취소할 수 없습니다. 받은 뒤 반품을 신청해 주세요.");
		}
		if (claims.hasOpenClaim(item.getId())) {
			throw ApiException.conflict("CLAIM_IN_PROGRESS", "이미 처리 중인 취소·반품이 있습니다.");
		}
		PaymentsRecord payment = requirePayment(order);
		boolean lastOpenItem = isLastOpenItem(order, item);
		// 마지막 남은 상품까지 취소하면 배송비를 포함해 남은 금액 전부 환불
		long amount = lastOpenItem ? payment.getBalanceAmount() : lineTotal(item);

		ClaimsRecord claim = new ClaimsRecord();
		claim.setOrderItemId(item.getId());
		claim.setType("CANCEL");
		claim.setQty(item.getQty());
		claim.setReasonCode("CANCEL");
		claim.setReasonDetail(reason);
		claim.setFault(fault);
		claim.setReturnShippingFee(0L);
		claim.setStatus("COMPLETED");
		claim.setPrevItemStatus(item.getStatus());
		claim.setRequestedBy(actorId);
		claim.setProcessedBy(actorId);
		long claimId = claims.insert(claim);

		long refunded = refund(payment, claimId, amount, reason);
		if (item.getSkuId() != null && item.getFulfillLocationId() != null) {
			inventory.release(item.getSkuId(), item.getFulfillLocationId(), item.getQty());
		}
		setItemStatus(item, "CANCELLED");
		if (lastOpenItem && allItemsIn(order, Set.of("CANCELLED"))) {
			orders.updateOrderStatus(order.getId(), "CANCELLED", false);
		}
		finish(claimId, refunded);
		return view(claimId);
	}

	// ------------------------------------------------------------------
	// 반품
	// ------------------------------------------------------------------

	@Transactional
	public ClaimView requestReturn(long userId, String orderNo, long itemId, ReturnReason reason, String detail) {
		OrdersRecord order = lockCustomerOrder(userId, orderNo);
		OrderItemsRecord item = requireItem(order, itemId, null);
		if (!RETURNABLE.contains(item.getStatus())) {
			throw ApiException.conflict("NOT_RETURNABLE", "CONFIRMED".equals(item.getStatus())
					? "구매확정한 상품은 고객센터로 문의해 주세요."
					: "배송이 시작된 상품만 반품을 신청할 수 있습니다.");
		}
		if (claims.hasOpenClaim(item.getId())) {
			throw ApiException.conflict("CLAIM_IN_PROGRESS", "이미 처리 중인 반품이 있습니다.");
		}

		// 단순변심: 고객 부담. 처음에 무료배송이었으면 왕복, 배송비를 냈으면 편도
		long returnFee = 0;
		if ("CUSTOMER".equals(reason.fault())) {
			long oneWay = claims.findShippingFee(item.getSkuId());
			returnFee = order.getShippingFee() == 0 ? oneWay * 2 : oneWay;
		}

		ClaimsRecord claim = new ClaimsRecord();
		claim.setOrderItemId(item.getId());
		claim.setType("RETURN");
		claim.setQty(item.getQty());
		claim.setReasonCode(reason.name());
		claim.setReasonDetail(detail == null || detail.isBlank() ? null : detail.strip());
		claim.setFault(reason.fault());
		claim.setReturnShippingFee(Math.min(returnFee, lineTotal(item)));
		claim.setStatus("REQUESTED");
		claim.setPrevItemStatus(item.getStatus());
		claim.setRequestedBy(userId);
		long claimId = claims.insert(claim);
		setItemStatus(item, "RETURN_REQUESTED");
		return view(claimId);
	}

	/** 고객이 반품 신청을 철회 (승인 전까지) */
	@Transactional
	public ClaimView withdraw(long userId, long claimId) {
		ClaimsRecord claim = claims.lock(claimId).orElseThrow(() -> ApiException.notFound("반품 신청"));
		OrderItemsRecord item = itemOf(claim);
		OrdersRecord order = orders.findByOrderNo(orderNoOf(claim)).orElseThrow();
		if (order.getUserId() == null || order.getUserId() != userId) {
			throw ApiException.notFound("반품 신청");
		}
		if (!"REQUESTED".equals(claim.getStatus())) {
			throw ApiException.conflict("CANNOT_WITHDRAW", "이미 처리가 시작되어 철회할 수 없습니다. 고객센터에 문의해 주세요.");
		}
		claim.setStatus("WITHDRAWN");
		claims.update(claim);
		setItemStatus(item, claim.getPrevItemStatus());
		return view(claimId);
	}

	/** 반품 승인: 회수 진행 (회수 송장은 있으면 기록) */
	@Transactional
	public ClaimView approve(long actorId, long sellerId, long claimId, Long carrierId, String trackingNo) {
		ClaimsRecord claim = lockSellerClaim(sellerId, claimId);
		requireStatus(claim, Set.of("REQUESTED"), "승인할 수 없는 상태입니다.");
		if (carrierId != null && !orders.carrierExists(carrierId)) {
			throw ApiException.badRequest("INVALID_CARRIER", "택배사를 다시 선택해 주세요.");
		}
		claim.setStatus("COLLECTING");
		claim.setProcessedBy(actorId);
		claim.setPickupCarrierId(carrierId);
		claim.setPickupTrackingNo(trackingNo == null || trackingNo.isBlank() ? null : trackingNo.replace("-", ""));
		claims.update(claim);
		setItemStatus(itemOf(claim), "RETURNING");
		return view(claimId);
	}

	@Transactional
	public ClaimView reject(long actorId, long sellerId, long claimId, String reason) {
		ClaimsRecord claim = lockSellerClaim(sellerId, claimId);
		requireStatus(claim, Set.of("REQUESTED", "COLLECTING", "INSPECTING"), "거절할 수 없는 상태입니다.");
		claim.setStatus("REJECTED");
		claim.setRejectReason(reason.strip());
		claim.setProcessedBy(actorId);
		claims.update(claim);
		claims.touchCompleted(claimId);
		setItemStatus(itemOf(claim), claim.getPrevItemStatus());
		return view(claimId);
	}

	/**
	 * 회수한 상품 검수 완료: 상태등급을 다시 매겨 재입고(또는 폐기)하고 환불한다.
	 * 같은 상품·옵션에 그 등급 SKU가 없으면 '판매중지' SKU를 새로 만들어 넣는다 (가격 확인 후 판매).
	 */
	@Transactional
	public ClaimView completeReturn(long actorId, long sellerId, long claimId, CompleteReturn request) {
		ClaimsRecord claim = lockSellerClaim(sellerId, claimId);
		requireStatus(claim, Set.of("REQUESTED", "COLLECTING", "INSPECTING"), "이미 처리가 끝난 반품입니다.");
		OrderItemsRecord item = itemOf(claim);
		OrdersRecord order = orders.lockByOrderNo(orderNoOf(claim)).orElseThrow();
		PaymentsRecord payment = requirePayment(order);

		// 재입고
		String grade = request.inspectionGrade();
		if (!"DISPOSE".equals(grade) && item.getSkuId() != null) {
			long locationId = restockLocation(sellerId, item, request.restockLocationId());
			long skuId = restockSku(item.getSkuId(), grade, claimId);
			inventory.changeOnHand(skuId, locationId, item.getQty());
			orders.insertMovement(skuId, locationId, item.getQty(), "RETURN_IN", "CLAIM", claimId, actorId);
			claim.setRestockSkuId(skuId);
			claim.setRestockLocationId(locationId);
		}

		// 환불: 상품 금액 - 고객 부담 반품 배송비. 판매자 귀책으로 마지막 상품까지 반품되면 배송비도 환불
		boolean lastOpenItem = isLastOpenItem(order, item);
		long amount = lineTotal(item) - claim.getReturnShippingFee();
		if (lastOpenItem && "SELLER".equals(claim.getFault())) {
			amount = payment.getBalanceAmount();
		}
		long refunded = refund(payment, claimId, Math.max(amount, 0), "반품 환불 (" + ReturnReason.labelOf(claim.getReasonCode()) + ")");

		claim.setInspectionGrade(grade);
		claim.setStatus("COMPLETED");
		claim.setProcessedBy(actorId);
		if (request.note() != null && !request.note().isBlank()) {
			String detail = claim.getReasonDetail() == null ? "" : claim.getReasonDetail() + "\n";
			claim.setReasonDetail(detail + "[검수] " + request.note().strip());
		}
		claims.update(claim);
		setItemStatus(item, "RETURNED");
		finish(claimId, refunded);
		return view(claimId);
	}

	private long restockSku(long originalSkuId, String grade, long claimId) {
		SkusRecord original = claims.findSku(originalSkuId).orElseThrow();
		if (original.getConditionGrade().equals(grade)) {
			return original.getId();
		}
		return claims.findSiblingSku(original.getProductId(), original.getOptionValues(), grade)
			.map(SkusRecord::getId)
			.orElseGet(() -> {
				SkusRecord sku = new SkusRecord();
				sku.setProductId(original.getProductId());
				String code = "P" + original.getProductId() + "-R" + claimId;
				sku.setSkuCode(claims.skuCodeExists(code) ? code + "-" + System.currentTimeMillis() % 10000 : code);
				sku.setOptionValues(original.getOptionValues());
				sku.setConditionGrade(grade);
				sku.setConditionNote("반품 검수 재입고 - 가격과 상태 설명을 확인한 뒤 판매로 바꿔 주세요.");
				sku.setPrice(original.getPrice());
				sku.setListPrice(original.getListPrice());
				sku.setStatus("INACTIVE");
				return claims.insertSku(sku);
			});
	}

	private long restockLocation(long sellerId, OrderItemsRecord item, Long requested) {
		if (requested != null) {
			if (!products.locationBelongsTo(sellerId, requested)) {
				throw ApiException.badRequest("INVALID_LOCATION", "재입고할 위치를 다시 선택해 주세요.");
			}
			return requested;
		}
		if (item.getFulfillLocationId() != null) {
			return item.getFulfillLocationId();
		}
		return products.findDefaultWarehouseId(sellerId)
			.orElseThrow(() -> ApiException.conflict("NO_WAREHOUSE", "기본 창고가 없습니다."));
	}

	// ------------------------------------------------------------------
	// 조회
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<ClaimView> sellerClaims(long sellerId, List<String> statuses, String type, int page, int size) {
		Condition condition = ClaimRepository.forSeller(sellerId, statuses, type);
		List<ClaimView> items = claims.findViews(condition, size, page * size).stream().map(ClaimService::toView).toList();
		return new PageResponse<>(items, page, size, claims.count(condition));
	}

	@Transactional(readOnly = true)
	public List<ClaimView> orderClaims(long orderId) {
		return claims.findViews(ClaimRepository.forOrder(orderId), 100, 0).stream().map(ClaimService::toView).toList();
	}

	private ClaimView view(long claimId) {
		return claims.findViews(ClaimRepository.byId(claimId), 1, 0).stream().map(ClaimService::toView).findFirst()
			.orElseThrow();
	}

	private static ClaimView toView(Record r) {
		long lineTotal = r.get(ORDER_ITEMS.UNIT_PRICE) * r.get(CLAIMS.QTY) - r.get(ORDER_ITEMS.DISCOUNT);
		return new ClaimView(r.get(CLAIMS.ID), r.get(CLAIMS.TYPE), r.get(CLAIMS.STATUS),
				r.get(ORDERS.ORDER_NO), r.get(CLAIMS.ORDER_ITEM_ID),
				r.get(ORDERS.ORDERER_NAME), r.get(ORDER_ITEMS.PRODUCT_NAME),
				r.get(ORDER_ITEMS.OPTION_TEXT), r.get(ORDER_ITEMS.CONDITION_GRADE), r.get(CLAIMS.QTY), lineTotal,
				r.get(CLAIMS.REASON_CODE), ReturnReason.labelOf(r.get(CLAIMS.REASON_CODE)), r.get(CLAIMS.REASON_DETAIL),
				r.get(CLAIMS.FAULT), r.get(CLAIMS.RETURN_SHIPPING_FEE), r.get(CLAIMS.REFUND_AMOUNT),
				r.get(CLAIMS.INSPECTION_GRADE), r.get(CLAIMS.REJECT_REASON),
				r.get(CARRIERS.NAME), r.get(CLAIMS.PICKUP_TRACKING_NO),
				r.get(CLAIMS.REQUESTED_AT), r.get(CLAIMS.COMPLETED_AT));
	}

	// ------------------------------------------------------------------

	private long refund(PaymentsRecord payment, long claimId, long amount, String reason) {
		long refundable = Math.min(amount, payment.getBalanceAmount());
		if (refundable <= 0) {
			return 0;
		}
		String idempotencyKey = "claim-" + claimId;
		TossPayment canceled;
		try {
			canceled = toss.cancel(payment.getPaymentKey(), reason, refundable, idempotencyKey);
		}
		catch (TossApiException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED", "환불하지 못했습니다: " + ex.getMessage());
		}
		claims.insertRefund(payment.getId(), claimId, refundable, reason, idempotencyKey, canceled.lastTransactionKey(),
				canceled.raw());
		orders.updatePayment(payment.getId(), canceled.status(), canceled.balanceAmount(), canceled.raw());
		return refundable;
	}

	private void finish(long claimId, long refunded) {
		ClaimsRecord claim = claims.lock(claimId).orElseThrow();
		claim.setRefundAmount(refunded);
		claims.update(claim);
		claims.touchCompleted(claimId);
	}

	private OrdersRecord lockCustomerOrder(long userId, String orderNo) {
		return orders.lockByOrderNo(orderNo)
			.filter(o -> o.getUserId() != null && o.getUserId() == userId)
			.orElseThrow(() -> ApiException.notFound("주문"));
	}

	private OrderItemsRecord requireItem(OrdersRecord order, long itemId, Long sellerId) {
		return orders.findItems(order.getId()).stream()
			.filter(i -> i.getId() == itemId)
			.filter(i -> sellerId == null || i.getSellerId() == sellerId)
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("주문 상품"));
	}

	private ClaimsRecord lockSellerClaim(long sellerId, long claimId) {
		ClaimsRecord claim = claims.lock(claimId).orElseThrow(() -> ApiException.notFound("클레임"));
		if (itemOf(claim).getSellerId() != sellerId) {
			throw ApiException.notFound("클레임");
		}
		return claim;
	}

	private OrderItemsRecord itemOf(ClaimsRecord claim) {
		return orders.findItem(claim.getOrderItemId()).orElseThrow();
	}

	private String orderNoOf(ClaimsRecord claim) {
		return orders.findOrderNoOfItem(claim.getOrderItemId()).orElseThrow();
	}

	private PaymentsRecord requirePayment(OrdersRecord order) {
		return orders.findDonePayment(order.getId())
			.orElseThrow(() -> ApiException.conflict("NO_PAYMENT", "결제 정보를 찾을 수 없습니다."));
	}

	private boolean isLastOpenItem(OrdersRecord order, OrderItemsRecord item) {
		return orders.findItems(order.getId()).stream()
			.filter(i -> i.getId().longValue() != item.getId())
			.allMatch(i -> CLOSED_ITEM.contains(i.getStatus()));
	}

	private boolean allItemsIn(OrdersRecord order, Set<String> statuses) {
		return orders.findItems(order.getId()).stream().allMatch(i -> statuses.contains(i.getStatus()));
	}

	private void setItemStatus(OrderItemsRecord item, String status) {
		orders.setItemStatus(item.getId(), status);
		item.setStatus(status);
	}

	private static void requireStatus(ClaimsRecord claim, Set<String> allowed, String message) {
		if (!allowed.contains(claim.getStatus())) {
			throw ApiException.conflict("INVALID_CLAIM_STATUS", message);
		}
	}

	private static long lineTotal(OrderItemsRecord item) {
		return item.getUnitPrice() * item.getQty() - item.getDiscount();
	}

	private static String blankOr(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.strip();
	}

}
