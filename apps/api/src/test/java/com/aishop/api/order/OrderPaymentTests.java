package com.aishop.api.order;

import static com.aishop.api.jooq.Tables.CART_ITEMS;
import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.INVENTORY_MOVEMENTS;
import static com.aishop.api.jooq.Tables.ORDERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
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
import com.aishop.api.common.web.ApiException;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderDetail;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.payment.PaymentService;
import com.aishop.api.payment.PaymentService.Outcome;
import com.aishop.api.payment.TossPaymentsClient;
import com.aishop.api.payment.TossPaymentsClient.TossApiException;
import com.aishop.api.payment.TossPaymentsClient.TossPayment;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderPaymentTests {

	private static final long SELLER = 1L;
	private static final long WAREHOUSE = 1L;

	@MockitoBean
	TossPaymentsClient toss;

	@Autowired
	OrderService orderService;

	@Autowired
	PaymentService paymentService;

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
		String email = "buyer-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		long userId = users.insert(email, null, "구매자", null, "CUSTOMER");
		customer = new AuthUser(userId, email, "구매자", "CUSTOMER", Set.of(), null);
	}

	@Test
	void pricingChargesShippingOncePerSellerAndAppliesFreeThreshold() {
		PurchaseLine a = line(1, 10_000, 3_000, 50_000L, 2);
		PurchaseLine b = line(1, 15_000, 2_500, null, 1);
		assertThat(OrderPricing.calculate(List.of(a, b)).shippingFee()).isEqualTo(3_000);
		assertThat(OrderPricing.calculate(List.of(a, b)).payAmount()).isEqualTo(38_000);

		PurchaseLine big = line(1, 60_000, 3_000, 50_000L, 1);
		assertThat(OrderPricing.calculate(List.of(big)).shippingFee()).isZero();

		PurchaseLine otherSeller = line(2, 10_000, 4_000, null, 1);
		assertThat(OrderPricing.calculate(List.of(a, otherSeller)).shippingFee()).isEqualTo(7_000);
	}

	@Test
	void successfulPaymentMarksPaidAndClearsCart() {
		long skuId = createSku(30_000, 5);
		cartService.add(customer.id(), skuId, 2);

		CheckoutInfo checkout = createOrder(skuId, 2);
		assertThat(checkout.amount()).isEqualTo(60_000); // 5만원 이상 무료배송
		assertThat(available(skuId)).isEqualTo(3); // 예약됨

		when(toss.confirm(eq("pk_ok"), eq(checkout.orderNo()), eq(60_000L), anyString()))
			.thenReturn(donePayment("pk_ok", checkout.orderNo(), 60_000));
		Outcome outcome = paymentService.confirm(customer.id(), "pk_ok", checkout.orderNo(), 60_000);

		assertThat(outcome.success()).isTrue();
		OrderDetail detail = orderService.myOrder(customer.id(), checkout.orderNo());
		assertThat(detail.status()).isEqualTo("PAID");
		assertThat(detail.items()).allMatch(i -> i.status().equals("PAID"));
		assertThat(detail.payment().easyPayProvider()).isEqualTo("네이버페이");
		assertThat(dsl.fetchCount(CART_ITEMS, CART_ITEMS.USER_ID.eq(customer.id()))).isZero();

		// 같은 요청이 다시 와도 성공으로 응답 (새로고침)
		assertThat(paymentService.confirm(customer.id(), "pk_ok", checkout.orderNo(), 60_000).success()).isTrue();
	}

	@Test
	void tamperedAmountIsRejectedWithoutCallingToss() {
		long skuId = createSku(30_000, 5);
		CheckoutInfo checkout = createOrder(skuId, 1);

		Outcome outcome = paymentService.confirm(customer.id(), "pk_x", checkout.orderNo(), 100);

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.code()).isEqualTo("AMOUNT_MISMATCH");
		verify(toss, never()).confirm(anyString(), anyString(), anyLong(), anyString());
		assertThat(orderStatus(checkout.orderNo())).isEqualTo("CANCELLED");
		assertThat(available(skuId)).isEqualTo(5); // 예약 해제
	}

	@Test
	void rejectedCardCancelsOrderAndReleasesStock() {
		long skuId = createSku(20_000, 2);
		CheckoutInfo checkout = createOrder(skuId, 2);
		when(toss.confirm(anyString(), anyString(), anyLong(), anyString()))
			.thenThrow(new TossApiException("REJECT_CARD_COMPANY", "카드사에서 거절했습니다.", false));

		Outcome outcome = paymentService.confirm(customer.id(), "pk_rejected", checkout.orderNo(), checkout.amount());

		assertThat(outcome.code()).isEqualTo("REJECT_CARD_COMPANY");
		assertThat(outcome.message()).isEqualTo("카드사에서 거절했습니다.");
		assertThat(orderStatus(checkout.orderNo())).isEqualTo("CANCELLED");
		assertThat(available(skuId)).isEqualTo(2);
	}

	@Test
	void networkErrorChecksTossBeforeDeciding() {
		long skuId = createSku(20_000, 2);
		CheckoutInfo checkout = createOrder(skuId, 1);
		when(toss.confirm(anyString(), anyString(), anyLong(), anyString()))
			.thenThrow(new TossApiException("NETWORK_ERROR", "통신 실패", true));
		when(toss.findByOrderId(checkout.orderNo()))
			.thenReturn(donePayment("pk_net", checkout.orderNo(), checkout.amount()));

		Outcome outcome = paymentService.confirm(customer.id(), "pk_net", checkout.orderNo(), checkout.amount());

		assertThat(outcome.success()).isTrue();
		assertThat(orderStatus(checkout.orderNo())).isEqualTo("PAID");
	}

	@Test
	void expiredPendingOrderIsCancelled() {
		long skuId = createSku(20_000, 3);
		CheckoutInfo checkout = createOrder(skuId, 3);
		assertThat(available(skuId)).isZero();
		when(toss.findByOrderId(checkout.orderNo()))
			.thenThrow(new TossApiException("NOT_FOUND_PAYMENT", "결제 없음", false));

		paymentService.expire(checkout.orderNo());

		assertThat(orderStatus(checkout.orderNo())).isEqualTo("CANCELLED");
		assertThat(available(skuId)).isEqualTo(3);
	}

	@Test
	void otherCustomerCannotConfirmOrView() {
		long skuId = createSku(20_000, 3);
		CheckoutInfo checkout = createOrder(skuId, 1);

		assertThatThrownBy(() -> paymentService.confirm(customer.id() + 10_000, "pk", checkout.orderNo(), checkout.amount()))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> orderService.myOrder(customer.id() + 10_000, checkout.orderNo()))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void customerCanCancelBeforeAcceptButNotAfter() {
		long skuId = createSku(20_000, 4);
		String cancelNo = paidOrder(skuId, 1);
		when(toss.cancel(anyString(), anyString(), any(), anyString()))
			.thenReturn(new TossPayment("pk_" + cancelNo, cancelNo, "CANCELED", "카드", null, 23_000, 0, null, "tx1", "{}"));

		paymentService.cancelByCustomer(customer.id(), cancelNo, "단순 변심");

		OrderDetail cancelled = orderService.myOrder(customer.id(), cancelNo);
		assertThat(cancelled.status()).isEqualTo("CANCELLED");
		assertThat(cancelled.payment().status()).isEqualTo("CANCELED");
		assertThat(available(skuId)).isEqualTo(4);

		String acceptedNo = paidOrder(skuId, 1);
		orderService.acceptOrder(SELLER, acceptedNo);
		assertThatThrownBy(() -> paymentService.cancelByCustomer(customer.id(), acceptedNo, "변심"))
			.isInstanceOf(ApiException.class)
			.extracting("code").isEqualTo("NOT_CANCELLABLE");
	}

	@Test
	void shippingDeductsStockAndRecordsMovement() {
		long skuId = createSku(20_000, 5);
		String orderNo = paidOrder(skuId, 2);
		orderService.acceptOrder(SELLER, orderNo);

		OrderDetail shipped = orderService.ship(customer.id(), SELLER, orderNo, 1L, "1234-5678-9012");

		assertThat(shipped.items()).allMatch(i -> i.status().equals("SHIPPED"));
		assertThat(shipped.shipments()).singleElement().satisfies(s -> {
			assertThat(s.carrierName()).isEqualTo("CJ대한통운");
			assertThat(s.trackingNo()).isEqualTo("123456789012");
		});
		var stock = dsl.selectFrom(INVENTORIES)
			.where(INVENTORIES.SKU_ID.eq(skuId), INVENTORIES.LOCATION_ID.eq(WAREHOUSE))
			.fetchOne();
		assertThat(stock.getOnHand()).isEqualTo(3);
		assertThat(stock.getReserved()).isZero();
		assertThat(dsl.fetchCount(INVENTORY_MOVEMENTS,
				INVENTORY_MOVEMENTS.SKU_ID.eq(skuId).and(INVENTORY_MOVEMENTS.TYPE.eq("SALE_OUT")))).isEqualTo(1);

		assertThatThrownBy(() -> orderService.ship(customer.id(), SELLER, orderNo, 1L, "999999999999"))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void receiverInfoIsEncryptedAtRest() {
		long skuId = createSku(20_000, 1);
		CheckoutInfo checkout = createOrder(skuId, 1);

		var row = dsl.selectFrom(ORDERS).where(ORDERS.ORDER_NO.eq(checkout.orderNo())).fetchOne();
		assertThat(row.getReceiverPhoneEnc()).startsWith("v1:").doesNotContain("1234");
		assertThat(row.getReceiverAddress1Enc()).doesNotContain("테헤란로");

		when(toss.confirm(anyString(), anyString(), anyLong(), anyString()))
			.thenReturn(donePayment("pk_enc", checkout.orderNo(), checkout.amount()));
		paymentService.confirm(customer.id(), "pk_enc", checkout.orderNo(), checkout.amount());
		OrderDetail detail = orderService.myOrder(customer.id(), checkout.orderNo());
		assertThat(detail.receiver().phone()).isEqualTo("010-1234-5678");
		assertThat(detail.receiver().address1()).isEqualTo("서울시 강남구 테헤란로 1");
	}

	// ------------------------------------------------------------------

	private long createSku(long price, int stock) {
		ProductFields fields = new ProductFields("주문테스트 " + UUID.randomUUID().toString().substring(0, 6), null, null,
				null, null, Map.of(), null, 3_000L, 50_000L);
		CreateSku sku = new CreateSku(null, Map.of(), ConditionGrade.RETURN_UNOPENED, null, null, price, null, stock);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields, List.of(sku)));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		return productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
	}

	private CheckoutInfo createOrder(long skuId, int qty) {
		return orderService.createOrder(customer, new CreateOrder(List.of(new OrderLineRequest(skuId, qty)), "홍길동",
				"01012345678", "06236", "서울시 강남구 테헤란로 1", "101호", "문 앞에 놓아주세요"));
	}

	private String paidOrder(long skuId, int qty) {
		CheckoutInfo checkout = createOrder(skuId, qty);
		String key = "pk_" + checkout.orderNo();
		when(toss.confirm(eq(key), eq(checkout.orderNo()), anyLong(), anyString()))
			.thenReturn(donePayment(key, checkout.orderNo(), checkout.amount()));
		assertThat(paymentService.confirm(customer.id(), key, checkout.orderNo(), checkout.amount()).success()).isTrue();
		return checkout.orderNo();
	}

	private int available(long skuId) {
		var stock = dsl.selectFrom(INVENTORIES).where(INVENTORIES.SKU_ID.eq(skuId)).fetchOne();
		return stock.getOnHand() - stock.getReserved();
	}

	private String orderStatus(String orderNo) {
		return dsl.select(ORDERS.STATUS).from(ORDERS).where(ORDERS.ORDER_NO.eq(orderNo)).fetchOne(ORDERS.STATUS);
	}

	private static TossPayment donePayment(String paymentKey, String orderId, long amount) {
		return new TossPayment(paymentKey, orderId, "DONE", "간편결제", "네이버페이", amount, amount,
				OffsetDateTime.now(), "tx", "{\"status\":\"DONE\"}");
	}

	private static PurchaseLine line(long sellerId, long price, long shippingFee, Long threshold, int qty) {
		return new PurchaseLine(1, 1, sellerId, "상품", Map.of(), ConditionGrade.NEW, price, null, shippingFee, threshold,
				null, 10, true, qty);
	}

}
