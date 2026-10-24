package com.aishop.api.shipping;

import static com.aishop.api.jooq.Tables.AUDIT_LOGS;
import static com.aishop.api.jooq.Tables.CARRIERS;
import static com.aishop.api.jooq.Tables.ORDERS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;
import static com.aishop.api.jooq.Tables.SELLERS;
import static com.aishop.api.jooq.Tables.SHIPMENTS;
import static com.aishop.api.jooq.Tables.SHIPMENT_ITEMS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import com.aishop.api.auth.UserRepository;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.jooq.tables.records.OrdersRecord;
import com.aishop.api.jooq.tables.records.SellersRecord;
import com.aishop.api.order.TrackingLinks;
import com.aishop.api.shipping.ShippingViews.ReadyOrder;
import com.aishop.api.shipping.ShippingViews.SenderSettings;
import com.aishop.api.shipping.ShippingViews.ShipmentRow;

@Repository
public class ShippingRepository {

	private final DSLContext dsl;

	public ShippingRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	/** 이 판매자 상품이 해당 상태로 남아 있는 주문 (결제완료·상품준비중) */
	public List<ReadyOrder> findReadyOrders(long sellerId, Set<String> itemStatuses) {
		var items = dsl.select(ORDERS.ID, ORDERS.ORDER_NO, ORDERS.PAID_AT, ORDERS.RECEIVER_NAME, ORDERS.PAY_AMOUNT,
				ORDER_ITEMS.STATUS, ORDER_ITEMS.PRODUCT_NAME, ORDER_ITEMS.OPTION_TEXT, ORDER_ITEMS.QTY)
			.from(ORDER_ITEMS)
			.join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDER_ID))
			.where(ORDER_ITEMS.SELLER_ID.eq(sellerId), ORDER_ITEMS.STATUS.in(itemStatuses))
			.orderBy(ORDERS.PAID_AT, ORDERS.ID, ORDER_ITEMS.ID)
			.fetch();

		Map<Long, List<Record>> byOrder = new LinkedHashMap<>();
		items.forEach(r -> byOrder.computeIfAbsent(r.get(ORDERS.ID), k -> new ArrayList<>()).add(r));
		List<ReadyOrder> result = new ArrayList<>();
		byOrder.values().forEach(rows -> {
			Record first = rows.getFirst();
			result.add(new ReadyOrder(first.get(ORDERS.ORDER_NO), first.get(ORDERS.PAID_AT),
					first.get(ORDER_ITEMS.STATUS), first.get(ORDERS.RECEIVER_NAME),
					itemsText(rows.stream().map(r -> new Line(r.get(ORDER_ITEMS.PRODUCT_NAME),
							r.get(ORDER_ITEMS.OPTION_TEXT), r.get(ORDER_ITEMS.QTY))).toList()),
					rows.stream().mapToInt(r -> r.get(ORDER_ITEMS.QTY)).sum(), first.get(ORDERS.PAY_AMOUNT)));
		});
		return result;
	}

	/** 송장 엑셀용: 주문 원본(암호화된 수령인 정보 포함)과 이 판매자의 출고 대기 상품 */
	public List<OrderWithItems> findOrdersForExport(long sellerId, Collection<String> orderNos, Set<String> itemStatuses) {
		List<OrdersRecord> orders = dsl.selectFrom(ORDERS)
			.where(ORDERS.ORDER_NO.in(orderNos))
			.orderBy(ORDERS.PAID_AT, ORDERS.ID)
			.fetch();
		List<OrderWithItems> result = new ArrayList<>();
		for (OrdersRecord order : orders) {
			List<Line> lines = dsl.select(ORDER_ITEMS.PRODUCT_NAME, ORDER_ITEMS.OPTION_TEXT, ORDER_ITEMS.QTY)
				.from(ORDER_ITEMS)
				.where(ORDER_ITEMS.ORDER_ID.eq(order.getId()), ORDER_ITEMS.SELLER_ID.eq(sellerId),
						ORDER_ITEMS.STATUS.in(itemStatuses))
				.orderBy(ORDER_ITEMS.ID)
				.fetch(r -> new Line(r.value1(), r.value2(), r.value3()));
			if (!lines.isEmpty()) {
				result.add(new OrderWithItems(order, lines));
			}
		}
		return result;
	}

	public record Line(String productName, String optionText, int qty) {
	}

	public record OrderWithItems(OrdersRecord order, List<Line> lines) {
	}

	public static String itemsText(List<Line> lines) {
		return String.join(", ", lines.stream()
			.map(l -> l.productName() + (l.optionText() == null || l.optionText().isBlank() ? "" : " (" + l.optionText() + ")")
					+ " x" + l.qty())
			.toList());
	}

	public PageResponse<ShipmentRow> findShipments(long sellerId, Set<String> statuses, String keyword, int page,
			int size) {
		Condition where = SHIPMENTS.SELLER_ID.eq(sellerId)
			.and(SHIPMENTS.TYPE.eq("OUTBOUND"))
			.and(statuses == null ? noCondition() : SHIPMENTS.STATUS.in(statuses))
			.and(keyword == null || keyword.isBlank() ? noCondition()
					: ORDERS.ORDER_NO.containsIgnoreCase(keyword.strip())
						.or(SHIPMENTS.TRACKING_NO.eq(keyword.strip().replace("-", "")))
						.or(ORDERS.RECEIVER_NAME.eq(keyword.strip())));

		long total = dsl.fetchCount(dsl.select(SHIPMENTS.ID)
			.from(SHIPMENTS)
			.join(ORDERS).on(ORDERS.ID.eq(SHIPMENTS.ORDER_ID))
			.where(where));
		var rows = dsl.select(SHIPMENTS.ID, ORDERS.ORDER_NO, ORDERS.RECEIVER_NAME, CARRIERS.CODE, CARRIERS.NAME,
				SHIPMENTS.TRACKING_NO, SHIPMENTS.STATUS, SHIPMENTS.SHIPPED_AT, SHIPMENTS.DELIVERED_AT)
			.from(SHIPMENTS)
			.join(ORDERS).on(ORDERS.ID.eq(SHIPMENTS.ORDER_ID))
			.leftJoin(CARRIERS).on(CARRIERS.ID.eq(SHIPMENTS.CARRIER_ID))
			.where(where)
			.orderBy(SHIPMENTS.SHIPPED_AT.desc().nullsLast(), SHIPMENTS.ID.desc())
			.limit(size)
			.offset(page * size)
			.fetch();

		Map<Long, List<Line>> lines = new LinkedHashMap<>();
		if (!rows.isEmpty()) {
			dsl.select(SHIPMENT_ITEMS.SHIPMENT_ID, ORDER_ITEMS.PRODUCT_NAME, ORDER_ITEMS.OPTION_TEXT, SHIPMENT_ITEMS.QTY)
				.from(SHIPMENT_ITEMS)
				.join(ORDER_ITEMS).on(ORDER_ITEMS.ID.eq(SHIPMENT_ITEMS.ORDER_ITEM_ID))
				.where(SHIPMENT_ITEMS.SHIPMENT_ID.in(rows.map(r -> r.get(SHIPMENTS.ID))))
				.orderBy(ORDER_ITEMS.ID)
				.forEach(r -> lines.computeIfAbsent(r.value1(), k -> new ArrayList<>())
					.add(new Line(r.value2(), r.value3(), r.value4())));
		}

		List<ShipmentRow> items = rows.map(r -> new ShipmentRow(r.get(SHIPMENTS.ID), r.get(ORDERS.ORDER_NO),
				r.get(ORDERS.RECEIVER_NAME), r.get(CARRIERS.NAME), r.get(SHIPMENTS.TRACKING_NO),
				TrackingLinks.url(r.get(CARRIERS.CODE), r.get(SHIPMENTS.TRACKING_NO)), r.get(SHIPMENTS.STATUS),
				r.get(SHIPMENTS.SHIPPED_AT), r.get(SHIPMENTS.DELIVERED_AT),
				itemsText(lines.getOrDefault(r.get(SHIPMENTS.ID), List.of()))));
		return new PageResponse<>(items, page, size, total);
	}

	/** 배송완료: 송장과 그 송장에 담긴 상품을 함께 바꾼다 */
	public boolean markDelivered(long sellerId, long shipmentId) {
		int updated = dsl.update(SHIPMENTS)
			.set(SHIPMENTS.STATUS, "DELIVERED")
			.set(SHIPMENTS.DELIVERED_AT, currentOffsetDateTime())
			.where(SHIPMENTS.ID.eq(shipmentId), SHIPMENTS.SELLER_ID.eq(sellerId))
			.and(SHIPMENTS.STATUS.in("SHIPPED", "IN_TRANSIT", "OUT_FOR_DELIVERY"))
			.execute();
		if (updated == 0) {
			return false;
		}
		dsl.update(ORDER_ITEMS)
			.set(ORDER_ITEMS.STATUS, "DELIVERED")
			.set(ORDER_ITEMS.DELIVERED_AT, currentOffsetDateTime())
			.where(ORDER_ITEMS.ID.in(select(SHIPMENT_ITEMS.ORDER_ITEM_ID).from(SHIPMENT_ITEMS)
				.where(SHIPMENT_ITEMS.SHIPMENT_ID.eq(shipmentId))))
			.and(ORDER_ITEMS.STATUS.eq("SHIPPED"))
			.execute();
		return true;
	}

	public void auditExport(long actorId, int orderCount, String ip) {
		dsl.insertInto(AUDIT_LOGS)
			.set(AUDIT_LOGS.ACTOR_USER_ID, actorId)
			.set(AUDIT_LOGS.ACTION, "EXPORT")
			.set(AUDIT_LOGS.TARGET_TYPE, "ORDER_SHIPPING_SHEET")
			.set(AUDIT_LOGS.DETAIL, JSONB.valueOf("{\"orders\":" + orderCount + "}"))
			.set(AUDIT_LOGS.IP, UserRepository.toInet(ip))
			.execute();
	}

	// ------------------------------------------------------------------
	// 보내는 사람·출고지·반품지
	// ------------------------------------------------------------------

	public Optional<SellersRecord> findSeller(long sellerId) {
		return dsl.selectFrom(SELLERS).where(SELLERS.ID.eq(sellerId)).fetchOptional();
	}

	public void updateSender(long sellerId, SenderSettings s) {
		dsl.update(SELLERS)
			.set(SELLERS.SENDER_NAME, blankToNull(s.senderName()))
			.set(SELLERS.SENDER_PHONE, blankToNull(s.senderPhone()))
			.set(SELLERS.SHIP_FROM_ZIPCODE, blankToNull(s.shipFromZipcode()))
			.set(SELLERS.SHIP_FROM_ADDRESS, blankToNull(s.shipFromAddress()))
			.set(SELLERS.RETURN_ZIPCODE, blankToNull(s.returnZipcode()))
			.set(SELLERS.RETURN_ADDRESS, blankToNull(s.returnAddress()))
			.where(SELLERS.ID.eq(sellerId))
			.execute();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
