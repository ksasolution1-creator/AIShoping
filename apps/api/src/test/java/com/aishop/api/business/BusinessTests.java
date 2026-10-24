package com.aishop.api.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.aishop.api.TestcontainersConfiguration;
import com.aishop.api.auth.AuthService;
import com.aishop.api.auth.UserRepository;
import com.aishop.api.business.BusinessViews.ApplyForm;
import com.aishop.api.business.BusinessViews.ProductBusinessTerms;
import com.aishop.api.business.BusinessViews.SkuBusinessPrice;
import com.aishop.api.cart.CartService;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.catalog.ProductService;
import com.aishop.api.catalog.ProductStatus;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.order.OrderService;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.payment.TossPaymentsClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BusinessTests {

	private static final long SELLER = 1L;
	// 검증번호가 맞는 사업자등록번호 (형식 시험용)
	private static final String VALID_NO = "124-81-00998";

	@MockitoBean
	TossPaymentsClient toss;

	@Autowired
	BusinessService business;

	@Autowired
	ProductService productService;

	@Autowired
	CartService cartService;

	@Autowired
	OrderService orderService;

	@Autowired
	AuthService authService;

	@Autowired
	UserRepository users;

	long staffId;

	@BeforeEach
	void setUp() {
		staffId = authService.createStaff("biz-" + uniq() + "@example.test", "staffpass1234", "심사", Set.of("ADMIN"),
				SELLER);
	}

	@Test
	void businessNumberChecksum() {
		assertThat(BusinessViews.validBusinessNo(BusinessViews.normalizeBusinessNo(VALID_NO))).isTrue();
		assertThat(BusinessViews.validBusinessNo("1248100999")).isFalse();
		assertThat(BusinessViews.validBusinessNo("12481009")).isFalse();
		assertThat(BusinessViews.validBusinessNo(null)).isFalse();
	}

	@Test
	void applyApproveAndBlockDuplicateNumber() {
		long a = customer();
		long b = customer();

		assertThatThrownBy(() -> business.apply(a, true, form("123-45-67890")))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("올바르지");
		assertThatThrownBy(() -> business.apply(staffId, false, form(VALID_NO))).isInstanceOf(ApiException.class);

		assertThat(business.apply(a, true, form(VALID_NO)).status()).isEqualTo("PENDING");
		business.apply(b, true, form(VALID_NO));

		business.review(a, staffId, true, null);
		assertThat(business.profile(a).status()).isEqualTo("APPROVED");
		// 같은 번호로 다른 회원을 또 승인할 수 없다
		assertThatThrownBy(() -> business.review(b, staffId, true, null)).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> business.review(b, staffId, false, " ")).isInstanceOf(ApiException.class);
		business.review(b, staffId, false, "사업자등록증 상호와 다릅니다.");
		assertThat(business.profile(b).rejectReason()).isEqualTo("사업자등록증 상호와 다릅니다.");
		assertThat(business.profile(customer()).status()).isEqualTo("NONE");
	}

	@Test
	void businessOnlyProductAndBusinessPrice() {
		long productId = product();
		long skuId = productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
		business.updateProductTerms(productId,
				new ProductBusinessTerms(true, true, List.of(new SkuBusinessPrice(skuId, 7_000L))));

		var detail = productService.getShopProduct(productId);
		assertThat(detail.businessOnly()).isTrue();
		assertThat(detail.imageReuseAllowed()).isTrue();
		assertThat(detail.skus().getFirst().businessPrice()).isEqualTo(7_000L);

		long general = customer();
		assertThatThrownBy(() -> cartService.add(general, skuId, 1))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("사업자 회원 전용");
		var preview = orderService.preview(general, List.of(new OrderLineRequest(skuId, 1)));
		assertThat(preview.allPurchasable()).isFalse();
		assertThat(preview.notices()).anyMatch(n -> n.contains("사업자 회원 전용"));

		long biz = customer();
		business.apply(biz, true, new ApplyForm(otherValidNo(), "떨고래상사", "김대표", "도소매"));
		business.review(biz, staffId, true, null);
		var cart = cartService.add(biz, skuId, 2);
		assertThat(cart.items().getFirst().price()).isEqualTo(7_000L);
		assertThat(cart.itemsTotal()).isEqualTo(14_000L);
	}

	// ------------------------------------------------------------------

	private long customer() {
		return users.insert("bz-" + uniq() + "@example.com", null, "고객", null, "CUSTOMER");
	}

	private long product() {
		ProductFields fields = new ProductFields("사업자상품 " + uniq(), null, null, null, null, Map.of(), null, 3_000L, null);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields,
				List.of(new CreateSku(null, Map.of(), ConditionGrade.NEW, null, null, 10_000L, null, 10))));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		return productId;
	}

	private static ApplyForm form(String no) {
		return new ApplyForm(no, "떨고래상회", "홍길동", null);
	}

	/** 검증번호를 맞춰 만든 임의의 사업자등록번호 (테스트끼리 겹치지 않게) */
	private static String otherValidNo() {
		String head = String.format("%09d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000L);
		int[] w = { 1, 3, 7, 1, 3, 7, 1, 3, 5 };
		int sum = 0;
		for (int i = 0; i < 9; i++) {
			sum += (head.charAt(i) - '0') * w[i];
		}
		sum += (head.charAt(8) - '0') * 5 / 10;
		return head + (10 - sum % 10) % 10;
	}

	private static String uniq() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
