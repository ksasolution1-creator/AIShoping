package com.aishop.api.inventory;

import static com.aishop.api.jooq.Tables.LOCATIONS;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.PURCHASES;
import static com.aishop.api.jooq.Tables.PURCHASE_ITEMS;
import static com.aishop.api.jooq.Tables.SKUS;
import static com.aishop.api.jooq.Tables.SUPPLIERS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import com.aishop.api.catalog.CatalogViews.Grade;
import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.common.db.JsonbMapper;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.inventory.InventoryViews.PurchaseDetail;
import com.aishop.api.inventory.InventoryViews.PurchaseItem;
import com.aishop.api.inventory.InventoryViews.PurchaseItemForm;
import com.aishop.api.inventory.InventoryViews.PurchaseSummary;
import com.aishop.api.inventory.InventoryViews.Supplier;
import com.aishop.api.inventory.InventoryViews.SupplierForm;
import com.aishop.api.jooq.tables.records.PurchasesRecord;

@Repository
public class PurchaseRepository {

	private final DSLContext dsl;
	private final JsonbMapper jsonb;

	public PurchaseRepository(DSLContext dsl, JsonbMapper jsonb) {
		this.dsl = dsl;
		this.jsonb = jsonb;
	}

	// ------------------------------------------------------------------
	// 매입처
	// ------------------------------------------------------------------

	public List<Supplier> findSuppliers(long sellerId, boolean includeInactive) {
		return dsl.selectFrom(SUPPLIERS)
			.where(SUPPLIERS.SELLER_ID.eq(sellerId))
			.and(includeInactive ? noCondition() : SUPPLIERS.IS_ACTIVE.isTrue())
			.orderBy(SUPPLIERS.IS_ACTIVE.desc(), SUPPLIERS.NAME)
			.fetch(r -> new Supplier(r.getId(), r.getName(), r.getBusinessNo(), r.getContactName(), r.getPhone(),
					r.getMemo(), r.getIsActive()));
	}

	public boolean supplierBelongsTo(long sellerId, long supplierId) {
		return dsl.fetchExists(SUPPLIERS, SUPPLIERS.ID.eq(supplierId), SUPPLIERS.SELLER_ID.eq(sellerId));
	}

	public boolean supplierUsed(long supplierId) {
		return dsl.fetchExists(PURCHASES, PURCHASES.SUPPLIER_ID.eq(supplierId));
	}

	public void deleteSupplier(long supplierId) {
		dsl.deleteFrom(SUPPLIERS).where(SUPPLIERS.ID.eq(supplierId)).execute();
	}

	public long insertSupplier(long sellerId, SupplierForm f) {
		return dsl.insertInto(SUPPLIERS)
			.set(SUPPLIERS.SELLER_ID, sellerId)
			.set(SUPPLIERS.NAME, f.name().strip())
			.set(SUPPLIERS.BUSINESS_NO, blankToNull(f.businessNo()))
			.set(SUPPLIERS.CONTACT_NAME, blankToNull(f.contactName()))
			.set(SUPPLIERS.PHONE, blankToNull(f.phone()))
			.set(SUPPLIERS.MEMO, blankToNull(f.memo()))
			.returning(SUPPLIERS.ID)
			.fetchOne(SUPPLIERS.ID);
	}

	public void updateSupplier(long supplierId, SupplierForm f) {
		dsl.update(SUPPLIERS)
			.set(SUPPLIERS.NAME, f.name().strip())
			.set(SUPPLIERS.BUSINESS_NO, blankToNull(f.businessNo()))
			.set(SUPPLIERS.CONTACT_NAME, blankToNull(f.contactName()))
			.set(SUPPLIERS.PHONE, blankToNull(f.phone()))
			.set(SUPPLIERS.MEMO, blankToNull(f.memo()))
			.set(SUPPLIERS.IS_ACTIVE, f.active() == null || f.active())
			.where(SUPPLIERS.ID.eq(supplierId))
			.execute();
	}

	// ------------------------------------------------------------------
	// 매입서
	// ------------------------------------------------------------------

	public boolean purchaseNoExists(String purchaseNo) {
		return dsl.fetchExists(PURCHASES, PURCHASES.PURCHASE_NO.eq(purchaseNo));
	}

	public long insertPurchase(long sellerId, String purchaseNo, Long supplierId, long locationId,
			LocalDate purchasedOn, String memo, long actorId) {
		return dsl.insertInto(PURCHASES)
			.set(PURCHASES.SELLER_ID, sellerId)
			.set(PURCHASES.PURCHASE_NO, purchaseNo)
			.set(PURCHASES.SUPPLIER_ID, supplierId)
			.set(PURCHASES.LOCATION_ID, locationId)
			.set(PURCHASES.PURCHASED_ON, purchasedOn)
			.set(PURCHASES.MEMO, blankToNull(memo))
			.set(PURCHASES.CREATED_BY, actorId)
			.returning(PURCHASES.ID)
			.fetchOne(PURCHASES.ID);
	}

	public void updatePurchaseHeader(long purchaseId, Long supplierId, long locationId, LocalDate purchasedOn,
			String memo) {
		dsl.update(PURCHASES)
			.set(PURCHASES.SUPPLIER_ID, supplierId)
			.set(PURCHASES.LOCATION_ID, locationId)
			.set(PURCHASES.PURCHASED_ON, purchasedOn)
			.set(PURCHASES.MEMO, blankToNull(memo))
			.where(PURCHASES.ID.eq(purchaseId))
			.execute();
	}

	/** 품목을 통째로 바꾸고 합계 금액을 다시 계산한다 */
	public void replaceItems(long purchaseId, List<PurchaseItemForm> items) {
		dsl.deleteFrom(PURCHASE_ITEMS).where(PURCHASE_ITEMS.PURCHASE_ID.eq(purchaseId)).execute();
		var insert = dsl.insertInto(PURCHASE_ITEMS, PURCHASE_ITEMS.PURCHASE_ID, PURCHASE_ITEMS.SKU_ID,
				PURCHASE_ITEMS.QTY, PURCHASE_ITEMS.UNIT_COST);
		for (PurchaseItemForm item : items) {
			insert = insert.values(purchaseId, item.skuId(), item.qty(), item.unitCost());
		}
		insert.execute();
		long total = items.stream().mapToLong(i -> i.unitCost() * i.qty()).sum();
		dsl.update(PURCHASES).set(PURCHASES.TOTAL_COST, total).where(PURCHASES.ID.eq(purchaseId)).execute();
	}

	public Optional<PurchasesRecord> lockPurchase(long sellerId, long purchaseId) {
		return dsl.selectFrom(PURCHASES)
			.where(PURCHASES.ID.eq(purchaseId), PURCHASES.SELLER_ID.eq(sellerId))
			.forUpdate()
			.fetchOptional();
	}

	public void markReceived(long purchaseId, long actorId) {
		dsl.update(PURCHASES)
			.set(PURCHASES.STATUS, "RECEIVED")
			.set(PURCHASES.RECEIVED_BY, actorId)
			.set(PURCHASES.RECEIVED_AT, currentOffsetDateTime())
			.where(PURCHASES.ID.eq(purchaseId))
			.execute();
	}

	public void markCancelled(long purchaseId) {
		dsl.update(PURCHASES).set(PURCHASES.STATUS, "CANCELLED").where(PURCHASES.ID.eq(purchaseId)).execute();
	}

	public void updateSkuCost(long skuId, long unitCost) {
		dsl.update(SKUS).set(SKUS.COST, unitCost).where(SKUS.ID.eq(skuId)).execute();
	}

	public PageResponse<PurchaseSummary> findPurchases(long sellerId, String status, String keyword, int page,
			int size) {
		Field<Integer> itemCount = field(select(count()).from(PURCHASE_ITEMS)
			.where(PURCHASE_ITEMS.PURCHASE_ID.eq(PURCHASES.ID))).as("item_count");
		Field<BigDecimal> totalQty = field(select(coalesce(sum(PURCHASE_ITEMS.QTY), inline(BigDecimal.ZERO)))
			.from(PURCHASE_ITEMS)
			.where(PURCHASE_ITEMS.PURCHASE_ID.eq(PURCHASES.ID))).as("total_qty");

		Condition where = PURCHASES.SELLER_ID.eq(sellerId)
			.and(status == null ? noCondition() : PURCHASES.STATUS.eq(status))
			.and(keyword == null || keyword.isBlank() ? noCondition()
					: PURCHASES.PURCHASE_NO.containsIgnoreCase(keyword.strip())
						.or(SUPPLIERS.NAME.containsIgnoreCase(keyword.strip())));

		long total = dsl.fetchCount(dsl.select(PURCHASES.ID)
			.from(PURCHASES)
			.leftJoin(SUPPLIERS).on(SUPPLIERS.ID.eq(PURCHASES.SUPPLIER_ID))
			.where(where));
		List<PurchaseSummary> items = dsl.select(PURCHASES.ID, PURCHASES.PURCHASE_NO, PURCHASES.PURCHASED_ON,
				PURCHASES.STATUS, SUPPLIERS.NAME, LOCATIONS.NAME, itemCount, totalQty, PURCHASES.TOTAL_COST)
			.from(PURCHASES)
			.join(LOCATIONS).on(LOCATIONS.ID.eq(PURCHASES.LOCATION_ID))
			.leftJoin(SUPPLIERS).on(SUPPLIERS.ID.eq(PURCHASES.SUPPLIER_ID))
			.where(where)
			.orderBy(PURCHASES.PURCHASED_ON.desc(), PURCHASES.ID.desc())
			.limit(size)
			.offset(page * size)
			.fetch(r -> new PurchaseSummary(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(),
					r.value7(), r.value8().intValue(), r.value9()));
		return new PageResponse<>(items, page, size, total);
	}

	public Optional<PurchaseDetail> findPurchase(long sellerId, long purchaseId) {
		return dsl.select(PURCHASES.ID, PURCHASES.PURCHASE_NO, PURCHASES.PURCHASED_ON, PURCHASES.STATUS,
				PURCHASES.SUPPLIER_ID, SUPPLIERS.NAME, PURCHASES.LOCATION_ID, LOCATIONS.NAME, PURCHASES.MEMO,
				PURCHASES.TOTAL_COST, PURCHASES.RECEIVED_AT)
			.from(PURCHASES)
			.join(LOCATIONS).on(LOCATIONS.ID.eq(PURCHASES.LOCATION_ID))
			.leftJoin(SUPPLIERS).on(SUPPLIERS.ID.eq(PURCHASES.SUPPLIER_ID))
			.where(PURCHASES.ID.eq(purchaseId), PURCHASES.SELLER_ID.eq(sellerId))
			.fetchOptional(r -> new PurchaseDetail(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(),
					r.value6(), r.value7(), r.value8(), r.value9(), r.value10(), r.value11(), findItems(purchaseId)));
	}

	public List<PurchaseItem> findItems(long purchaseId) {
		return dsl.select(PURCHASE_ITEMS.SKU_ID, SKUS.SKU_CODE, PRODUCTS.NAME, SKUS.OPTION_VALUES,
				SKUS.CONDITION_GRADE, SKUS.PRICE, PURCHASE_ITEMS.QTY, PURCHASE_ITEMS.UNIT_COST)
			.from(PURCHASE_ITEMS)
			.join(SKUS).on(SKUS.ID.eq(PURCHASE_ITEMS.SKU_ID))
			.join(PRODUCTS).on(PRODUCTS.ID.eq(SKUS.PRODUCT_ID))
			.where(PURCHASE_ITEMS.PURCHASE_ID.eq(purchaseId))
			.orderBy(PURCHASE_ITEMS.ID)
			.fetch(r -> new PurchaseItem(r.value1(), r.value2(), r.value3(),
					InventoryRepository.optionText(jsonb.toStringMap(r.value4())),
					Grade.of(ConditionGrade.valueOf(r.value5())), r.value6(), r.value7(), r.value8()));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
