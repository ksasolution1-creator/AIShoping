package com.aishop.api.claim;

import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.INVENTORY_MOVEMENTS;
import static com.aishop.api.jooq.Tables.SKUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.catalog.ProductService;
import com.aishop.api.catalog.ProductStatus;
import com.aishop.api.claim.ClaimViews.ClaimView;
import com.aishop.api.claim.ClaimViews.CompleteReturn;
import com.aishop.api.claim.ClaimViews.ReturnReason;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.order.OrderService;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderDetail;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.payment.PaymentService;
import com.aishop.api.payment.TossPaymentsClient;
import com.aishop.api.payment.TossPaymentsClient.TossPayment;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ClaimServiceTests {

	private static final long SELLER = 1L;

	@MockitoBean
	TossPaymentsClient toss;

	@Autowired
	ClaimService claimService;

	@Autowired
	OrderService orderService;

	@Autowired
	PaymentService paymentService;

	@Autowired
	ProductService productService;

	@Autowired
	UserRepository users;

	@Autowired
	DSLContext dsl;

	AuthUser customer;

	@BeforeEach
	void setUp() {
		when(toss.isConfigured()).thenReturn(true);
		when(toss.clientKey()).thenReturn("test_ck");
		String email = "claim-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		customer = new AuthUser(users.insert(email, null, "반품고객", null, "CUSTOMER"), email, "반품고객", "CUSTOMER",
				Set.of(), null);
	}

	@Test
	void cancelOneItemRefundsOnlyThatItem() {
		long skuA = createSku(30_000, 5);
		long skuB = createSku(40_000, 5);
		Paid paid = paidOrder(List.of(new OrderLineRequest(skuA, 1), new OrderLineRequest(skuB, 1)));
		OrderDetail detail = orderService.myOrder(customer.id(), paid.orderNo);
		long itemA = detail.items().stream().filter(i -> i.skuId() == skuA).findFirst().orElseThrow().id();
		mockCancel(paid, 40_000);

		ClaimView claim = claimService.cancelItemByCustomer(customer.id(), paid.orderNo, itemA, null);

		assertThat(claim.refundAmount()).isEqualTo(30_000);
		verify(toss).cancel(eq(paid.paymentKey), anyString(), eq(30_000L), eq("claim-" + claim.id()));
		OrderDetail after = orderService.myOrder(customer.id(), paid.orderNo);
		assertThat(after.status()).isEqualTo("PAID");
		assertThat(after.items()).extracting(i -> i.status()).containsExactlyInAnyOrder("CANCELLED", "PAID");
		assertThat(available(skuA)).isEqualTo(5); // 예약 해제
		assertThat(after.claims()).singleElement().extracting(ClaimView::type).isEqualTo("CANCEL");
	}

	@Test
	void cancellingLastItemRefundsRemainingBalanceIncludingShipping() {
		long sku = createSku(20_000, 3); // 5만원 미만 → 배송비 3,000원
		Paid paid = paidOrder(List.of(new OrderLineRequest(sku, 1)));
		long itemId = orderService.myOrder(customer.id(), paid.orderNo).items().getFirst().id();
		mockCancel(paid, 0);

		ClaimView claim = claimService.cancelItemByCustomer(customer.id(), paid.orderNo, itemId, "변심");

		assertThat(claim.refundAmount()).isEqualTo(23_000);
		assertThat(orderService.myOrder(customer.id(), paid.orderNo).status()).isEqualTo("CANCELLED");
	}

	@Test
	void changeOfMindReturnChargesRoundTripShippingAndRestocksWithNewGrade() {
		long sku = createSku(60_000, 2); // 무료배송 주문 → 단순변심 반품비는 왕복 6,000원
		Paid paid = paidOrder(List.of(new OrderLineRequest(sku, 1)));
		ship(paid.orderNo);
		long itemId = orderService.myOrder(customer.id(), paid.orderNo).items().getFirst().id();

		ClaimView requested = claimService.requestReturn(customer.id(), paid.orderNo, itemId,
				ReturnReason.CHANGE_OF_MIND, "생각보다 커요");
		assertThat(requested.fault()).isEqualTo("CUSTOMER");
		assertThat(requested.returnShippingFee()).isEqualTo(6_000);

		claimService.approve(1L, SELLER, requested.id(), 1L, "555566667777");
		mockCancel(paid, 6_000);
		ClaimView done = claimService.completeReturn(1L, SELLER, requested.id(),
				new CompleteReturn("RETURN_OPENED", "박스 개봉, 사용감 없음", null));

		assertThat(done.status()).isEqualTo("COMPLETED");
		assertThat(done.refundAmount()).isEqualTo(54_000);
		assertThat(done.inspectionGrade()).isEqualTo("RETURN_OPENED");

		// '반품 개봉' 등급 SKU가 새로 생기고 판매중지 상태로 재입고된다
		var restocked = dsl.selectFrom(SKUS)
			.where(SKUS.PRODUCT_ID.eq(productOf(sku)), SKUS.CONDITION_GRADE.eq("RETURN_OPENED"))
			.fetchOne();
		assertThat(restocked).isNotNull();
		assertThat(restocked.getStatus()).isEqualTo("INACTIVE");
		assertThat(dsl.select(INVENTORIES.ON_HAND).from(INVENTORIES).where(INVENTORIES.SKU_ID.eq(restocked.getId()))
			.fetchOne(INVENTORIES.ON_HAND)).isEqualTo(1);
		assertThat(dsl.fetchCount(INVENTORY_MOVEMENTS, INVENTORY_MOVEMENTS.SKU_ID.eq(restocked.getId())
			.and(INVENTORY_MOVEMENTS.TYPE.eq("RETURN_IN")))).isEqualTo(1);
		assertThat(orderService.myOrder(customer.id(), paid.orderNo).items().getFirst().status()).isEqualTo("RETURNED");
	}

	@Test
	void defectReturnIsFreeAndDisposedItemIsNotRestocked() {
		long sku = createSku(20_000, 2);
		Paid paid = paidOrder(List.of(new OrderLineRequest(sku, 1))); // 23,000원 (배송비 포함)
		ship(paid.orderNo);
		long itemId = orderService.myOrder(customer.id(), paid.orderNo).items().getFirst().id();

		ClaimView requested = claimService.requestReturn(customer.id(), paid.orderNo, itemId, ReturnReason.DEFECTIVE, "전원 안 켜짐");
		assertThat(requested.returnShippingFee()).isZero();
		mockCancel(paid, 0);

		ClaimView done = claimService.completeReturn(1L, SELLER, requested.id(), new CompleteReturn("DISPOSE", "불량 확인", null));

		// 판매자 귀책 + 마지막 상품 → 배송비까지 전액 환불
		assertThat(done.refundAmount()).isEqualTo(23_000);
		assertThat(available(sku)).isEqualTo(1); // 폐기라서 재입고 없음
	}

	@Test
	void rejectRestoresItemStatusAndWithdrawWorksOnlyBeforeApproval() {
		long sku = createSku(20_000, 2);
		Paid paid = paidOrder(List.of(new OrderLineRequest(sku, 1)));
		ship(paid.orderNo);
		long itemId = orderService.myOrder(customer.id(), paid.orderNo).items().getFirst().id();

		ClaimView first = claimService.requestReturn(customer.id(), paid.orderNo, itemId, ReturnReason.CHANGE_OF_MIND, null);
		claimService.withdraw(customer.id(), first.id());
		assertThat(orderService.myOrder(customer.id(), paid.orderNo).items().getFirst().status()).isEqualTo("SHIPPED");

		ClaimView second = claimService.requestReturn(customer.id(), paid.orderNo, itemId, ReturnReason.CHANGE_OF_MIND, null);
		claimService.approve(1L, SELLER, second.id(), null, null);
		assertThatThrownBy(() -> claimService.withdraw(customer.id(), second.id()))
			.isInstanceOf(ApiException.class).extracting("code").isEqualTo("CANNOT_WITHDRAW");

		ClaimView rejected = claimService.reject(1L, SELLER, second.id(), "사용 흔적이 있어 반품 불가");
		assertThat(rejected.status()).isEqualTo("REJECTED");
		assertThat(orderService.myOrder(customer.id(), paid.orderNo).items().getFirst().status()).isEqualTo("SHIPPED");
	}

	@Test
	void cannotReturnBeforeShippingOrCancelAfterAccept() {
		long sku = createSku(20_000, 2);
		Paid paid = paidOrder(List.of(new OrderLineRequest(sku, 1)));
		long itemId = orderService.myOrder(customer.id(), paid.orderNo).items().getFirst().id();

		assertThatThrownBy(() -> claimService.requestReturn(customer.id(), paid.orderNo, itemId, ReturnReason.DEFECTIVE, null))
			.isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_RETURNABLE");

		orderService.acceptOrder(SELLER, paid.orderNo);
		assertThatThrownBy(() -> claimService.cancelItemByCustomer(customer.id(), paid.orderNo, itemId, null))
			.isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_CANCELLABLE");
	}

	// ------------------------------------------------------------------

	private record Paid(String orderNo, String paymentKey, long amount) {
	}

	private long createSku(long price, int stock) {
		ProductFields fields = new ProductFields("반품테스트 " + UUID.randomUUID().toString().substring(0, 6), null, null,
				null, null, Map.of(), null, 3_000L, 50_000L);
		CreateSku sku = new CreateSku(null, Map.of("색상", "블루"), ConditionGrade.RETURN_UNOPENED, null, null, price, null, stock);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields, List.of(sku)));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		return productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
	}

	private Paid paidOrder(List<OrderLineRequest> lines) {
		CheckoutInfo checkout = orderService.createOrder(customer,
				new CreateOrder(lines, "홍길동", "010-1111-2222", "06236", "서울시 강남구 테헤란로 1", null, null));
		String key = "pk_" + checkout.orderNo();
		when(toss.confirm(eq(key), eq(checkout.orderNo()), anyLong(), anyString()))
			.thenReturn(new TossPayment(key, checkout.orderNo(), "DONE", "카드", null, checkout.amount(), checkout.amount(),
					OffsetDateTime.now(), "tx", "{}"));
		assertThat(paymentService.confirm(customer.id(), key, checkout.orderNo(), checkout.amount()).success()).isTrue();
		return new Paid(checkout.orderNo(), key, checkout.amount());
	}

	private void ship(String orderNo) {
		orderService.ship(1L, SELLER, orderNo, 1L, String.valueOf(100_000_000_000L + (long) (Math.random() * 1_000_000_000)));
	}

	/** 토스 부분취소 응답: 취소 후 남은 금액을 balance로 돌려준다 */
	private void mockCancel(Paid paid, long balanceAfter) {
		when(toss.cancel(eq(paid.paymentKey), anyString(), anyLong(), anyString()))
			.thenReturn(new TossPayment(paid.paymentKey, paid.orderNo, balanceAfter == 0 ? "CANCELED" : "PARTIAL_CANCELED",
					"카드", null, paid.amount, balanceAfter, null, "tx-cancel", "{}"));
	}

	private int available(long skuId) {
		var stock = dsl.selectFrom(INVENTORIES).where(INVENTORIES.SKU_ID.eq(skuId)).fetchOne();
		return stock.getOnHand() - stock.getReserved();
	}

	private long productOf(long skuId) {
		return dsl.select(SKUS.PRODUCT_ID).from(SKUS).where(SKUS.ID.eq(skuId)).fetchOne(SKUS.PRODUCT_ID);
	}

}
