package com.aishop.api.claim;

import static com.aishop.api.jooq.Tables.CARRIERS;
import static com.aishop.api.jooq.Tables.CLAIMS;
import static com.aishop.api.jooq.Tables.ORDERS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.REFUNDS;
import static com.aishop.api.jooq.Tables.SKUS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.noCondition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import com.aishop.api.jooq.tables.records.ClaimsRecord;
import com.aishop.api.jooq.tables.records.SkusRecord;

@Repository
public class ClaimRepository {

	private final DSLContext dsl;

	public ClaimRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public long insert(ClaimsRecord claim) {
		return dsl.insertInto(CLAIMS).set(claim).returning(CLAIMS.ID).fetchOne(CLAIMS.ID);
	}

	public Optional<ClaimsRecord> lock(long claimId) {
		return dsl.selectFrom(CLAIMS).where(CLAIMS.ID.eq(claimId)).forUpdate().fetchOptional();
	}

	public void update(ClaimsRecord claim) {
		dsl.executeUpdate(claim);
	}

	public boolean hasOpenClaim(long orderItemId) {
		return dsl.fetchExists(CLAIMS, CLAIMS.ORDER_ITEM_ID.eq(orderItemId)
			.and(CLAIMS.STATUS.notIn("COMPLETED", "REJECTED", "WITHDRAWN")));
	}

	/** 클레임 + 주문상품 + 주문 정보 (목록·상세 화면용) */
	public List<Record> findViews(Condition condition, int limit, int offset) {
		return dsl.select(CLAIMS.fields())
			.select(ORDER_ITEMS.PRODUCT_NAME, ORDER_ITEMS.OPTION_TEXT, ORDER_ITEMS.CONDITION_GRADE, ORDER_ITEMS.SKU_ID,
					ORDER_ITEMS.UNIT_PRICE, ORDER_ITEMS.DISCOUNT, ORDER_ITEMS.SELLER_ID, ORDERS.ORDER_NO,
					ORDERS.ORDERER_NAME, CARRIERS.NAME)
			.from(CLAIMS)
			.join(ORDER_ITEMS).on(ORDER_ITEMS.ID.eq(CLAIMS.ORDER_ITEM_ID))
			.join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDER_ID))
			.leftJoin(CARRIERS).on(CARRIERS.ID.eq(CLAIMS.PICKUP_CARRIER_ID))
			.where(condition)
			.orderBy(CLAIMS.REQUESTED_AT.desc())
			.limit(limit)
			.offset(offset)
			.fetch();
	}

	public int count(Condition condition) {
		return dsl.fetchCount(dsl.select(CLAIMS.ID)
			.from(CLAIMS)
			.join(ORDER_ITEMS).on(ORDER_ITEMS.ID.eq(CLAIMS.ORDER_ITEM_ID))
			.join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDER_ID))
			.where(condition));
	}

	public static Condition forSeller(long sellerId, Collection<String> statuses, String type) {
		return ORDER_ITEMS.SELLER_ID.eq(sellerId)
			.and(statuses == null || statuses.isEmpty() ? noCondition() : CLAIMS.STATUS.in(statuses))
			.and(type == null ? noCondition() : CLAIMS.TYPE.eq(type));
	}

	public static Condition forOrder(long orderId) {
		return ORDER_ITEMS.ORDER_ID.eq(orderId);
	}

	public static Condition byId(long claimId) {
		return CLAIMS.ID.eq(claimId);
	}

	/** 반품 배송비 계산용: 이 SKU 상품의 편도 배송비 */
	public long findShippingFee(Long skuId) {
		if (skuId == null) {
			return 0;
		}
		Long fee = dsl.select(PRODUCTS.SHIPPING_FEE)
			.from(SKUS).join(PRODUCTS).on(PRODUCTS.ID.eq(SKUS.PRODUCT_ID))
			.where(SKUS.ID.eq(skuId))
			.fetchOne(PRODUCTS.SHIPPING_FEE);
		return fee == null ? 0 : fee;
	}

	public Optional<SkusRecord> findSku(long skuId) {
		return dsl.selectFrom(SKUS).where(SKUS.ID.eq(skuId)).fetchOptional();
	}

	/** 같은 상품·같은 옵션·지정한 상태등급의 SKU (재입고 대상) */
	public Optional<SkusRecord> findSiblingSku(long productId, JSONB optionValues, String grade) {
		return dsl.selectFrom(SKUS)
			.where(SKUS.PRODUCT_ID.eq(productId), SKUS.OPTION_VALUES.eq(optionValues), SKUS.CONDITION_GRADE.eq(grade))
			.and(SKUS.STATUS.ne("DELETED"))
			.orderBy(SKUS.ID)
			.limit(1)
			.fetchOptional();
	}

	public boolean skuCodeExists(String code) {
		return dsl.fetchExists(SKUS, SKUS.SKU_CODE.eq(code));
	}

	public long insertSku(SkusRecord sku) {
		return dsl.insertInto(SKUS).set(sku).returning(SKUS.ID).fetchOne(SKUS.ID);
	}

	public void insertRefund(long paymentId, long claimId, long amount, String reason, String idempotencyKey,
			String transactionKey, String raw) {
		dsl.insertInto(REFUNDS)
			.set(REFUNDS.PAYMENT_ID, paymentId)
			.set(REFUNDS.CLAIM_ID, claimId)
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

	public void touchCompleted(long claimId) {
		dsl.update(CLAIMS).set(CLAIMS.COMPLETED_AT, currentOffsetDateTime()).where(CLAIMS.ID.eq(claimId)).execute();
	}

}
