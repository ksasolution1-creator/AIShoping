package com.aishop.api.notification;

import static com.aishop.api.jooq.Tables.ORDERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
import com.aishop.api.order.OrderRepository;
import com.aishop.api.order.OrderService;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.payment.PaymentService;
import com.aishop.api.payment.TossPaymentsClient;

/** 무통장입금 이메일 알림: 새 주문(운영자·고객), 입금 확인(고객), 입금 기한 임박(운영자) */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.mail.host=localhost", "aishop.notify.admin-email=admin@example.com",
		"aishop.notify.deposit-remind-check-ms=3600000" })
class DepositNotificationTests {

	private static final long SELLER = 1L;

	@MockitoBean
	JavaMailSender mailSender;

	@MockitoBean
	TossPaymentsClient toss;

	@Autowired
	OrderService orderService;

	@Autowired
	PaymentService paymentService;

	@Autowired
	ProductService productService;

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	OrderNotifier notifier;

	@Autowired
	UserRepository users;

	@Autowired
	DSLContext dsl;

	AuthUser customer;

	@BeforeEach
	void setUp() {
		String email = "notify-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		long userId = users.insert(email, null, "알림고객", null, "CUSTOMER");
		customer = new AuthUser(userId, email, "알림고객", "CUSTOMER", Set.of(), null);
	}

	@Test
	void bankOrderMailsAdminAndCustomer() {
		CheckoutInfo checkout = bankOrder("김입금");

		List<SimpleMailMessage> sent = captureMails(2);
		SimpleMailMessage admin = byRecipient(sent, "admin@example.com");
		assertThat(admin.getSubject()).contains("입금대기", "김입금");
		assertThat(admin.getText()).contains(checkout.orderNo(), "알림테스트");

		SimpleMailMessage toCustomer = byRecipient(sent, customer.email());
		assertThat(toCustomer.getText()).contains("478-083872-04-017", "김입금", "자동으로 취소");
	}

	@Test
	void depositConfirmationMailsCustomer() {
		CheckoutInfo checkout = bankOrder(null);
		captureMails(2);
		clearInvocations(mailSender);

		paymentService.confirmDeposit(SELLER, checkout.orderNo());

		SimpleMailMessage mail = captureMails(1).getFirst();
		assertThat(mail.getTo()).containsExactly(customer.email());
		assertThat(mail.getSubject()).contains("입금이 확인");
	}

	@Test
	void cardOrderSendsNoMail() {
		long skuId = createSku();
		orderService.createOrder(customer, new CreateOrder(List.of(new OrderLineRequest(skuId, 1)), "홍길동",
				"01012345678", "06236", "서울시 강남구 테헤란로 1", null, null));

		verify(mailSender, after(500).never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
	}

	@Test
	void dueSoonOrdersAreRemindedOnce() {
		CheckoutInfo soon = bankOrder(null);
		CheckoutInfo later = bankOrder(null);
		dsl.update(ORDERS)
			.set(ORDERS.DEPOSIT_DUE_AT, OffsetDateTime.now().plusHours(2))
			.where(ORDERS.ORDER_NO.eq(soon.orderNo()))
			.execute();

		var claimed = orderRepository.claimDepositReminders(Duration.ofHours(6), 100);
		assertThat(claimed).extracting(o -> o.getOrderNo()).contains(soon.orderNo()).doesNotContain(later.orderNo());
		assertThat(orderRepository.claimDepositReminders(Duration.ofHours(6), 100))
			.extracting(o -> o.getOrderNo())
			.doesNotContain(soon.orderNo()); // 두 번 알리지 않는다

		captureMails(4);
		clearInvocations(mailSender);
		notifier.remindDepositsDue(claimed);
		SimpleMailMessage mail = captureMails(1).getFirst();
		assertThat(mail.getTo()).containsExactly("admin@example.com");
		assertThat(mail.getText()).contains(soon.orderNo());
	}

	// ------------------------------------------------------------------

	private List<SimpleMailMessage> captureMails(int count) {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, timeout(5_000).times(count)).send(captor.capture());
		return captor.getAllValues();
	}

	private static SimpleMailMessage byRecipient(List<SimpleMailMessage> mails, String to) {
		return mails.stream().filter(m -> List.of(m.getTo()).contains(to)).findFirst().orElseThrow();
	}

	private CheckoutInfo bankOrder(String depositor) {
		return orderService.createOrder(customer, new CreateOrder(List.of(new OrderLineRequest(createSku(), 1)),
				"홍길동", "01012345678", "06236", "서울시 강남구 테헤란로 1", null, null, "BANK_TRANSFER", depositor));
	}

	private long createSku() {
		ProductFields fields = new ProductFields("알림테스트 " + UUID.randomUUID().toString().substring(0, 6), null, null,
				null, null, Map.of(), null, 3_000L, 50_000L);
		CreateSku sku = new CreateSku(null, Map.of(), ConditionGrade.RETURN_UNOPENED, null, null, 20_000L, null, 5);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields, List.of(sku)));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		return productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
	}

}
