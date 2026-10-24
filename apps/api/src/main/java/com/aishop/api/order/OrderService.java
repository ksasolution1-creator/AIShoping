package com.aishop.api.order;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.auth.AuthUser;
import com.aishop.api.claim.ClaimService;
import com.aishop.api.claim.ClaimViews.ClaimView;
import com.aishop.api.common.crypto.FieldCipher;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.inventory.InventoryRepository;
import com.aishop.api.jooq.tables.records.OrderItemsRecord;
import com.aishop.api.jooq.tables.records.OrdersRecord;
import com.aishop.api.order.OrderRepository.CarrierOption;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderDetail;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.order.OrderViews.OrderLineView;
import com.aishop.api.order.OrderViews.OrderSummary;
import com.aishop.api.order.OrderViews.PaymentView;
import com.aishop.api.order.OrderViews.Receiver;
import com.aishop.api.order.OrderViews.ShipmentView;
import com.aishop.api.payment.TossPaymentsClient;

@Service
public class OrderService {

	/** 고객이 직접 취소할 수 있는 상태 (발주확인 전) */
	public static final Set<String> CUSTOMER_CANCELLABLE = Set.of("PAID");
	/** 운영자가 취소할 수 있는 상태 (출고 전) */
	public static final Set<String> ADMIN_CANCELLABLE = Set.of("PAID", "PREPARING");

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final String ORDER_NO_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private final OrderRepository orders;
	private final PurchaseLineRepository purchaseLines;
	private final InventoryRepository inventory;
	private final FieldCipher cipher;
	private final TossPaymentsClient toss;
	private final ClaimService claimService;
	private final SecureRandom random = new SecureRandom();

	public OrderService(OrderRepository orders, PurchaseLineRepository purchaseLines, InventoryRepository inventory,
			FieldCipher cipher, TossPaymentsClient toss, ClaimService claimService) {
		this.orders = orders;
		this.purchaseLines = purchaseLines;
		this.inventory = inventory;
		this.cipher = cipher;
		this.toss = toss;
		this.claimService = claimService;
	}

	// ------------------------------------------------------------------
	// 주문서 → 결제 대기 주문
	// ------------------------------------------------------------------

	/**
	 * 결제 대기 주문을 만들고 재고를 예약한다. 금액은 서버가 계산한 값만 쓴다.
	 * 결제창은 여기서 돌려준 orderNo와 amount로 띄운다.
	 */
	@Transactional
	public CheckoutInfo createOrder(AuthUser user, CreateOrder request) {
		Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
		for (OrderLineRequest line : request.items()) {
			qtyBySku.merge(line.skuId(), line.qty(), Integer::sum);
		}
		Map<Long, PurchaseLine> found = purchaseLines.findBySkuIds(qtyBySku.keySet());

		List<PurchaseLine> lines = new ArrayList<>();
		for (Map.Entry<Long, Integer> entry : qtyBySku.entrySet()) {
			PurchaseLine line = found.get(entry.getKey());
			if (line == null || !line.purchasable()) {
				throw ApiException.badRequest("NOT_PURCHASABLE", "판매가 끝난 상품이 있습니다. 장바구니를 확인해 주세요.");
			}
			if (line.available() < entry.getValue()) {
				throw ApiException.conflict("OUT_OF_STOCK",
						"'" + line.productName() + "' 남은 수량이 " + Math.max(line.available(), 0) + "개입니다.");
			}
			lines.add(line.withQty(entry.getValue()));
		}
		OrderPricing.Summary summary = OrderPricing.calculate(lines);

		String orderNo = newOrderNo();
		OrdersRecord order = new OrdersRecord();
		order.setOrderNo(orderNo);
		order.setChannel("OWN");
		order.setUserId(user.id());
		order.setOrdererName(user.name());
		order.setOrdererEmail(user.email());
		order.setReceiverName(request.receiverName().strip());
		order.setReceiverPhoneEnc(cipher.encrypt(normalizePhone(request.receiverPhone())));
		order.setReceiverZipcode(request.zipcode());
		order.setReceiverAddress1Enc(cipher.encrypt(request.address1().strip()));
		order.setReceiverAddress2Enc(cipher.encrypt(blankToNull(request.address2())));
		order.setDeliveryMemo(blankToNull(request.deliveryMemo()));
		order.setItemsTotal(summary.itemsTotal());
		order.setShippingFee(summary.shippingFee());
		order.setDiscountTotal(summary.discountTotal());
		order.setPayAmount(summary.payAmount());
		order.setStatus("PENDING_PAYMENT");
		long orderId = orders.insertOrder(order);

		for (PurchaseLine line : lines) {
			long locationId = reserve(line);
			OrderItemsRecord item = new OrderItemsRecord();
			item.setOrderId(orderId);
			item.setSellerId(line.sellerId());
			item.setSkuId(line.skuId());
			item.setProductName(line.productName());
			item.setOptionText(line.optionText().isEmpty() ? null : line.optionText());
			item.setConditionGrade(line.grade().name());
			item.setQty(line.qty());
			item.setUnitPrice(line.price());
			item.setDiscount(0L);
			item.setStatus("PENDING_PAYMENT");
			item.setFulfillLocationId(locationId);
			orders.insertOrderItem(item);
		}

		return new CheckoutInfo(orderNo, orderName(lines.getFirst().productName(), lines.size()), summary.payAmount(),
				cipher.opaqueId("toss-customer", user.id()), user.name(), user.email(), toss.clientKey());
	}

	public record PreviewLine(long skuId, String productName, String optionText, String conditionGrade,
			String thumbnailUrl, long price, int qty, int available, boolean purchasable, long lineTotal) {
	}

	public record Preview(List<PreviewLine> lines, long itemsTotal, long shippingFee, long payAmount,
			boolean allPurchasable) {
	}

	/** 주문서 미리보기: 서버 기준 금액과 구매 가능 여부 (재고는 예약하지 않음) */
	@Transactional(readOnly = true)
	public Preview preview(List<OrderLineRequest> requested) {
		Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
		requested.forEach(line -> qtyBySku.merge(line.skuId(), line.qty(), Integer::sum));
		Map<Long, PurchaseLine> found = purchaseLines.findBySkuIds(qtyBySku.keySet());

		List<PreviewLine> lines = new ArrayList<>();
		List<PurchaseLine> buyable = new ArrayList<>();
		for (Map.Entry<Long, Integer> entry : qtyBySku.entrySet()) {
			PurchaseLine line = found.get(entry.getKey());
			if (line == null) {
				continue;
			}
			PurchaseLine withQty = line.withQty(entry.getValue());
			boolean ok = line.purchasable() && line.available() >= entry.getValue();
			if (ok) {
				buyable.add(withQty);
			}
			lines.add(new PreviewLine(line.skuId(), line.productName(), line.optionText(), line.grade().label(),
					line.thumbnailUrl(), line.price(), entry.getValue(), line.available(), ok, withQty.lineTotal()));
		}
		OrderPricing.Summary summary = OrderPricing.calculate(buyable);
		return new Preview(lines, summary.itemsTotal(), summary.shippingFee(), summary.payAmount(),
				!lines.isEmpty() && lines.size() == qtyBySku.size() && buyable.size() == lines.size());
	}

	/** 판매자의 재고 위치 중 남은 수량이 있는 곳에 예약한다 */
	private long reserve(PurchaseLine line) {
		for (long locationId : orders.findLocationIds(line.sellerId())) {
			if (inventory.reserve(line.skuId(), locationId, line.qty())) {
				return locationId;
			}
		}
		throw ApiException.conflict("OUT_OF_STOCK", "'" + line.productName() + "' 재고가 방금 소진되었습니다.");
	}

	private String newOrderNo() {
		String date = LocalDate.now(SEOUL).format(DateTimeFormatter.BASIC_ISO_DATE);
		for (int attempt = 0; attempt < 5; attempt++) {
			StringBuilder sb = new StringBuilder(date).append('-');
			for (int i = 0; i < 8; i++) {
				sb.append(ORDER_NO_CHARS.charAt(random.nextInt(ORDER_NO_CHARS.length())));
			}
			if (!orders.orderNoExists(sb.toString())) {
				return sb.toString();
			}
		}
		throw new IllegalStateException("주문번호 생성 실패");
	}

	static String orderName(String firstProductName, int lineCount) {
		String name = firstProductName.length() > 40 ? firstProductName.substring(0, 40) + "…" : firstProductName;
		return lineCount > 1 ? name + " 외 " + (lineCount - 1) + "건" : name;
	}

	// ------------------------------------------------------------------
	// 고객 주문 조회
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<OrderSummary> myOrders(long userId, int page, int size) {
		List<OrderSummary> items = orders.findByUser(userId, size, page * size).stream().map(this::toSummary).toList();
		return new PageResponse<>(items, page, size, orders.countByUser(userId));
	}

	@Transactional(readOnly = true)
	public OrderDetail myOrder(long userId, String orderNo) {
		OrdersRecord order = orders.findByOrderNo(orderNo)
			.filter(o -> o.getUserId() != null && o.getUserId() == userId)
			.orElseThrow(() -> ApiException.notFound("주문"));
		return toDetail(order, null, CUSTOMER_CANCELLABLE);
	}

	// ------------------------------------------------------------------
	// 운영자 주문 처리
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<OrderSummary> sellerOrders(long sellerId, String itemStatus, String keyword, int page, int size) {
		List<OrderSummary> items = orders.findForSeller(sellerId, itemStatus, keyword, size, page * size)
			.stream()
			.map(this::toSummary)
			.toList();
		return new PageResponse<>(items, page, size, orders.countForSeller(sellerId, itemStatus, keyword));
	}

	/** 운영자 주문 상세. 수령인 개인정보를 보여주므로 열람 기록을 남긴다 */
	@Transactional
	public OrderDetail sellerOrder(AuthUser admin, long sellerId, String orderNo, String ip) {
		OrdersRecord order = requireSellerOrder(sellerId, orderNo);
		orders.auditPersonalInfoView(admin.id(), order.getId(), ip);
		return toDetail(order, sellerId, ADMIN_CANCELLABLE);
	}

	/** 발주확인: 결제완료 → 상품준비중 */
	@Transactional
	public OrderDetail acceptOrder(long sellerId, String orderNo) {
		OrdersRecord order = lockSellerOrder(sellerId, orderNo);
		if (orders.updateItemStatus(order.getId(), sellerId, Set.of("PAID"), "PREPARING") == 0) {
			throw ApiException.conflict("INVALID_STATUS", "결제완료 상태인 상품이 없습니다.");
		}
		return toDetail(order, sellerId, ADMIN_CANCELLABLE);
	}

	/** 출고: 송장번호를 등록하고 재고를 실제로 뺀다 */
	@Transactional
	public OrderDetail ship(long actorId, long sellerId, String orderNo, long carrierId, String trackingNo) {
		OrdersRecord order = lockSellerOrder(sellerId, orderNo);
		String tracking = trackingNo.replace("-", "");
		if (!orders.carrierExists(carrierId)) {
			throw ApiException.badRequest("INVALID_CARRIER", "택배사를 다시 선택해 주세요.");
		}
		if (orders.trackingNoInUse(carrierId, tracking)) {
			throw ApiException.conflict("DUPLICATE_TRACKING", "이미 등록된 송장번호입니다.");
		}
		List<OrderItemsRecord> shippable = orders.findItems(order.getId()).stream()
			.filter(i -> i.getSellerId() == sellerId && ADMIN_CANCELLABLE.contains(i.getStatus()))
			.toList();
		if (shippable.isEmpty()) {
			throw ApiException.conflict("INVALID_STATUS", "출고할 상품이 없습니다.");
		}

		long shipmentId = orders.insertShipment(sellerId, order.getId(), carrierId, tracking,
				shippable.getFirst().getFulfillLocationId());
		for (OrderItemsRecord item : shippable) {
			if (item.getSkuId() != null && item.getFulfillLocationId() != null) {
				if (!orders.shipOut(item.getSkuId(), item.getFulfillLocationId(), item.getQty())) {
					throw ApiException.conflict("STOCK_MISMATCH",
							"'" + item.getProductName() + "' 재고 기록이 맞지 않습니다. 재고를 확인해 주세요.");
				}
				orders.insertMovement(item.getSkuId(), item.getFulfillLocationId(), -item.getQty(), "SALE_OUT",
						"ORDER_ITEM", item.getId(), actorId);
			}
			orders.insertShipmentItem(shipmentId, item.getId(), item.getQty());
		}
		orders.updateItemStatus(order.getId(), sellerId, ADMIN_CANCELLABLE, "SHIPPED");
		return toDetail(order, sellerId, ADMIN_CANCELLABLE);
	}

	public List<CarrierOption> carriers() {
		return orders.findCarriers();
	}

	OrdersRecord requireSellerOrder(long sellerId, String orderNo) {
		OrdersRecord order = orders.findByOrderNo(orderNo).orElseThrow(() -> ApiException.notFound("주문"));
		boolean mine = orders.findItems(order.getId()).stream().anyMatch(i -> i.getSellerId() == sellerId);
		if (!mine || "PENDING_PAYMENT".equals(order.getStatus())) {
			throw ApiException.notFound("주문");
		}
		return order;
	}

	private OrdersRecord lockSellerOrder(long sellerId, String orderNo) {
		requireSellerOrder(sellerId, orderNo);
		return orders.lockByOrderNo(orderNo).orElseThrow();
	}

	// ------------------------------------------------------------------
	// 변환
	// ------------------------------------------------------------------

	private OrderSummary toSummary(OrdersRecord order) {
		List<OrderItemsRecord> items = orders.findItems(order.getId());
		OrderItemsRecord first = items.getFirst();
		return new OrderSummary(order.getOrderNo(), order.getOrderedAt(), order.getStatus(),
				orderName(first.getProductName(), items.size()), orders.findThumbnail(first.getSkuId()).orElse(null),
				items.size(), order.getPayAmount(), items.stream().map(OrderItemsRecord::getStatus).distinct().toList());
	}

	OrderDetail toDetail(OrdersRecord order, Long sellerId, Set<String> cancellableStatuses) {
		List<OrderItemsRecord> items = orders.findItems(order.getId()).stream()
			.filter(i -> sellerId == null || i.getSellerId() == sellerId)
			.toList();
		List<OrderLineView> lines = items.stream()
			.map(i -> new OrderLineView(i.getId(), i.getSkuId(), i.getProductName(), i.getOptionText(),
					i.getConditionGrade(), orders.findThumbnail(i.getSkuId()).orElse(null), i.getQty(), i.getUnitPrice(),
					i.getUnitPrice() * i.getQty() - i.getDiscount(), i.getStatus()))
			.toList();
		PaymentView payment = orders.findDonePayment(order.getId())
			.map(p -> new PaymentView(p.getMethod(), p.getEasyPayProvider(), p.getAmount(), p.getBalanceAmount(),
					p.getStatus(), p.getApprovedAt()))
			.orElse(null);
		List<ShipmentView> shipments = orders.findShipments(order.getId()).stream()
			.filter(s -> sellerId == null || s.getSellerId() == sellerId)
			.map(s -> new ShipmentView(s.getId(), orders.findCarrierName(s.getCarrierId()).orElse("-"),
					s.getTrackingNo(), s.getStatus(), s.getShippedAt(), s.getDeliveredAt()))
			.toList();
		Receiver receiver = new Receiver(order.getReceiverName(), cipher.decrypt(order.getReceiverPhoneEnc()),
				order.getReceiverZipcode(), cipher.decrypt(order.getReceiverAddress1Enc()),
				cipher.decrypt(order.getReceiverAddress2Enc()), order.getDeliveryMemo());
		boolean cancellable = !items.isEmpty() && items.stream().allMatch(i -> cancellableStatuses.contains(i.getStatus()));
		return new OrderDetail(order.getOrderNo(), order.getChannel(), order.getOrderedAt(), order.getPaidAt(),
				order.getStatus(), order.getOrdererName(), order.getOrdererEmail(), receiver, lines,
				order.getItemsTotal(), order.getShippingFee(), order.getDiscountTotal(), order.getPayAmount(), payment,
				shipments, cancellable, claimsOf(order, items));
	}

	/** 화면에 보이는 주문상품(판매자 화면이면 그 판매자 상품)의 취소·반품 내역 */
	private List<ClaimView> claimsOf(OrdersRecord order, List<OrderItemsRecord> visibleItems) {
		Set<Long> itemIds = new java.util.HashSet<>(visibleItems.stream().map(OrderItemsRecord::getId).toList());
		return claimService.orderClaims(order.getId()).stream().filter(c -> itemIds.contains(c.orderItemId())).toList();
	}

	private static String normalizePhone(String phone) {
		String digits = phone.replaceAll("\\D", "");
		if (digits.length() == 11) {
			return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
		}
		if (digits.length() == 10) {
			int head = digits.startsWith("02") ? 2 : 3;
			return digits.substring(0, head) + "-" + digits.substring(head, digits.length() - 4) + "-"
					+ digits.substring(digits.length() - 4);
		}
		return phone.strip();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
