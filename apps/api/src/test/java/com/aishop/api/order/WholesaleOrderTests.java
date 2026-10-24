package com.aishop.api.order;

import static com.aishop.api.jooq.Tables.ORDERS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.aishop.api.TestcontainersConfiguration;
import com.aishop.api.auth.AuthUser;
import com.aishop.api.auth.UserRepository;
import com.aishop.api.cart.CartService;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.catalog.ProductService;
import com.aishop.api.catalog.ProductStatus;
import com.aishop.api.catalog.WholesaleTerms;
import com.aishop.api.catalog.WholesaleTerms.PriceTier;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.order.OrderService.Preview;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.payment.TossPaymentsClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WholesaleOrderTests {

	private static final long SELLER = 1L;

	@MockitoBean
	TossPaymentsClient toss;

	@Autowired
	OrderService orderService;

	@Autowired
	ProductService productService;

	@Autowired
	CartService cartService;

	@Autowired
	UserRepository users;

	@Autowired
	DSLContext dsl;

	AuthUser customer;

	@BeforeEach
	void setUp() {
		when(toss.isConfigured()).thenReturn(true);
		when(toss.clientKey()).thenReturn("test_ck_dummy");
		String email = "whole-" + uniq() + "@example.com";
		customer = new AuthUser(users.insert(email, null, "도매구매자", null, "CUSTOMER"), email, "도매구매자", "CUSTOMER",
				Set.of(), null);
	}

	@Test
	void tierDiscountUsesTotalQtyOfProductAcrossGrades() {
		// 10개 이상 5%, 50개 이상 10%. 무료배송 기준이 없어 배송비 3,000원
		long[] skus = product(new WholesaleTerms(1, 2, List.of(new PriceTier(10, 500), new PriceTier(50, 1000))),
				1_990L, 2_990L);

		Preview few = orderService.preview(customer.id(), List.of(new OrderLineRequest(skus[0], 9)));
		assertThat(few.discountTotal()).isZero();

		// 등급이 다른 두 SKU 합계 12개 → 5% (1,990원 → 단가 할인 90원, 2,990원 → 140원. 10원 단위 내림)
		Preview mixed = orderService.preview(customer.id(), List.of(new OrderLineRequest(skus[0], 7), new OrderLineRequest(skus[1], 5)));
		assertThat(mixed.lines()).extracting(OrderService.PreviewLine::unitDiscount).containsExactly(90L, 140L);
		long gross = 1_990L * 7 + 2_990L * 5;
		long discount = 90L * 7 + 140L * 5;
		assertThat(mixed.itemsTotal()).isEqualTo(gross);
		assertThat(mixed.discountTotal()).isEqualTo(discount);
		assertThat(mixed.payAmount()).isEqualTo(gross - discount + 3_000L);
		assertThat(mixed.allPurchasable()).isTrue();
	}

	@Test
	void orderStoresDiscountAndPayAmount() {
		long sku = product(new WholesaleTerms(1, 3, List.of(new PriceTier(10, 1000))), 5_000L)[0];

		CheckoutInfo checkout = orderService.createOrder(customer, order(sku, 10));

		var order = dsl.selectFrom(ORDERS).where(ORDERS.ORDER_NO.eq(checkout.orderNo())).fetchOne();
		assertThat(order.getItemsTotal()).isEqualTo(50_000L);
		assertThat(order.getDiscountTotal()).isEqualTo(5_000L);
		assertThat(order.getPayAmount()).isEqualTo(45_000L + 3_000L);
		assertThat(checkout.amount()).isEqualTo(order.getPayAmount());
		var item = dsl.selectFrom(ORDER_ITEMS).where(ORDER_ITEMS.ORDER_ID.eq(order.getId())).fetchOne();
		assertThat(item.getUnitPrice()).isEqualTo(5_000L);
		assertThat(item.getDiscount()).isEqualTo(5_000L);
	}

	@Test
	void belowMinimumQtyIsBlocked() {
		long sku = product(new WholesaleTerms(3, 3, List.of()), 1_000L)[0];

		Preview preview = orderService.preview(customer.id(), List.of(new OrderLineRequest(sku, 2)));
		assertThat(preview.allPurchasable()).isFalse();
		assertThat(preview.notices()).singleElement().asString().contains("최소 3개");

		assertThatThrownBy(() -> orderService.createOrder(customer, order(sku, 2)))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("최소 3개");
		assertThat(orderService.createOrder(customer, order(sku, 3)).orderNo()).isNotBlank();
	}

	@Test
	void cartShowsDiscountAndMinimumNotice() {
		long sku = product(new WholesaleTerms(5, 3, List.of(new PriceTier(5, 2000))), 10_000L)[0];

		cartService.add(customer.id(), sku, 2);
		var cart = cartService.view(customer.id());
		assertThat(cart.items().getFirst().minimumNotice()).contains("최소 5개");
		assertThat(cart.discountTotal()).isZero();

		cartService.changeQty(customer.id(), sku, 5);
		cart = cartService.view(customer.id());
		assertThat(cart.items().getFirst().minimumNotice()).isNull();
		assertThat(cart.discountTotal()).isEqualTo(2_000L * 5);
	}

	@Test
	void tiersMustIncreaseWithQty() {
		long productId = productId(1_000L);
		assertThatThrownBy(() -> productService.updateWholesale(SELLER, productId,
				new WholesaleTerms(1, 3, List.of(new PriceTier(10, 1000), new PriceTier(20, 500)))))
			.isInstanceOf(ApiException.class);

		var detail = productService.updateWholesale(SELLER, productId,
				new WholesaleTerms(2, 0, List.of(new PriceTier(20, 1000), new PriceTier(10, 500))));
		assertThat(detail.wholesale().minOrderQty()).isEqualTo(2);
		assertThat(detail.wholesale().tiers()).extracting(PriceTier::minQty).containsExactly(10, 20);
		assertThat(productService.getShopProduct(productId).wholesale().shipWithinDays()).isZero();
	}

	// ------------------------------------------------------------------

	private long[] product(WholesaleTerms terms, long... prices) {
		long productId = productId(prices);
		productService.updateWholesale(SELLER, productId, terms);
		return productService.getAdminProduct(SELLER, productId).skus().stream().mapToLong(s -> s.id()).toArray();
	}

	private long productId(long... prices) {
		ProductFields fields = new ProductFields("도매조건 " + uniq(), null, null, null, null, Map.of(), null, 3_000L, null);
		List<CreateSku> skus = new ArrayList<>();
		ConditionGrade[] grades = { ConditionGrade.NEW, ConditionGrade.RETURN_OPENED };
		for (int i = 0; i < prices.length; i++) {
			skus.add(new CreateSku(null, Map.of(), grades[i], null, null, prices[i], null, 100));
		}
		long productId = productService.createProduct(SELLER, new CreateProduct(fields, skus));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		return productId;
	}

	private static CreateOrder order(long skuId, int qty) {
		return new CreateOrder(List.of(new OrderLineRequest(skuId, qty)), "홍길동", "01012345678", "06236",
				"서울시 강남구 테헤란로 1", "101호", null);
	}

	private static String uniq() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
