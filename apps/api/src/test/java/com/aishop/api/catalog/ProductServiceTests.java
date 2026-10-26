package com.aishop.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aishop.api.TestcontainersConfiguration;
import com.aishop.api.catalog.CatalogRequests.AdjustStock;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.CatalogViews.AdminProductDetail;
import com.aishop.api.catalog.CatalogViews.ProductCard;
import com.aishop.api.catalog.CatalogViews.ShopProductDetail;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.inventory.InventoryRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductServiceTests {

	private static final long SELLER = 1L;
	private static final long OTHER_SELLER = 999L;
	private static final long WAREHOUSE = 1L;

	@Autowired
	ProductService productService;

	@Autowired
	CategoryRepository categories;

	@Autowired
	InventoryRepository inventory;

	@Test
	void createProductWithSkusAndInitialStock() {
		long productId = createProduct("에어팟 프로 " + uniq(), null,
				sku(ConditionGrade.RETURN_UNOPENED, 250_000L, 3),
				sku(ConditionGrade.USED_B, 180_000L, 1));

		AdminProductDetail detail = productService.getAdminProduct(SELLER, productId);

		assertThat(detail.status()).isEqualTo(ProductStatus.DRAFT);
		assertThat(detail.skus()).hasSize(2);
		assertThat(detail.skus()).extracting(s -> s.grade().code())
			.containsExactly(ConditionGrade.RETURN_UNOPENED, ConditionGrade.USED_B);
		assertThat(detail.skus()).extracting(CatalogViews.AdminSku::onHand).containsExactly(3, 1);
		assertThat(detail.skus().getFirst().skuCode()).isEqualTo("P" + productId + "-1");
	}

	@Test
	void expiryDateIsSavedAndShownInShop() {
		ProductFields fields = new ProductFields("참치캔 " + uniq(), null, null, null, null, Map.of(), null, 3_000L,
				null, LocalDate.of(2027, 3, 1));
		long productId = productService.createProduct(SELLER,
				new CreateProduct(fields, List.of(sku(ConditionGrade.NEW, 10_000L, 5))));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);

		assertThat(productService.getAdminProduct(SELLER, productId).expiryDate()).isEqualTo(LocalDate.of(2027, 3, 1));
		assertThat(productService.getShopProduct(productId).expiryDate()).isEqualTo(LocalDate.of(2027, 3, 1));

		// 수정 화면에서 비우면 지워진다
		productService.updateProduct(SELLER, productId, new ProductFields(fields.name(), null, null, null, null,
				Map.of(), null, 3_000L, null, null));
		assertThat(productService.getShopProduct(productId).expiryDate()).isNull();
	}

	@Test
	void draftProductIsHiddenFromShopUntilOnSale() {
		String name = "다이슨 청소기 " + uniq();
		long productId = createProduct(name, null, sku(ConditionGrade.REFURB, 300_000L, 2));

		assertThat(searchShop(name).items()).isEmpty();
		assertThatThrownBy(() -> productService.getShopProduct(productId)).isInstanceOf(ApiException.class);

		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);

		PageResponse<ProductCard> result = searchShop(name);
		assertThat(result.items()).singleElement().satisfies(card -> {
			assertThat(card.minPrice()).isEqualTo(300_000L);
			assertThat(card.availableQty()).isEqualTo(2);
			assertThat(card.grades()).extracting(CatalogViews.Grade::label).containsExactly("리퍼");
		});
	}

	@Test
	void shopShowsAvailableQtyMinusReservations() {
		long productId = createProduct("갤럭시 버즈 " + uniq(), null, sku(ConditionGrade.NEW, 99_000L, 5));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		long skuId = productService.getAdminProduct(SELLER, productId).skus().getFirst().id();

		inventory.reserve(skuId, WAREHOUSE, 2);

		ShopProductDetail detail = productService.getShopProduct(productId);
		assertThat(detail.skus()).singleElement().extracting(CatalogViews.ShopSku::availableQty).isEqualTo(3);
	}

	@Test
	void categoryFilterIncludesSubcategories() {
		long parent = categories.insert("가전 " + uniq(), null, 0);
		long child = categories.insert("주방가전", parent, 0);
		String name = "에어프라이어 " + uniq();
		long productId = createProduct(name, child, sku(ConditionGrade.DISPLAY, 70_000L, 1));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);

		PageResponse<ProductCard> result = productService.searchShop(name, parent, ProductSort.NEWEST, 0, 10);

		assertThat(result.items()).extracting(ProductCard::id).containsExactly(productId);
	}

	@Test
	void cannotSellWithoutActiveSku() {
		long productId = createProduct("단종 상품 " + uniq(), null, sku(ConditionGrade.NEW, 10_000L, 0));
		long skuId = productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
		productService.updateSku(SELLER, skuId,
				new CatalogRequests.UpdateSku(null, Map.of(), null, null, 10_000L, null, SkuStatus.INACTIVE));

		assertThatThrownBy(() -> productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE))
			.isInstanceOf(ApiException.class)
			.extracting("code").isEqualTo("NO_ACTIVE_SKU");
	}

	@Test
	void gradeAndPriceCanBeChangedAfterRegistration() {
		String name = "등급 변경 " + uniq();
		long productId = createProduct(name, null, sku(ConditionGrade.NEW, 30_000L, 1));
		long skuId = productService.getAdminProduct(SELLER, productId).skus().getFirst().id();

		// 수정 화면: 등급을 넣으면 바뀌고, null이면 그대로
		productService.updateSku(SELLER, skuId,
				new CatalogRequests.UpdateSku(ConditionGrade.DISPLAY, Map.of(), "전시 사용감", null, 30_000L, null,
						SkuStatus.ACTIVE));
		productService.updateSku(SELLER, skuId,
				new CatalogRequests.UpdateSku(null, Map.of(), "전시 사용감", null, 28_000L, null, SkuStatus.ACTIVE));
		assertThat(productService.getAdminProduct(SELLER, productId).skus().getFirst().grade().code())
			.isEqualTo(ConditionGrade.DISPLAY);

		// 목록: 등급만 바로 변경, 목록 행에도 SKU 등급이 실린다
		productService.changeSkuGrade(SELLER, skuId, ConditionGrade.USED_A);
		var row = productService.searchAdmin(SELLER, new AdminSearch(name, null, 0, 10))
			.items().stream().filter(r -> r.id() == productId).findFirst().orElseThrow();
		assertThat(row.skus()).extracting(s -> s.grade().code()).containsExactly(ConditionGrade.USED_A);

		// 목록: 판매가만 바로 변경
		productService.changeSkuPrice(SELLER, skuId, 25_000L);
		assertThat(productService.getAdminProduct(SELLER, productId).skus().getFirst().price()).isEqualTo(25_000L);
		assertThatThrownBy(() -> productService.changeSkuPrice(OTHER_SELLER, skuId, 1L))
			.isInstanceOf(ApiException.class);

		// 다른 판매자의 SKU는 바꿀 수 없다
		assertThatThrownBy(() -> productService.changeSkuGrade(OTHER_SELLER, skuId, ConditionGrade.NEW))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void deleteProductAndSkuWithGuards() {
		long productId = createProduct("삭제 테스트 " + uniq(), null,
				sku(ConditionGrade.NEW, 10_000L, 2), sku(ConditionGrade.USED_A, 8_000L, 1));
		List<CatalogViews.AdminSku> skus = productService.getAdminProduct(SELLER, productId).skus();
		long first = skus.get(0).id();
		long second = skus.get(1).id();

		// 주문이 잡아 둔 재고가 있으면 SKU도 상품도 지울 수 없다
		inventory.reserve(first, WAREHOUSE, 1);
		assertThatThrownBy(() -> productService.deleteSku(SELLER, first))
			.isInstanceOf(ApiException.class).extracting("code").isEqualTo("HAS_OPEN_ORDERS");
		assertThatThrownBy(() -> productService.deleteProduct(SELLER, productId))
			.isInstanceOf(ApiException.class).extracting("code").isEqualTo("HAS_OPEN_ORDERS");
		inventory.release(first, WAREHOUSE, 1);

		// SKU 하나 삭제 → 마지막 SKU는 지울 수 없음 → 상품 삭제
		productService.deleteSku(SELLER, second);
		assertThat(productService.getAdminProduct(SELLER, productId).skus()).extracting(CatalogViews.AdminSku::id)
			.containsExactly(first);
		assertThatThrownBy(() -> productService.deleteSku(SELLER, first))
			.isInstanceOf(ApiException.class).extracting("code").isEqualTo("LAST_SKU");
		assertThatThrownBy(() -> productService.deleteProduct(OTHER_SELLER, productId))
			.isInstanceOf(ApiException.class);
		productService.deleteProduct(SELLER, productId);
		assertThatThrownBy(() -> productService.getAdminProduct(SELLER, productId))
			.isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_FOUND");
	}

	@Test
	void setStockToTargetQuantity() {
		long productId = createProduct("재고 맞추기 " + uniq(), null, sku(ConditionGrade.NEW, 10_000L, 5));
		long skuId = productService.getAdminProduct(SELLER, productId).skus().getFirst().id();

		assertThat(productService.setStock(SELLER, skuId, 8, null)).containsExactly(8, 0);
		assertThat(productService.setStock(SELLER, skuId, 3, null)).containsExactly(3, 0);
		assertThat(productService.getAdminProduct(SELLER, productId).skus().getFirst().onHand()).isEqualTo(3);

		// 주문이 잡아 둔 2개보다 적게는 못 줄인다
		inventory.reserve(skuId, WAREHOUSE, 2);
		assertThatThrownBy(() -> productService.setStock(SELLER, skuId, 1, null))
			.isInstanceOf(ApiException.class).extracting("code").isEqualTo("INSUFFICIENT_STOCK");
		assertThat(productService.setStock(SELLER, skuId, 2, null)).containsExactly(2, 2);
		assertThatThrownBy(() -> productService.setStock(OTHER_SELLER, skuId, 9, null))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void stockCannotDropBelowReserved() {
		long productId = createProduct("키보드 " + uniq(), null, sku(ConditionGrade.RETURN_OPENED, 50_000L, 3));
		long skuId = productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
		inventory.reserve(skuId, WAREHOUSE, 2);

		assertThatThrownBy(() -> productService.adjustStock(SELLER, skuId, new AdjustStock(null, -2, "파손")))
			.isInstanceOf(ApiException.class)
			.extracting("code").isEqualTo("INSUFFICIENT_STOCK");

		productService.adjustStock(SELLER, skuId, new AdjustStock(null, -1, "파손"));
		productService.adjustStock(SELLER, skuId, new AdjustStock(null, 4, "추가 입고"));

		assertThat(productService.getAdminProduct(SELLER, productId).skus().getFirst().onHand()).isEqualTo(6);
	}

	@Test
	void otherSellerCannotSeeOrEditProduct() {
		long productId = createProduct("모니터 " + uniq(), null, sku(ConditionGrade.NEW, 200_000L, 1));

		assertThatThrownBy(() -> productService.getAdminProduct(OTHER_SELLER, productId))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> productService.changeStatus(OTHER_SELLER, productId, ProductStatus.HIDDEN))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void duplicateSkuCodeIsRejected() {
		String code = "DUP-" + uniq();
		CreateSku first = new CreateSku(code, Map.of(), ConditionGrade.NEW, null, null, 1_000L, null, 0);
		createProduct("상품A " + uniq(), null, first);

		assertThatThrownBy(() -> createProduct("상품B " + uniq(), null, first))
			.isInstanceOf(ApiException.class)
			.extracting("code").isEqualTo("DUPLICATE_SKU_CODE");
	}

	// ------------------------------------------------------------------

	private long createProduct(String name, Long categoryId, CreateSku... skus) {
		ProductFields fields = new ProductFields(name, "브랜드", null, categoryId, "설명", Map.of("제조국", "중국"), null,
				3_000L, 50_000L);
		return productService.createProduct(SELLER, new CreateProduct(fields, List.of(skus)));
	}

	private static CreateSku sku(ConditionGrade grade, long price, int stock) {
		return new CreateSku(null, Map.of(), grade, null, null, price, null, stock);
	}

	private PageResponse<ProductCard> searchShop(String keyword) {
		return productService.searchShop(keyword, null, ProductSort.NEWEST, 0, 10);
	}

	private static String uniq() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
