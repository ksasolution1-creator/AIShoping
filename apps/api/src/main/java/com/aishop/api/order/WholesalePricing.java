package com.aishop.api.order;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aishop.api.catalog.WholesaleRepository;
import com.aishop.api.catalog.WholesaleTerms;

/**
 * 도매 구매조건 적용: 같은 상품(옵션·등급 합계) 수량으로 수량별 할인을 정하고, 최소구매수량을 확인한다.
 * 장바구니·주문서 미리보기·주문 생성이 모두 같은 규칙을 쓴다.
 */
@Component
public class WholesalePricing {

	private final WholesaleRepository wholesale;

	public WholesalePricing(WholesaleRepository wholesale) {
		this.wholesale = wholesale;
	}

	/**
	 * @param lines 할인 적용된 구매 라인 (입력 순서 유지)
	 * @param belowMinimum 최소구매수량에 못 미친 상품 ID → 안내 문구
	 */
	public record Applied(List<PurchaseLine> lines, Map<Long, String> belowMinimum) {

		public boolean meetsMinimum(long productId) {
			return !belowMinimum.containsKey(productId);
		}

	}

	public Applied apply(List<PurchaseLine> lines) {
		Map<Long, Integer> qtyByProduct = new LinkedHashMap<>();
		Map<Long, String> nameByProduct = new LinkedHashMap<>();
		for (PurchaseLine line : lines) {
			qtyByProduct.merge(line.productId(), line.qty(), Integer::sum);
			nameByProduct.putIfAbsent(line.productId(), line.productName());
		}
		Map<Long, WholesaleTerms> terms = wholesale.findAll(qtyByProduct.keySet());

		List<PurchaseLine> priced = new ArrayList<>();
		for (PurchaseLine line : lines) {
			WholesaleTerms t = terms.getOrDefault(line.productId(), WholesaleTerms.DEFAULT);
			int bp = t.discountBpFor(qtyByProduct.get(line.productId()));
			priced.add(line.withUnitDiscount(WholesaleTerms.unitDiscount(line.price(), bp)));
		}

		Map<Long, String> belowMinimum = new LinkedHashMap<>();
		qtyByProduct.forEach((productId, qty) -> {
			int min = terms.getOrDefault(productId, WholesaleTerms.DEFAULT).minOrderQty();
			if (qty < min) {
				belowMinimum.put(productId,
						"'" + nameByProduct.get(productId) + "'은(는) 최소 " + min + "개부터 살 수 있습니다. (지금 " + qty + "개)");
			}
		});
		return new Applied(priced, belowMinimum);
	}

}
