package com.aishop.api.community;

import static com.aishop.api.jooq.Tables.ORDERS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.aishop.api.auth.AuthService;
import com.aishop.api.auth.AuthUser;
import com.aishop.api.auth.UserRepository;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.catalog.ProductService;
import com.aishop.api.catalog.ProductStatus;
import com.aishop.api.claim.ClaimService;
import com.aishop.api.claim.ClaimViews.ReturnReason;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.order.OrderService;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.order.PurchaseAutoConfirmJob;
import com.aishop.api.payment.PaymentService;
import com.aishop.api.payment.TossPaymentsClient;
import com.aishop.api.payment.TossPaymentsClient.TossPayment;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CommunityTests {

	private static final long SELLER = 1L;

	@MockitoBean
	TossPaymentsClient toss;

	@Autowired
	CommunityService community;

	@Autowired
	OrderService orderService;

	@Autowired
	PaymentService paymentService;

	@Autowired
	ProductService productService;

	@Autowired
	ClaimService claimService;

	@Autowired
	PurchaseAutoConfirmJob autoConfirm;

	@Autowired
	AuthService authService;

	@Autowired
	UserRepository users;

	@Autowired
	DSLContext dsl;

	AuthUser customer;
	long staffId;

	@BeforeEach
	void setUp() {
		when(toss.isConfigured()).thenReturn(true);
		when(toss.clientKey()).thenReturn("test_ck_dummy");
		customer = newCustomer("김민혁");
		staffId = authService.createStaff("cs-" + uniq() + "@example.test", "staffpass1234", "상담", Set.of("OPERATOR"),
				SELLER);
	}

	@Test
	void reviewNeedsConfirmedPurchaseAndOnlyOnce() {
		long productId = product();
		String orderNo = shippedOrder(productId);
		long itemId = itemId(orderNo);

		assertThatThrownBy(() -> community.writeReview(customer.id(), orderNo, itemId, 5, "아주 좋아요 재구매 의사 있어요"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("구매확정");

		var detail = orderService.confirmPurchase(customer.id(), orderNo, itemId);
		assertThat(detail.items().getFirst().status()).isEqualTo("CONFIRMED");
		// 구매확정 후에는 온라인 반품 신청을 받지 않는다
		assertThatThrownBy(() -> claimService.requestReturn(customer.id(), orderNo, itemId, ReturnReason.CHANGE_OF_MIND, null))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("고객센터");

		community.writeReview(customer.id(), orderNo, itemId, 4, "포장 상태 좋고 실물도 사진과 같아요");
		assertThat(orderService.myOrder(customer.id(), orderNo).items().getFirst().reviewed()).isTrue();
		assertThatThrownBy(() -> community.writeReview(customer.id(), orderNo, itemId, 5, "한 번 더 써 봅니다 열 자 이상"))
			.isInstanceOf(ApiException.class);

		var reviews = community.productReviews(productId, 0);
		assertThat(reviews.summary().count()).isEqualTo(1);
		assertThat(reviews.summary().average()).isEqualTo(4.0);
		assertThat(reviews.page().items().getFirst().author()).isEqualTo("김*혁");

		// 숨긴 후기는 쇼핑몰에 나오지 않는다
		long reviewId = reviews.page().items().getFirst().id();
		community.setReviewHidden(SELLER, reviewId, true);
		assertThat(community.productReviews(productId, 0).summary().count()).isZero();
	}

	@Test
	void otherCustomerCannotReviewMyOrder() {
		long productId = product();
		String orderNo = shippedOrder(productId);
		long itemId = itemId(orderNo);
		orderService.confirmPurchase(customer.id(), orderNo, itemId);

		AuthUser other = newCustomer("남궁");
		assertThatThrownBy(() -> community.writeReview(other.id(), orderNo, itemId, 1, "남의 주문에 후기 쓰기 시도"))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> orderService.confirmPurchase(other.id(), orderNo, itemId))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void autoConfirmAfterEightDaysFromDelivery() {
		long productId = product();
		String orderNo = shippedOrder(productId);
		long itemId = itemId(orderNo);
		dsl.update(ORDER_ITEMS)
			.set(ORDER_ITEMS.STATUS, "DELIVERED")
			.set(ORDER_ITEMS.DELIVERED_AT, OffsetDateTime.now().minusDays(9))
			.where(ORDER_ITEMS.ID.eq(itemId))
			.execute();

		autoConfirm.run();

		assertThat(dsl.select(ORDER_ITEMS.STATUS).from(ORDER_ITEMS).where(ORDER_ITEMS.ID.eq(itemId))
			.fetchOne(ORDER_ITEMS.STATUS)).isEqualTo("CONFIRMED");
	}

	@Test
	void secretQuestionIsVisibleOnlyToAuthorAndAnsweredBySeller() {
		long productId = product();
		community.ask(customer.id(), productId, "대량 구매하면 더 할인되나요?", true);
		community.ask(customer.id(), productId, "재입고 예정 있나요?", false);

		AuthUser other = newCustomer("박영희");
		var forOther = community.questions(productId, other.id(), 0).items();
		assertThat(forOther).hasSize(2);
		var secret = forOther.stream().filter(q -> q.secret()).findFirst().orElseThrow();
		assertThat(secret.question()).isNull();
		assertThat(community.questions(productId, null, 0).items()).filteredOn(q -> !q.secret())
			.extracting(q -> q.question()).containsExactly("재입고 예정 있나요?");
		assertThat(community.questions(productId, customer.id(), 0).items()).allMatch(q -> q.question() != null);

		var pending = community.sellerQuestions(SELLER, true, 0).items().stream()
			.filter(q -> q.productId() == productId)
			.toList();
		assertThat(pending).hasSize(2);
		community.answer(SELLER, staffId, pending.getFirst().id(), "100개 이상은 5% 더 할인됩니다.");
		assertThat(community.sellerQuestions(SELLER, true, 0).items()).noneMatch(q -> q.id() == pending.getFirst().id());
		assertThatThrownBy(() -> community.answer(999L, staffId, pending.get(1).id(), "다른 판매자 답변"))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void wishlistAddRemove() {
		long productId = product();

		community.wish(customer.id(), productId);
		community.wish(customer.id(), productId);
		assertThat(community.isWished(customer.id(), productId)).isTrue();
		assertThat(community.wishes(customer.id())).singleElement().satisfies(w -> {
			assertThat(w.productId()).isEqualTo(productId);
			assertThat(w.minPrice()).isEqualTo(10_000L);
		});

		community.unwish(customer.id(), productId);
		assertThat(community.wishes(customer.id())).isEmpty();
	}

	@Test
	void namesAreMasked() {
		assertThat(CommunityViews.mask("홍길동")).isEqualTo("홍*동");
		assertThat(CommunityViews.mask("남궁민수")).isEqualTo("남**수");
		assertThat(CommunityViews.mask("이선")).isEqualTo("이*");
		assertThat(CommunityViews.mask(null)).isEqualTo("구매자");
	}

	// ------------------------------------------------------------------

	private AuthUser newCustomer(String name) {
		String email = "cm-" + uniq() + "@example.com";
		return new AuthUser(users.insert(email, null, name, null, "CUSTOMER"), email, name, "CUSTOMER", Set.of(), null);
	}

	private long product() {
		ProductFields fields = new ProductFields("후기테스트 " + uniq(), null, null, null, null, Map.of(), null, 3_000L, null);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields,
				List.of(new CreateSku(null, Map.of(), ConditionGrade.NEW, null, null, 10_000L, null, 10))));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		return productId;
	}

	private String shippedOrder(long productId) {
		long skuId = productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
		CheckoutInfo checkout = orderService.createOrder(customer, new CreateOrder(List.of(new OrderLineRequest(skuId, 1)),
				"김민혁", "01012345678", "06236", "서울시 강남구 테헤란로 1", "101호", null));
		String key = "pk_" + checkout.orderNo();
		when(toss.confirm(eq(key), eq(checkout.orderNo()), anyLong(), anyString()))
			.thenReturn(new TossPayment(key, checkout.orderNo(), "DONE", "카드", null, checkout.amount(), checkout.amount(),
					OffsetDateTime.now(), "tx", "{}"));
		paymentService.confirm(customer.id(), key, checkout.orderNo(), checkout.amount());
		long carrierId = orderService.carriers().getFirst().id();
		orderService.ship(staffId, SELLER, checkout.orderNo(), carrierId, String.valueOf(System.nanoTime()));
		return checkout.orderNo();
	}

	private long itemId(String orderNo) {
		return dsl.select(ORDER_ITEMS.ID)
			.from(ORDER_ITEMS)
			.join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDER_ID))
			.where(ORDERS.ORDER_NO.eq(orderNo))
			.fetchOne(ORDER_ITEMS.ID);
	}

	private static String uniq() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
