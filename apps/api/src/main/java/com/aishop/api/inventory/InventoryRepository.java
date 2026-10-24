package com.aishop.api.inventory;

import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.INVENTORY_MOVEMENTS;
import static com.aishop.api.jooq.Tables.LOCATIONS;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.SKUS;
import static com.aishop.api.jooq.Tables.USERS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.sum;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import com.aishop.api.catalog.CatalogViews.Grade;
import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.common.db.JsonbMapper;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.inventory.InventoryViews.Location;
import com.aishop.api.inventory.InventoryViews.LocationStock;
import com.aishop.api.inventory.InventoryViews.Movement;
import com.aishop.api.inventory.InventoryViews.StockRow;

@Repository
public class InventoryRepository {

	private final DSLContext dsl;
	private final JsonbMapper jsonb;

	public InventoryRepository(DSLContext dsl, JsonbMapper jsonb) {
		this.dsl = dsl;
		this.jsonb = jsonb;
	}

	/**
	 * 판매가능 수량(on_hand - reserved)이 충분할 때만 예약한다.
	 * 조건부 UPDATE 한 번으로 처리하므로 동시 주문에도 초과 판매되지 않는다.
	 *
	 * @return 예약 성공 여부
	 */
	public boolean reserve(long skuId, long locationId, int qty) {
		return dsl.update(INVENTORIES)
			.set(INVENTORIES.RESERVED, INVENTORIES.RESERVED.plus(qty))
			.where(INVENTORIES.SKU_ID.eq(skuId))
			.and(INVENTORIES.LOCATION_ID.eq(locationId))
			.and(INVENTORIES.ON_HAND.minus(INVENTORIES.RESERVED).ge(qty))
			.execute() == 1;
	}

	/** 결제 실패·만료 시 예약을 푼다. */
	public void release(long skuId, long locationId, int qty) {
		int updated = dsl.update(INVENTORIES)
			.set(INVENTORIES.RESERVED, INVENTORIES.RESERVED.minus(qty))
			.where(INVENTORIES.SKU_ID.eq(skuId))
			.and(INVENTORIES.LOCATION_ID.eq(locationId))
			.and(INVENTORIES.RESERVED.ge(qty))
			.execute();
		if (updated != 1) {
			throw new IllegalStateException("예약 해제 실패: sku=" + skuId + ", location=" + locationId + ", qty=" + qty);
		}
	}

	public int availableQty(long skuId, long locationId) {
		Integer available = dsl.select(INVENTORIES.ON_HAND.minus(INVENTORIES.RESERVED))
			.from(INVENTORIES)
			.where(INVENTORIES.SKU_ID.eq(skuId))
			.and(INVENTORIES.LOCATION_ID.eq(locationId))
			.fetchOne(0, Integer.class);
		return available == null ? 0 : available;
	}

	/**
	 * 보유 재고(on_hand)를 qty만큼 늘리거나 줄인다.
	 * 줄일 때는 주문 예약 수량 아래로 내려가지 않는 경우에만 반영한다.
	 *
	 * @return 반영 여부
	 */
	public boolean changeOnHand(long skuId, long locationId, int qty) {
		if (qty >= 0) {
			dsl.insertInto(INVENTORIES)
				.set(INVENTORIES.SKU_ID, skuId)
				.set(INVENTORIES.LOCATION_ID, locationId)
				.set(INVENTORIES.ON_HAND, qty)
				.onConflict(INVENTORIES.SKU_ID, INVENTORIES.LOCATION_ID)
				.doUpdate()
				.set(INVENTORIES.ON_HAND, INVENTORIES.ON_HAND.plus(qty))
				.set(INVENTORIES.UPDATED_AT, currentOffsetDateTime())
				.execute();
			return true;
		}
		return dsl.update(INVENTORIES)
			.set(INVENTORIES.ON_HAND, INVENTORIES.ON_HAND.plus(qty))
			.set(INVENTORIES.UPDATED_AT, currentOffsetDateTime())
			.where(INVENTORIES.SKU_ID.eq(skuId), INVENTORIES.LOCATION_ID.eq(locationId))
			.and(INVENTORIES.ON_HAND.plus(qty).ge(INVENTORIES.RESERVED))
			.execute() == 1;
	}

	public void insertMovement(long skuId, long locationId, int qty, String type, String refType, Long refId,
			String memo, Long actorId) {
		dsl.insertInto(INVENTORY_MOVEMENTS)
			.set(INVENTORY_MOVEMENTS.SKU_ID, skuId)
			.set(INVENTORY_MOVEMENTS.LOCATION_ID, locationId)
			.set(INVENTORY_MOVEMENTS.QTY, qty)
			.set(INVENTORY_MOVEMENTS.TYPE, type)
			.set(INVENTORY_MOVEMENTS.REF_TYPE, refType)
			.set(INVENTORY_MOVEMENTS.REF_ID, refId)
			.set(INVENTORY_MOVEMENTS.MEMO, memo)
			.set(INVENTORY_MOVEMENTS.CREATED_BY, actorId)
			.execute();
	}

	// ------------------------------------------------------------------
	// 관리자 재고 현황
	// ------------------------------------------------------------------

	public List<Location> findLocations(long sellerId) {
		return dsl.select(LOCATIONS.ID, LOCATIONS.NAME, LOCATIONS.TYPE)
			.from(LOCATIONS)
			.where(LOCATIONS.SELLER_ID.eq(sellerId), LOCATIONS.IS_ACTIVE.isTrue())
			.orderBy(LOCATIONS.ID)
			.fetch(r -> new Location(r.value1(), r.value2(), r.value3()));
	}

	public boolean locationBelongsTo(long sellerId, long locationId) {
		return dsl.fetchExists(LOCATIONS, LOCATIONS.ID.eq(locationId), LOCATIONS.SELLER_ID.eq(sellerId),
				LOCATIONS.IS_ACTIVE.isTrue());
	}

	public boolean skuBelongsTo(long sellerId, long skuId) {
		return dsl.fetchExists(dsl.selectOne()
			.from(SKUS)
			.join(PRODUCTS).on(PRODUCTS.ID.eq(SKUS.PRODUCT_ID))
			.where(SKUS.ID.eq(skuId), PRODUCTS.SELLER_ID.eq(sellerId), SKUS.STATUS.ne("DELETED")));
	}

	/**
	 * 재고 현황. 검색어는 상품명(부분 일치) 또는 SKU코드·바코드(정확히 일치)로 찾는다.
	 *
	 * @param maxAvailable 값이 있으면 판매가능 수량이 이 값 이하인 SKU만 (품절 임박)
	 */
	public PageResponse<StockRow> findStock(long sellerId, String keyword, Long locationId, Integer maxAvailable,
			int page, int size) {
		return findStock(sellerId, keywordCondition(keyword), locationId, maxAvailable, page, size);
	}

	public Optional<StockRow> findStockRow(long sellerId, long skuId) {
		return findStock(sellerId, SKUS.ID.eq(skuId), null, null, 0, 1).items().stream().findFirst();
	}

	private PageResponse<StockRow> findStock(long sellerId, Condition filter, Long locationId, Integer maxAvailable,
			int page, int size) {
		Field<Integer> onHand = coalesce(sum(INVENTORIES.ON_HAND), inline(BigDecimal.ZERO))
			.cast(Integer.class).as("on_hand");
		Field<Integer> reserved = coalesce(sum(INVENTORIES.RESERVED), inline(BigDecimal.ZERO))
			.cast(Integer.class).as("reserved");

		Condition where = PRODUCTS.SELLER_ID.eq(sellerId)
			.and(PRODUCTS.STATUS.ne("DELETED"))
			.and(SKUS.STATUS.ne("DELETED"))
			.and(filter);
		Condition join = INVENTORIES.SKU_ID.eq(SKUS.ID)
			.and(locationId == null ? noCondition() : INVENTORIES.LOCATION_ID.eq(locationId));
		Condition having = maxAvailable == null ? noCondition()
				: coalesce(sum(INVENTORIES.ON_HAND.minus(INVENTORIES.RESERVED)), inline(BigDecimal.ZERO))
					.le(BigDecimal.valueOf(maxAvailable));

		var base = dsl.select(SKUS.ID, SKUS.SKU_CODE, SKUS.BARCODE, SKUS.PRODUCT_ID, PRODUCTS.NAME, SKUS.OPTION_VALUES,
				SKUS.CONDITION_GRADE, SKUS.PRICE, SKUS.COST, onHand, reserved)
			.from(SKUS)
			.join(PRODUCTS).on(PRODUCTS.ID.eq(SKUS.PRODUCT_ID))
			.leftJoin(INVENTORIES).on(join)
			.where(where)
			.groupBy(SKUS.ID, PRODUCTS.ID)
			.having(having);

		long total = dsl.fetchCount(base);
		var rows = base.orderBy(PRODUCTS.ID.desc(), SKUS.ID).limit(size).offset(page * size).fetch();
		Map<Long, List<LocationStock>> perLocation = findLocationStock(rows.stream().map(r -> r.get(SKUS.ID)).toList(),
				locationId);

		List<StockRow> items = rows.stream()
			.map(r -> new StockRow(r.get(SKUS.ID), r.get(SKUS.SKU_CODE), r.get(SKUS.BARCODE), r.get(SKUS.PRODUCT_ID),
					r.get(PRODUCTS.NAME), optionText(jsonb.toStringMap(r.get(SKUS.OPTION_VALUES))),
					Grade.of(ConditionGrade.valueOf(r.get(SKUS.CONDITION_GRADE))), r.get(SKUS.PRICE), r.get(SKUS.COST),
					r.get(onHand), r.get(reserved), perLocation.getOrDefault(r.get(SKUS.ID), List.of())))
			.toList();
		return new PageResponse<>(items, page, size, total);
	}

	/** SKU코드 또는 바코드로 한 개를 찾는다 (스캐너 입력) */
	public Optional<Long> findSkuIdByCode(long sellerId, String code) {
		return dsl.select(SKUS.ID)
			.from(SKUS)
			.join(PRODUCTS).on(PRODUCTS.ID.eq(SKUS.PRODUCT_ID))
			.where(PRODUCTS.SELLER_ID.eq(sellerId), SKUS.STATUS.ne("DELETED"))
			.and(SKUS.SKU_CODE.equalIgnoreCase(code).or(SKUS.BARCODE.eq(code)))
			.orderBy(SKUS.SKU_CODE.equalIgnoreCase(code).desc(), SKUS.ID)
			.limit(1)
			.fetchOptional(SKUS.ID);
	}

	private Map<Long, List<LocationStock>> findLocationStock(List<Long> skuIds, Long locationId) {
		Map<Long, List<LocationStock>> result = new HashMap<>();
		if (skuIds.isEmpty()) {
			return result;
		}
		dsl.select(INVENTORIES.SKU_ID, INVENTORIES.LOCATION_ID, LOCATIONS.NAME, INVENTORIES.ON_HAND,
				INVENTORIES.RESERVED)
			.from(INVENTORIES)
			.join(LOCATIONS).on(LOCATIONS.ID.eq(INVENTORIES.LOCATION_ID))
			.where(INVENTORIES.SKU_ID.in(skuIds))
			.and(locationId == null ? noCondition() : INVENTORIES.LOCATION_ID.eq(locationId))
			.orderBy(INVENTORIES.LOCATION_ID)
			.forEach(r -> result.computeIfAbsent(r.value1(), k -> new ArrayList<>())
				.add(new LocationStock(r.value2(), r.value3(), r.value4(), r.value5())));
		return result;
	}

	public List<Movement> findMovements(long skuId, int limit) {
		return dsl.select(INVENTORY_MOVEMENTS.ID, INVENTORY_MOVEMENTS.CREATED_AT, LOCATIONS.NAME,
				INVENTORY_MOVEMENTS.QTY, INVENTORY_MOVEMENTS.TYPE, INVENTORY_MOVEMENTS.REF_TYPE,
				INVENTORY_MOVEMENTS.REF_ID, INVENTORY_MOVEMENTS.MEMO, USERS.NAME)
			.from(INVENTORY_MOVEMENTS)
			.join(LOCATIONS).on(LOCATIONS.ID.eq(INVENTORY_MOVEMENTS.LOCATION_ID))
			.leftJoin(USERS).on(USERS.ID.eq(INVENTORY_MOVEMENTS.CREATED_BY))
			.where(INVENTORY_MOVEMENTS.SKU_ID.eq(skuId))
			.orderBy(INVENTORY_MOVEMENTS.ID.desc())
			.limit(limit)
			.fetch(r -> new Movement(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(),
					r.value7(), r.value8(), r.value9()));
	}

	private static Condition keywordCondition(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return noCondition();
		}
		String k = keyword.strip();
		return PRODUCTS.NAME.containsIgnoreCase(k)
			.or(SKUS.SKU_CODE.equalIgnoreCase(k))
			.or(SKUS.SKU_CODE.startsWithIgnoreCase(k))
			.or(SKUS.BARCODE.eq(k));
	}

	static String optionText(Map<String, String> optionValues) {
		return String.join(", ", optionValues.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).toList());
	}

}
