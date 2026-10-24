package com.aishop.api.order;

import java.util.Map;

import com.aishop.api.catalog.ConditionGrade;

/**
 * 구매하려는 SKU 한 줄과 가격 계산에 필요한 값 (장바구니·주문 공통).
 *
 * @param available 지금 살 수 있는 수량 (보유 - 예약)
 * @param purchasable 상품이 판매중이고 SKU가 판매 상태인지
 * @param unitDiscount 도매 수량별 할인으로 단가에서 빠지는 금액 ({@link WholesalePricing}이 채운다)
 * @param businessOnly 사업자 회원 전용 상품인지 (사업자가 아니면 purchasable이 false)
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
		int qty,
		long unitDiscount,
		boolean businessOnly) {

	/** 할인 전 금액 */
	public long grossTotal() {
		return price * qty;
	}

	public long discountTotal() {
		return unitDiscount * qty;
	}

	/** 할인 후 금액 (실제 결제되는 상품 금액) */
	public long lineTotal() {
		return grossTotal() - discountTotal();
	}

	public PurchaseLine withQty(int newQty) {
		return new PurchaseLine(skuId, productId, sellerId, productName, optionValues, grade, price, listPrice,
				shippingFee, freeShippingThreshold, thumbnailUrl, available, purchasable, newQty, unitDiscount,
				businessOnly);
	}

	public PurchaseLine withUnitDiscount(long discount) {
		return new PurchaseLine(skuId, productId, sellerId, productName, optionValues, grade, price, listPrice,
				shippingFee, freeShippingThreshold, thumbnailUrl, available, purchasable, qty, discount, businessOnly);
	}

	public String optionText() {
		return String.join(", ", optionValues.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).toList());
	}

}
