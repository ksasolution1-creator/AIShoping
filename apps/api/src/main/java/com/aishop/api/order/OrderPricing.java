package com.aishop.api.order;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 주문 금액 계산. 판매자별로 묶어서 배송비를 계산한다.
 * - 판매자 배송비 = 그 판매자 상품들의 배송비 중 가장 큰 값 (한 번만 부과)
 * - 판매자 상품 합계가 무료배송 기준 금액(상품들 중 가장 낮은 기준) 이상이면 무료
 */
public final class OrderPricing {

	private OrderPricing() {
	}

	public record Summary(long itemsTotal, long shippingFee, long discountTotal, long payAmount,
			Map<Long, Long> shippingBySeller) {
	}

	public static Summary calculate(List<PurchaseLine> lines) {
		Map<Long, List<PurchaseLine>> bySeller = new LinkedHashMap<>();
		for (PurchaseLine line : lines) {
			bySeller.computeIfAbsent(line.sellerId(), k -> new java.util.ArrayList<>()).add(line);
		}

		long itemsTotal = 0;
		long shippingTotal = 0;
		Map<Long, Long> shippingBySeller = new LinkedHashMap<>();
		for (Map.Entry<Long, List<PurchaseLine>> entry : bySeller.entrySet()) {
			List<PurchaseLine> sellerLines = entry.getValue();
			long subtotal = sellerLines.stream().mapToLong(PurchaseLine::lineTotal).sum();
			long fee = sellerLines.stream().mapToLong(PurchaseLine::shippingFee).max().orElse(0);
			Long threshold = sellerLines.stream()
				.map(PurchaseLine::freeShippingThreshold)
				.filter(Objects::nonNull)
				.min(Long::compare)
				.orElse(null);
			if (threshold != null && subtotal >= threshold) {
				fee = 0;
			}
			itemsTotal += subtotal;
			shippingTotal += fee;
			shippingBySeller.put(entry.getKey(), fee);
		}
		return new Summary(itemsTotal, shippingTotal, 0, itemsTotal + shippingTotal, shippingBySeller);
	}

}
