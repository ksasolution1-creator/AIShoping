package com.aishop.api.order;

import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.PRODUCT_IMAGES;
import static com.aishop.api.jooq.Tables.SKUS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.common.db.JsonbMapper;

/** 장바구니·주문에서 쓰는 SKU 구매 정보 조회 */
@Repository
public class PurchaseLineRepository {

	private final DSLContext dsl;
	private final JsonbMapper jsonb;

	public PurchaseLineRepository(DSLContext dsl, JsonbMapper jsonb) {
		this.dsl = dsl;
		this.jsonb = jsonb;
	}

	/** SKU ID → 구매 정보 (수량은 1로 채움). 없는 SKU는 결과에서 빠진다 */
	public Map<Long, PurchaseLine> findBySkuIds(Collection<Long> skuIds) {
		if (skuIds.isEmpty()) {
			return Map.of();
		}
		Field<BigDecimal> available = field(select(coalesce(sum(INVENTORIES.ON_HAND.minus(INVENTORIES.RESERVED)),
				BigDecimal.ZERO)).from(INVENTORIES).where(INVENTORIES.SKU_ID.eq(SKUS.ID)));
		// 이 SKU의 실물 사진이 있으면 그것을, 없으면 상품 공통 사진
		Field<String> thumbnail = field(select(PRODUCT_IMAGES.URL).from(PRODUCT_IMAGES)
			.where(PRODUCT_IMAGES.PRODUCT_ID.eq(SKUS.PRODUCT_ID))
			.and(PRODUCT_IMAGES.SKU_ID.eq(SKUS.ID).or(PRODUCT_IMAGES.SKU_ID.isNull()))
			.orderBy(PRODUCT_IMAGES.SKU_ID.nullsLast(), PRODUCT_IMAGES.SORT_ORDER, PRODUCT_IMAGES.ID)
			.limit(1));

		Map<Long, PurchaseLine> result = new LinkedHashMap<>();
		dsl.select(SKUS.ID, SKUS.PRODUCT_ID, PRODUCTS.SELLER_ID, PRODUCTS.NAME, SKUS.OPTION_VALUES,
				SKUS.CONDITION_GRADE, SKUS.PRICE, SKUS.LIST_PRICE, PRODUCTS.SHIPPING_FEE,
				PRODUCTS.FREE_SHIPPING_THRESHOLD, thumbnail, available, PRODUCTS.STATUS, SKUS.STATUS)
			.from(SKUS)
			.join(PRODUCTS).on(PRODUCTS.ID.eq(SKUS.PRODUCT_ID))
			.where(SKUS.ID.in(skuIds))
			.forEach(r -> result.put(r.value1(), new PurchaseLine(
					r.value1(), r.value2(), r.value3(), r.value4(), jsonb.toStringMap(r.value5()),
					ConditionGrade.valueOf(r.value6()), r.value7(), r.value8(), r.value9(), r.value10(), r.value11(),
					r.value12() == null ? 0 : r.value12().intValue(),
					"ON_SALE".equals(r.value13()) && "ACTIVE".equals(r.value14()), 1)));
		return result;
	}

}
