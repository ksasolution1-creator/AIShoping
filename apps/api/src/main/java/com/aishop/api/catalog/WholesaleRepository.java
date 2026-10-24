package com.aishop.api.catalog;

import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.PRODUCT_PRICE_TIERS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import com.aishop.api.catalog.WholesaleTerms.PriceTier;

/** 상품의 도매 구매조건(최소구매수량·출고 예정일·수량별 할인) 조회와 저장 */
@Repository
public class WholesaleRepository {

	private final DSLContext dsl;

	public WholesaleRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public WholesaleTerms find(long productId) {
		return findAll(List.of(productId)).getOrDefault(productId, WholesaleTerms.DEFAULT);
	}

	public Map<Long, WholesaleTerms> findAll(Collection<Long> productIds) {
		Map<Long, WholesaleTerms> result = new HashMap<>();
		if (productIds.isEmpty()) {
			return result;
		}
		Map<Long, List<PriceTier>> tiers = new HashMap<>();
		dsl.selectFrom(PRODUCT_PRICE_TIERS)
			.where(PRODUCT_PRICE_TIERS.PRODUCT_ID.in(productIds))
			.forEach(r -> tiers.computeIfAbsent(r.getProductId(), k -> new ArrayList<>())
				.add(new PriceTier(r.getMinQty(), r.getDiscountBp())));
		dsl.select(PRODUCTS.ID, PRODUCTS.MIN_ORDER_QTY, PRODUCTS.SHIP_WITHIN_DAYS)
			.from(PRODUCTS)
			.where(PRODUCTS.ID.in(productIds))
			.forEach(r -> result.put(r.value1(),
					new WholesaleTerms(r.value2(), r.value3(), tiers.getOrDefault(r.value1(), List.of()))));
		return result;
	}

	public void save(long productId, WholesaleTerms terms) {
		dsl.update(PRODUCTS)
			.set(PRODUCTS.MIN_ORDER_QTY, terms.minOrderQty())
			.set(PRODUCTS.SHIP_WITHIN_DAYS, terms.shipWithinDays())
			.where(PRODUCTS.ID.eq(productId))
			.execute();
		dsl.deleteFrom(PRODUCT_PRICE_TIERS).where(PRODUCT_PRICE_TIERS.PRODUCT_ID.eq(productId)).execute();
		for (PriceTier tier : terms.tiers()) {
			dsl.insertInto(PRODUCT_PRICE_TIERS)
				.set(PRODUCT_PRICE_TIERS.PRODUCT_ID, productId)
				.set(PRODUCT_PRICE_TIERS.MIN_QTY, tier.minQty())
				.set(PRODUCT_PRICE_TIERS.DISCOUNT_BP, tier.discountBp())
				.execute();
		}
	}

}
