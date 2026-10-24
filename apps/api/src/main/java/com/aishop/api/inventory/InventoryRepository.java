package com.aishop.api.inventory;

import static com.aishop.api.jooq.Tables.INVENTORIES;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {

	private final DSLContext dsl;

	public InventoryRepository(DSLContext dsl) {
		this.dsl = dsl;
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

}
