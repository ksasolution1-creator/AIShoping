package com.aishop.api.order;

import java.util.Map;

import com.aishop.api.catalog.ConditionGrade;

/**
 * 구매하려는 SKU 한 줄과 가격 계산에 필요한 값 (장바구니·주문 공통).
 *
 * @param available 지금 살 수 있는 수량 (보유 - 예약)
 * @param purchasable 상품이 판매중이고 SKU가 판매 상태인지
 */
public record PurchaseLine(
		long skuId,
		long productId,
		long sellerId,
		String productName,
		Map<String, String> optionValues,
		ConditionGrade grade,
		long price,
		Long listPrice,
		long shippingFee,
		Long freeShippingThreshold,
		String thumbnailUrl,
		int available,
		boolean purchasable,
		int qty) {

	public long lineTotal() {
		return price * qty;
	}

	public PurchaseLine withQty(int newQty) {
		return new PurchaseLine(skuId, productId, sellerId, productName, optionValues, grade, price, listPrice,
				shippingFee, freeShippingThreshold, thumbnailUrl, available, purchasable, newQty);
	}

	public String optionText() {
		return String.join(", ", optionValues.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).toList());
	}

}
