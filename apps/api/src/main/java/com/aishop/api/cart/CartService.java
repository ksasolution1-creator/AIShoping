package com.aishop.api.cart;

import static com.aishop.api.jooq.Tables.CART_ITEMS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.business.BusinessRepository;
import com.aishop.api.catalog.CatalogViews.Grade;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.order.OrderPricing;
import com.aishop.api.order.PurchaseLine;
import com.aishop.api.order.PurchaseLineRepository;
import com.aishop.api.order.WholesalePricing;

@Service
public class CartService {

	public static final int MAX_QTY_PER_ITEM = 99;

	private final DSLContext dsl;
	private final PurchaseLineRepository purchaseLines;
	private final WholesalePricing wholesale;
	private final BusinessRepository business;

	public CartService(DSLContext dsl, PurchaseLineRepository purchaseLines, WholesalePricing wholesale,
			BusinessRepository business) {
		this.dsl = dsl;
		this.purchaseLines = purchaseLines;
		this.wholesale = wholesale;
		this.business = business;
	}

	/**
	 * @param unitDiscount 도매 수량 할인으로 단가에서 빠지는 금액
	 * @param minimumNotice 최소구매수량에 못 미치면 안내 문구, 아니면 null
	 */
	public record CartItem(long skuId, long productId, String productName, String optionText, Grade grade,
			String thumbnailUrl, long price, Long listPrice, long unitDiscount, int qty, int available,
			boolean purchasable, long lineTotal, String minimumNotice) {
	}

	/** 구매 가능한 상품만 합계에 넣는다 (품절·판매중지 상품은 목록에만 표시) */
	public record CartView(List<CartItem> items, long itemsTotal, long discountTotal, long shippingFee,
			long payAmount) {
	}

	@Transactional(readOnly = true)
	public CartView view(long userId) {
		List<Record2<Long, Integer>> rows = dsl.select(CART_ITEMS.SKU_ID, CART_ITEMS.QTY)
			.from(CART_ITEMS)
			.where(CART_ITEMS.USER_ID.eq(userId))
			.orderBy(CART_ITEMS.CREATED_AT.desc())
			.fetch();
		Map<Long, PurchaseLine> lines = purchaseLines.findBySkuIds(rows.stream().map(Record2::value1).toList(),
				business.isApprovedBusiness(userId));

		List<PurchaseLine> inCart = rows.stream()
			.filter(row -> lines.containsKey(row.value1()))
			.map(row -> lines.get(row.value1()).withQty(row.value2()))
			.toList();
		WholesalePricing.Applied applied = wholesale.apply(inCart);

		List<CartItem> items = new ArrayList<>();
		List<PurchaseLine> buyable = new ArrayList<>();
		for (PurchaseLine line : applied.lines()) {
			boolean ok = line.purchasable() && line.available() >= line.qty();
			if (ok) {
				buyable.add(line);
			}
			items.add(new CartItem(line.skuId(), line.productId(), line.productName(), line.optionText(),
					Grade.of(line.grade()), line.thumbnailUrl(), line.price(), line.listPrice(), line.unitDiscount(),
					line.qty(), line.available(), ok, line.lineTotal(), applied.belowMinimum().get(line.productId())));
		}
		OrderPricing.Summary summary = OrderPricing.calculate(buyable);
		return new CartView(items, summary.itemsTotal(), summary.discountTotal(), summary.shippingFee(),
				summary.payAmount());
	}

	/** 담기. 이미 있으면 수량을 더한다 */
	@Transactional
	public CartView add(long userId, long skuId, int qty) {
		PurchaseLine line = requirePurchasable(userId, skuId);
		Integer current = dsl.select(CART_ITEMS.QTY).from(CART_ITEMS)
			.where(CART_ITEMS.USER_ID.eq(userId), CART_ITEMS.SKU_ID.eq(skuId))
			.fetchOne(CART_ITEMS.QTY);
		int next = (current == null ? 0 : current) + qty;
		checkQty(line, next);
		dsl.insertInto(CART_ITEMS)
			.set(CART_ITEMS.USER_ID, userId)
			.set(CART_ITEMS.SKU_ID, skuId)
			.set(CART_ITEMS.QTY, next)
			.onConflict(CART_ITEMS.USER_ID, CART_ITEMS.SKU_ID)
			.doUpdate()
			.set(CART_ITEMS.QTY, next)
			.execute();
		return view(userId);
	}

	@Transactional
	public CartView changeQty(long userId, long skuId, int qty) {
		checkQty(requirePurchasable(userId, skuId), qty);
		int updated = dsl.update(CART_ITEMS)
			.set(CART_ITEMS.QTY, qty)
			.where(CART_ITEMS.USER_ID.eq(userId), CART_ITEMS.SKU_ID.eq(skuId))
			.execute();
		if (updated == 0) {
			throw ApiException.notFound("장바구니 상품");
		}
		return view(userId);
	}

	@Transactional
	public CartView remove(long userId, List<Long> skuIds) {
		dsl.deleteFrom(CART_ITEMS)
			.where(CART_ITEMS.USER_ID.eq(userId), CART_ITEMS.SKU_ID.in(skuIds))
			.execute();
		return view(userId);
	}

	private PurchaseLine requirePurchasable(long userId, long skuId) {
		PurchaseLine line = purchaseLines.findBySkuIds(List.of(skuId), business.isApprovedBusiness(userId)).get(skuId);
		if (line != null && !line.purchasable() && line.businessOnly()) {
			throw ApiException.badRequest("BUSINESS_ONLY", "사업자 회원 전용 상품입니다. 마이페이지에서 사업자 인증을 받은 뒤 구매해 주세요.");
		}
		if (line == null || !line.purchasable()) {
			throw ApiException.badRequest("NOT_PURCHASABLE", "지금 구매할 수 없는 상품입니다.");
		}
		return line;
	}

	private static void checkQty(PurchaseLine line, int qty) {
		if (qty < 1 || qty > MAX_QTY_PER_ITEM) {
			throw ApiException.badRequest("INVALID_QTY", "수량은 1~" + MAX_QTY_PER_ITEM + "개로 담을 수 있습니다.");
		}
		if (qty > line.available()) {
			throw ApiException.conflict("OUT_OF_STOCK",
					line.available() <= 0 ? "품절된 상품입니다." : "남은 수량은 " + line.available() + "개입니다.");
		}
	}

}
