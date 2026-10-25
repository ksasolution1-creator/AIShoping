package com.aishop.api.order;

import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.ORDERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.aishop.api.claim.ClaimService;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderDetail;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.payment.PaymentService;
import com.aishop.api.payment.TossPaymentsClient;

/** 무통장입금: 주문 → 입금대기 → 운영자 입금 확인 → 결제완료, 환불은 송금 대기로 남는다 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BankTransferOrderTests {

	private static final long SELLER = 1L;

	@MockitoBean
	TossPaymentsClient toss;

	@Autowired
	OrderService orderService;

	@Autowired
	PaymentService paymentService;

	@Autowired
	ClaimService claimService;

	@Autowired
	ProductService productService;

	@Autowired
	UserRepository users;

	@Autowired
	DSLContext dsl;

	AuthUser customer;

	@BeforeEach
	void setUp() {
		String email = "bank-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		long userId = users.insert(email, null, "입금자", null, "CUSTOMER");
		customer = new AuthUser(userId, email, "입금자", "CUSTOMER", Set.of(), null);
	}

	@Test
	void depositConfirmationMarksPaid() {
		long skuId = createSku(20_000, 3);
		CheckoutInfo checkout = bankOrder(skuId, 2, "김입금");
		assertThat(checkout.payMethod()).isEqualTo("BANK_TRANSFER");
		assertThat(available(skuId)).isEqualTo(1); // 입금 기한까지 재고를 잡아 둔다

		// 입금 전에도 고객 주문 내역과 운영자 주문 목록에 보인다
		OrderDetail waiting = orderService.myOrder(customer.id(), checkout.orderNo());
		assertThat(waiting.status()).isEqualTo("PENDING_PAYMENT");
		assertThat(waiting.bankDeposit().accountNo()).isEqualTo("478-083872-04-017");
		assertThat(waiting.bankDeposit().depositorName()).isEqualTo("김입금");
		assertThat(waiting.bankDeposit().dueAt()).isAfter(OffsetDateTime.now().plusHours(47));
		assertThat(waiting.cancellable()).isTrue();
		assertThat(orderService.myOrders(customer.id(), 0, 20).items())
			.anyMatch(o -> o.orderNo().equals(checkout.orderNo()));
		assertThat(orderService.sellerOrders(SELLER, "PENDING_PAYMENT", checkout.orderNo(), 0, 20).items())
			.singleElement();

		paymentService.confirmDeposit(SELLER, checkout.orderNo());

		OrderDetail paid = orderService.myOrder(customer.id(), checkout.orderNo());
		assertThat(paid.status()).isEqualTo("PAID");
		assertThat(paid.items()).allMatch(i -> i.status().equals("PAID"));
		assertThat(paid.payment().method()).isEqualTo("무통장입금");
		assertThat(paid.paidAt()).isNotNull();
		assertThatThrownBy(() -> paymentService.confirmDeposit(SELLER, checkout.orderNo()))
			.isInstanceOf(ApiException.class)
			.extracting("code").isEqualTo("NOT_AWAITING_DEPOSIT");
	}

	@Test
	void cancelBeforeDepositReleasesStockWithoutRefund() {
		long skuId = createSku(20_000, 2);
		CheckoutInfo checkout = bankOrder(skuId, 2, null);

		paymentService.cancelByCustomer(customer.id(), checkout.orderNo(), "단순 변심");

		OrderDetail cancelled = orderService.myOrder(customer.id(), checkout.orderNo());
		assertThat(cancelled.status()).isEqualTo("CANCELLED");
		assertThat(cancelled.refunds()).isEmpty();
		assertThat(cancelled.bankDeposit().depositorName()).isEqualTo("입금자"); // 비우면 주문자 이름
		assertThat(available(skuId)).isEqualTo(2);
	}

	@Test
	void unpaidOrderExpiresAfterDueTime() {
		long skuId = createSku(20_000, 1);
		CheckoutInfo checkout = bankOrder(skuId, 1, null);
		dsl.update(ORDERS)
			.set(ORDERS.DEPOSIT_DUE_AT, OffsetDateTime.now().minusMinutes(1))
			.where(ORDERS.ORDER_NO.eq(checkout.orderNo()))
			.execute();

		paymentService.expire(checkout.orderNo());

		assertThat(orderStatus(checkout.orderNo())).isEqualTo("CANCELLED");
		assertThat(available(skuId)).isEqualTo(1);
		verify(toss, never()).findByOrderId(anyString());
	}

	@Test
	void refundAfterDepositWaitsForManualTransfer() {
		long skuId = createSku(20_000, 2);
		CheckoutInfo checkout = bankOrder(skuId, 1, null);
		paymentService.confirmDeposit(SELLER, checkout.orderNo());

		paymentService.cancelByCustomer(customer.id(), checkout.orderNo(), "단순 변심");

		OrderDetail cancelled = orderService.myOrder(customer.id(), checkout.orderNo());
		assertThat(cancelled.status()).isEqualTo("CANCELLED");
		assertThat(cancelled.payment().status()).isEqualTo("CANCELED");
		assertThat(cancelled.refunds()).singleElement().satisfies(r -> {
			assertThat(r.amount()).isEqualTo(checkout.amount());
			assertThat(r.status()).isEqualTo("REQUESTED");
		});
		verify(toss, never()).cancel(any(), any(), any(), any());

		long refundId = cancelled.refunds().getFirst().id();
		paymentService.completeManualRefund(SELLER, checkout.orderNo(), refundId);
		OrderDetail refunded = orderService.myOrder(customer.id(), checkout.orderNo());
		assertThat(refunded.refunds().getFirst().status()).isEqualTo("DONE");
		assertThat(refunded.refunds().getFirst().completedAt()).isNotNull();
		assertThatThrownBy(() -> paymentService.completeManualRefund(SELLER, checkout.orderNo(), refundId))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void partialItemCancelLeavesBalance() {
		long first = createSku(20_000, 2);
		long second = createSku(15_000, 2);
		CheckoutInfo checkout = orderService.createOrder(customer, new CreateOrder(
				List.of(new OrderLineRequest(first, 1), new OrderLineRequest(second, 1)), "홍길동", "01012345678",
				"06236", "서울시 강남구 테헤란로 1", null, null, "BANK_TRANSFER", null));
		paymentService.confirmDeposit(SELLER, checkout.orderNo());
		OrderDetail paid = orderService.myOrder(customer.id(), checkout.orderNo());
		long secondItemId = paid.items().stream().filter(i -> i.skuId() == second).findFirst().orElseThrow().id();

		claimService.cancelItemByCustomer(customer.id(), checkout.orderNo(), secondItemId, "변심");

		OrderDetail after = orderService.myOrder(customer.id(), checkout.orderNo());
		assertThat(after.payment().status()).isEqualTo("PARTIAL_CANCELED");
		assertThat(after.payment().balanceAmount()).isEqualTo(checkout.amount() - 15_000);
		assertThat(after.refunds()).singleElement().satisfies(r -> assertThat(r.status()).isEqualTo("REQUESTED"));
	}

	@Test
	void bankOrderCannotBeConfirmedAsCard() {
		long skuId = createSku(20_000, 1);
		CheckoutInfo checkout = bankOrder(skuId, 1, null);

		var outcome = paymentService.confirm(customer.id(), "pk_any", checkout.orderNo(), checkout.amount());

		assertThat(outcome.success()).isFalse();
		assertThat(orderStatus(checkout.orderNo())).isEqualTo("PENDING_PAYMENT");
		paymentService.abandon(customer.id(), checkout.orderNo()); // 결제창 이탈 처리도 무통장입금 주문은 건드리지 않는다
		assertThat(orderStatus(checkout.orderNo())).isEqualTo("PENDING_PAYMENT");
	}

	// ------------------------------------------------------------------

	private CheckoutInfo bankOrder(long skuId, int qty, String depositor) {
		return orderService.createOrder(customer, new CreateOrder(List.of(new OrderLineRequest(skuId, qty)), "홍길동",
				"01012345678", "06236", "서울시 강남구 테헤란로 1", "101호", null, "BANK_TRANSFER", depositor));
	}

	private long createSku(long price, int stock) {
		ProductFields fields = new ProductFields("입금테스트 " + UUID.randomUUID().toString().substring(0, 6), null, null,
				null, null, Map.of(), null, 3_000L, 50_000L);
		CreateSku sku = new CreateSku(null, Map.of(), ConditionGrade.RETURN_UNOPENED, null, null, price, null, stock);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields, List.of(sku)));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		return productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
	}

	private int available(long skuId) {
		var stock = dsl.selectFrom(INVENTORIES).where(INVENTORIES.SKU_ID.eq(skuId)).fetchOne();
		return stock.getOnHand() - stock.getReserved();
	}

	private String orderStatus(String orderNo) {
		return dsl.select(ORDERS.STATUS).from(ORDERS).where(ORDERS.ORDER_NO.eq(orderNo)).fetchOne(ORDERS.STATUS);
	}

}
