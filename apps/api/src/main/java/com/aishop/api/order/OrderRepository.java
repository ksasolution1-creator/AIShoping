package com.aishop.api.order;

import static com.aishop.api.jooq.Tables.AUDIT_LOGS;
import static com.aishop.api.jooq.Tables.CARRIERS;
import static com.aishop.api.jooq.Tables.CART_ITEMS;
import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.INVENTORY_MOVEMENTS;
import static com.aishop.api.jooq.Tables.LOCATIONS;
import static com.aishop.api.jooq.Tables.ORDERS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;
import static com.aishop.api.jooq.Tables.PAYMENTS;
import static com.aishop.api.jooq.Tables.PRODUCT_IMAGES;
import static com.aishop.api.jooq.Tables.REFUNDS;
import static com.aishop.api.jooq.Tables.SHIPMENTS;
import static com.aishop.api.jooq.Tables.SHIPMENT_ITEMS;
import static com.aishop.api.jooq.Tables.SKUS;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import com.aishop.api.jooq.tables.records.OrderItemsRecord;
import com.aishop.api.jooq.tables.records.OrdersRecord;
import com.aishop.api.jooq.tables.records.PaymentsRecord;
import com.aishop.api.jooq.tables.records.ShipmentsRecord;

@Repository
public class OrderRepository {

	private final DSLContext dsl;

	public OrderRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	// ------------------------------------------------------------------
	// 주문 생성
	// ------------------------------------------------------------------

	public boolean orderNoExists(String orderNo) {
		return dsl.fetchExists(ORDERS, ORDERS.ORDER_NO.eq(orderNo));
	}

	public long insertOrder(OrdersRecord order) {
		return dsl.insertInto(ORDERS).set(order).returning(ORDERS.ID).fetchOne(ORDERS.ID);
	}

	public void insertOrderItem(OrderItemsRecord item) {
		dsl.insertInto(ORDER_ITEMS).set(item).execute();
	}

	/** 판매자의 재고 위치 (창고 먼저, 그다음 매장) */
	public List<Long> findLocationIds(long sellerId) {
		return dsl.select(LOCATIONS.ID)
			.from(LOCATIONS)
			.where(LOCATIONS.SELLER_ID.eq(sellerId), LOCATIONS.IS_ACTIVE.isTrue())
			.orderBy(LOCATIONS.TYPE.eq("WAREHOUSE").desc(), LOCATIONS.ID)
			.fetch(LOCATIONS.ID);
	}

	// ------------------------------------------------------------------
	// 조회
	// ------------------------------------------------------------------

	public Optional<OrdersRecord> findByOrderNo(String orderNo) {
		return dsl.selectFrom(ORDERS).where(ORDERS.ORDER_NO.eq(orderNo)).fetchOptional();
	}

	/** 같은 주문을 동시에 처리하지 않도록 행을 잠근다 */
	public Optional<OrdersRecord> lockByOrderNo(String orderNo) {
		return dsl.selectFrom(ORDERS).where(ORDERS.ORDER_NO.eq(orderNo)).forUpdate().fetchOptional();
	}

	public List<OrderItemsRecord> findItems(long orderId) {
		return dsl.selectFrom(ORDER_ITEMS).where(ORDER_ITEMS.ORDER_ID.eq(orderId)).orderBy(ORDER_ITEMS.ID).fetch();
	}

	public Optional<PaymentsRecord> findDonePayment(long orderId) {
		return dsl.selectFrom(PAYMENTS)
			.where(PAYMENTS.ORDER_ID.eq(orderId), PAYMENTS.STATUS.in("DONE", "PARTIAL_CANCELED", "CANCELED"))
			.orderBy(PAYMENTS.ID.desc())
			.limit(1)
			.fetchOptional();
	}

	public Optional<PaymentsRecord> findPaymentByKey(String paymentKey) {
		return dsl.selectFrom(PAYMENTS).where(PAYMENTS.PAYMENT_KEY.eq(paymentKey)).fetchOptional();
	}

	public List<ShipmentsRecord> findShipments(long orderId) {
		return dsl.selectFrom(SHIPMENTS).where(SHIPMENTS.ORDER_ID.eq(orderId)).orderBy(SHIPMENTS.ID).fetch();
	}

	public Optional<String> findCarrierName(Long carrierId) {
		if (carrierId == null) {
			return Optional.empty();
		}
		return dsl.select(CARRIERS.NAME).from(CARRIERS).where(CARRIERS.ID.eq(carrierId)).fetchOptional(CARRIERS.NAME);
	}

	public List<OrdersRecord> findByUser(long userId, int limit, int offset) {
		return dsl.selectFrom(ORDERS)
			.where(ORDERS.USER_ID.eq(userId), ORDERS.STATUS.ne("PENDING_PAYMENT"))
			.orderBy(ORDERS.ORDERED_AT.desc())
			.limit(limit)
			.offset(offset)
			.fetch();
	}

	public int countByUser(long userId) {
		return dsl.fetchCount(ORDERS, ORDERS.USER_ID.eq(userId).and(ORDERS.STATUS.ne("PENDING_PAYMENT")));
	}

	/** 판매자 상품이 들어 있는 주문 (결제 대기는 제외) */
	public List<OrdersRecord> findForSeller(long sellerId, String itemStatus, String keyword, int limit, int offset) {
		return dsl.selectFrom(ORDERS)
			.where(sellerCondition(sellerId, itemStatus, keyword))
			.orderBy(ORDERS.ORDERED_AT.desc())
			.limit(limit)
			.offset(offset)
			.fetch();
	}

	public int countForSeller(long sellerId, String itemStatus, String keyword) {
		return dsl.fetchCount(ORDERS, sellerCondition(sellerId, itemStatus, keyword));
	}

	private static Condition sellerCondition(long sellerId, String itemStatus, String keyword) {
		Condition items = ORDER_ITEMS.ORDER_ID.eq(ORDERS.ID).and(ORDER_ITEMS.SELLER_ID.eq(sellerId))
			.and(itemStatus == null ? noCondition() : ORDER_ITEMS.STATUS.eq(itemStatus));
		Condition search = keyword == null || keyword.isBlank() ? noCondition()
				: ORDERS.ORDER_NO.containsIgnoreCase(keyword.strip()).or(ORDERS.ORDERER_NAME.eq(keyword.strip()));
		return ORDERS.STATUS.ne("PENDING_PAYMENT")
			.andExists(selectOne().from(ORDER_ITEMS).where(items))
			.and(search);
	}

	/** 주문 목록용 대표 사진 */
	public Optional<String> findThumbnail(Long skuId) {
		if (skuId == null) {
			return Optional.empty();
		}
		Field<Long> productId = field(select(SKUS.PRODUCT_ID).from(SKUS).where(SKUS.ID.eq(skuId)));
		return dsl.select(PRODUCT_IMAGES.URL)
			.from(PRODUCT_IMAGES)
			.where(PRODUCT_IMAGES.PRODUCT_ID.eq(productId))
			.and(PRODUCT_IMAGES.SKU_ID.eq(skuId).or(PRODUCT_IMAGES.SKU_ID.isNull()))
			.orderBy(PRODUCT_IMAGES.SKU_ID.nullsLast(), PRODUCT_IMAGES.SORT_ORDER, PRODUCT_IMAGES.ID)
			.limit(1)
			.fetchOptional(PRODUCT_IMAGES.URL);
	}

	/** 결제 시간이 지난 결제 대기 주문 (다른 서버가 처리 중인 행은 건너뜀) */
	public List<String> lockExpiredPending(Duration ttl, int limit) {
		return dsl.select(ORDERS.ORDER_NO)
			.from(ORDERS)
			.where(ORDERS.STATUS.eq("PENDING_PAYMENT"), ORDERS.CREATED_AT.lt(OffsetDateTime.now().minus(ttl)))
			.orderBy(ORDERS.CREATED_AT)
			.limit(limit)
			.forUpdate()
			.skipLocked()
			.fetch(ORDERS.ORDER_NO);
	}

	// ------------------------------------------------------------------
	// 상태 변경
	// ------------------------------------------------------------------

	public void updateOrderStatus(long orderId, String status, boolean paidNow) {
		var update = dsl.update(ORDERS).set(ORDERS.STATUS, status);
		if (paidNow) {
			update = update.set(ORDERS.PAID_AT, currentOffsetDateTime());
		}
		update.where(ORDERS.ID.eq(orderId)).execute();
	}

	public int updateItemStatus(long orderId, Long sellerId, Collection<String> fromStatuses, String toStatus) {
		return dsl.update(ORDER_ITEMS)
			.set(ORDER_ITEMS.STATUS, toStatus)
			.where(ORDER_ITEMS.ORDER_ID.eq(orderId), ORDER_ITEMS.STATUS.in(fromStatuses))
			.and(sellerId == null ? noCondition() : ORDER_ITEMS.SELLER_ID.eq(sellerId))
			.execute();
	}

	public void insertPayment(long orderId, String paymentKey, String method, String easyPay, long amount,
			long balance, String status, OffsetDateTime approvedAt, String raw) {
		dsl.insertInto(PAYMENTS)
			.set(PAYMENTS.ORDER_ID, orderId)
			.set(PAYMENTS.PAYMENT_KEY, paymentKey)
			.set(PAYMENTS.METHOD, method)
			.set(PAYMENTS.EASY_PAY_PROVIDER, easyPay)
			.set(PAYMENTS.AMOUNT, amount)
			.set(PAYMENTS.BALANCE_AMOUNT, balance)
			.set(PAYMENTS.STATUS, status)
			.set(PAYMENTS.APPROVED_AT, approvedAt)
			.set(PAYMENTS.RAW_RESPONSE, raw == null ? null : JSONB.valueOf(raw))
			.onConflict(PAYMENTS.PAYMENT_KEY)
			.doNothing()
			.execute();
	}

	public void updatePayment(long paymentId, String status, long balance, String raw) {
		dsl.update(PAYMENTS)
			.set(PAYMENTS.STATUS, status)
			.set(PAYMENTS.BALANCE_AMOUNT, balance)
			.set(PAYMENTS.RAW_RESPONSE, raw == null ? null : JSONB.valueOf(raw))
			.where(PAYMENTS.ID.eq(paymentId))
			.execute();
	}

	public void insertRefund(long paymentId, long amount, String reason, String idempotencyKey, String transactionKey,
			String raw) {
		dsl.insertInto(REFUNDS)
			.set(REFUNDS.PAYMENT_ID, paymentId)
			.set(REFUNDS.AMOUNT, amount)
			.set(REFUNDS.REASON, reason)
			.set(REFUNDS.IDEMPOTENCY_KEY, idempotencyKey)
			.set(REFUNDS.STATUS, "DONE")
			.set(REFUNDS.PG_TRANSACTION_KEY, transactionKey)
			.set(REFUNDS.RAW_RESPONSE, raw == null ? null : JSONB.valueOf(raw))
			.onConflict(REFUNDS.IDEMPOTENCY_KEY)
			.doNothing()
			.execute();
	}

	public void deleteCartItems(long userId, Collection<Long> skuIds) {
		dsl.deleteFrom(CART_ITEMS).where(CART_ITEMS.USER_ID.eq(userId), CART_ITEMS.SKU_ID.in(skuIds)).execute();
	}

	// ------------------------------------------------------------------
	// 출고
	// ------------------------------------------------------------------

	/** 출고: 예약을 풀면서 보유 재고에서 뺀다 */
	public boolean shipOut(long skuId, long locationId, int qty) {
		return dsl.update(INVENTORIES)
			.set(INVENTORIES.ON_HAND, INVENTORIES.ON_HAND.minus(qty))
			.set(INVENTORIES.RESERVED, INVENTORIES.RESERVED.minus(qty))
			.set(INVENTORIES.UPDATED_AT, currentOffsetDateTime())
			.where(INVENTORIES.SKU_ID.eq(skuId), INVENTORIES.LOCATION_ID.eq(locationId))
			.and(INVENTORIES.RESERVED.ge(qty))
			.and(INVENTORIES.ON_HAND.ge(qty))
			.execute() == 1;
	}

	public void insertMovement(long skuId, long locationId, int qty, String type, String refType, long refId,
			Long actorId) {
		dsl.insertInto(INVENTORY_MOVEMENTS)
			.set(INVENTORY_MOVEMENTS.SKU_ID, skuId)
			.set(INVENTORY_MOVEMENTS.LOCATION_ID, locationId)
			.set(INVENTORY_MOVEMENTS.QTY, qty)
			.set(INVENTORY_MOVEMENTS.TYPE, type)
			.set(INVENTORY_MOVEMENTS.REF_TYPE, refType)
			.set(INVENTORY_MOVEMENTS.REF_ID, refId)
			.set(INVENTORY_MOVEMENTS.CREATED_BY, actorId)
			.execute();
	}

	public boolean carrierExists(long carrierId) {
		return dsl.fetchExists(CARRIERS, CARRIERS.ID.eq(carrierId).and(CARRIERS.IS_ACTIVE.isTrue()));
	}

	public long insertShipment(long sellerId, long orderId, long carrierId, String trackingNo, Long fromLocationId) {
		return dsl.insertInto(SHIPMENTS)
			.set(SHIPMENTS.SELLER_ID, sellerId)
			.set(SHIPMENTS.ORDER_ID, orderId)
			.set(SHIPMENTS.TYPE, "OUTBOUND")
			.set(SHIPMENTS.CARRIER_ID, carrierId)
			.set(SHIPMENTS.TRACKING_NO, trackingNo)
			.set(SHIPMENTS.FROM_LOCATION_ID, fromLocationId)
			.set(SHIPMENTS.STATUS, "SHIPPED")
			.set(SHIPMENTS.SHIPPED_AT, currentOffsetDateTime())
			.returning(SHIPMENTS.ID)
			.fetchOne(SHIPMENTS.ID);
	}

	public void insertShipmentItem(long shipmentId, long orderItemId, int qty) {
		dsl.insertInto(SHIPMENT_ITEMS)
			.set(SHIPMENT_ITEMS.SHIPMENT_ID, shipmentId)
			.set(SHIPMENT_ITEMS.ORDER_ITEM_ID, orderItemId)
			.set(SHIPMENT_ITEMS.QTY, qty)
			.execute();
	}

	public boolean trackingNoInUse(long carrierId, String trackingNo) {
		return dsl.fetchExists(SHIPMENTS, SHIPMENTS.CARRIER_ID.eq(carrierId).and(SHIPMENTS.TRACKING_NO.eq(trackingNo)));
	}

	public List<CarrierOption> findCarriers() {
		return dsl.select(CARRIERS.ID, CARRIERS.CODE, CARRIERS.NAME)
			.from(CARRIERS)
			.where(CARRIERS.IS_ACTIVE.isTrue())
			.orderBy(CARRIERS.IS_CONTRACTED.desc(), CARRIERS.ID)
			.fetch(r -> new CarrierOption(r.value1(), r.value2(), r.value3()));
	}

	public record CarrierOption(long id, String code, String name) {
	}

	/** 운영자가 고객 개인정보(수령인 연락처·주소)를 열람한 기록 */
	public void auditPersonalInfoView(long actorId, long orderId, String ip) {
		dsl.insertInto(AUDIT_LOGS)
			.set(AUDIT_LOGS.ACTOR_USER_ID, actorId)
			.set(AUDIT_LOGS.ACTION, "VIEW_PERSONAL_INFO")
			.set(AUDIT_LOGS.TARGET_TYPE, "ORDER")
			.set(AUDIT_LOGS.TARGET_ID, orderId)
			.set(AUDIT_LOGS.IP, com.aishop.api.auth.UserRepository.toInet(ip))
			.execute();
	}

	public int countItems(long orderId) {
		return dsl.select(count()).from(ORDER_ITEMS).where(ORDER_ITEMS.ORDER_ID.eq(orderId)).fetchOne(0, int.class);
	}

}
