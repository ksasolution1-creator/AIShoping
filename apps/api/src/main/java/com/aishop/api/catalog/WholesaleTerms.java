package com.aishop.api.catalog;

import java.util.Comparator;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 상품의 도매 구매조건.
 *
 * @param minOrderQty 한 주문에서 이 상품(옵션·등급 합계)을 최소 몇 개 사야 하는지
 * @param shipWithinDays 결제 후 며칠 안에 출고하는지 (0이면 당일 출고)
 * @param tiers 수량별 할인. 같은 상품 합계 수량이 minQty 이상이면 단가에서 discountBp만큼 깎는다
 */
public record WholesaleTerms(
		@Min(value = 1, message = "최소구매수량은 1개 이상이어야 합니다.") @Max(10_000) int minOrderQty,
		@Min(0) @Max(value = 30, message = "출고 예정일은 30일 이내로 정해 주세요.") int shipWithinDays,
		@Size(max = 10, message = "수량별 할인은 10단계까지 정할 수 있습니다.") List<@Valid PriceTier> tiers) {

	public static final WholesaleTerms DEFAULT = new WholesaleTerms(1, 3, List.of());

	public WholesaleTerms {
		tiers = tiers == null ? List.of()
				: tiers.stream().sorted(Comparator.comparingInt(PriceTier::minQty)).toList();
	}

	/** 합계 수량에 맞는 할인율(bp). 해당하는 단계가 없으면 0 */
	public int discountBpFor(int totalQty) {
		int bp = 0;
		for (PriceTier tier : tiers) {
			if (totalQty >= tier.minQty()) {
				bp = tier.discountBp();
			}
		}
		return bp;
	}

	/** 할인 후 단가에서 깎이는 금액 (10원 단위 내림으로 할인해서 소수점 금액이 생기지 않게) */
	public static long unitDiscount(long price, int discountBp) {
		return price * discountBp / 10_000 / 10 * 10;
	}

	public record PriceTier(
			@Min(value = 2, message = "수량별 할인은 2개 이상부터 정할 수 있습니다.") @Max(10_000) int minQty,
			@Min(value = 1, message = "할인율은 0.01% 이상이어야 합니다.") @Max(value = 9000, message = "할인율은 90%까지 정할 수 있습니다.") int discountBp) {
	}

}
