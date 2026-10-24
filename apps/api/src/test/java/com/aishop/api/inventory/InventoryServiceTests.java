package com.aishop.api.inventory;

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

import com.aishop.api.TestcontainersConfiguration;
import com.aishop.api.auth.AuthService;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.catalog.ProductService;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.inventory.InventoryViews.AdjustStock;
import com.aishop.api.inventory.InventoryViews.PurchaseDetail;
import com.aishop.api.inventory.InventoryViews.PurchaseForm;
import com.aishop.api.inventory.InventoryViews.PurchaseItemForm;
import com.aishop.api.inventory.InventoryViews.StockRow;
import com.aishop.api.inventory.InventoryViews.SupplierForm;
import com.aishop.api.inventory.InventoryViews.TransferStock;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InventoryServiceTests {

	private static final long SELLER = 1L;
	private static final long OTHER_SELLER = 999L;
	private static final long WAREHOUSE = 1L;
	private static final long STORE = 2L;

	@Autowired
	InventoryService service;

	@Autowired
	ProductService productService;

	@Autowired
	InventoryRepository inventory;

	@Autowired
	AuthService authService;

	long actor;

	@BeforeEach
	void createStaff() {
		actor = authService.createStaff("stock-" + uniq() + "@example.test", "staffpass1234", "재고담당",
				Set.of("OPERATOR"), SELLER);
	}

	@Test
	void receivingPurchaseAddsStockAndRecordsCost() {
		long skuId = createSku(2);
		long supplierId = service.createSupplier(SELLER,
				new SupplierForm("반품 도매상 " + uniq(), null, null, null, null, null));

		PurchaseDetail draft = service.createPurchase(SELLER, actor,
				new PurchaseForm(supplierId, WAREHOUSE, null, "10월 땡처리 매입", List.of(new PurchaseItemForm(skuId, 10, 3_500))));

		assertThat(draft.status()).isEqualTo("DRAFT");
		assertThat(draft.purchaseNo()).startsWith("PO-");
		assertThat(draft.totalCost()).isEqualTo(35_000);
		// 작성만 해서는 재고가 늘지 않는다
		assertThat(stock(skuId).onHand()).isEqualTo(2);

		PurchaseDetail received = service.receive(SELLER, actor, draft.id());

		assertThat(received.status()).isEqualTo("RECEIVED");
		StockRow row = stock(skuId);
		assertThat(row.onHand()).isEqualTo(12);
		assertThat(row.cost()).isEqualTo(3_500);
		assertThat(service.movements(SELLER, skuId)).first().satisfies(m -> {
			assertThat(m.type()).isEqualTo("PURCHASE_IN");
			assertThat(m.qty()).isEqualTo(10);
		});

		// 두 번 입고할 수 없다
		assertThatThrownBy(() -> service.receive(SELLER, actor, draft.id())).isInstanceOf(ApiException.class);
		assertThat(stock(skuId).onHand()).isEqualTo(12);
	}

	@Test
	void cancelledPurchaseCannotBeReceived() {
		long skuId = createSku(0);
		PurchaseDetail draft = service.createPurchase(SELLER, actor,
				new PurchaseForm(null, WAREHOUSE, null, null, List.of(new PurchaseItemForm(skuId, 5, 1_000))));

		service.cancelPurchase(SELLER, draft.id());

		assertThatThrownBy(() -> service.receive(SELLER, actor, draft.id())).isInstanceOf(ApiException.class);
		assertThat(stock(skuId).onHand()).isZero();
	}

	@Test
	void duplicateSkuLinesAreRejected() {
		long skuId = createSku(0);
		assertThatThrownBy(() -> service.createPurchase(SELLER, actor, new PurchaseForm(null, WAREHOUSE, null, null,
				List.of(new PurchaseItemForm(skuId, 1, 100), new PurchaseItemForm(skuId, 2, 100)))))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("한 줄로");
	}

	@Test
	void adjustCannotGoBelowReserved() {
		long skuId = createSku(5);
		assertThat(inventory.reserve(skuId, WAREHOUSE, 3)).isTrue();

		assertThatThrownBy(() -> service.adjust(SELLER, actor, new AdjustStock(skuId, WAREHOUSE, -3, "파손")))
			.isInstanceOf(ApiException.class);

		StockRow row = service.adjust(SELLER, actor, new AdjustStock(skuId, WAREHOUSE, -2, "파손"));
		assertThat(row.onHand()).isEqualTo(3);
		assertThat(row.available()).isZero();
	}

	@Test
	void transferMovesStockBetweenLocations() {
		long skuId = createSku(5);

		StockRow row = service.transfer(SELLER, actor, new TransferStock(skuId, WAREHOUSE, STORE, 2, "매장 진열"));

		assertThat(row.onHand()).isEqualTo(5);
		assertThat(row.locations()).extracting(InventoryViews.LocationStock::onHand).containsExactly(3, 2);
		assertThatThrownBy(() -> service.transfer(SELLER, actor, new TransferStock(skuId, STORE, WAREHOUSE, 3, null)))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void lookupFindsBySkuCodeAndHidesOtherSellers() {
		long skuId = createSku(1);
		String skuCode = stock(skuId).skuCode();

		assertThat(service.lookup(SELLER, skuCode.toLowerCase()).skuId()).isEqualTo(skuId);
		assertThatThrownBy(() -> service.lookup(OTHER_SELLER, skuCode)).isInstanceOf(ApiException.class);
	}

	@Test
	void lowStockFilterShowsOnlyNearlySoldOut() {
		String name = "재고필터 " + uniq();
		long few = createSku(name, 1);
		createSku(name, 30);

		assertThat(service.stock(SELLER, name, null, 2, 0, 50).items()).extracting(StockRow::skuId)
			.containsExactly(few);
	}

	// ------------------------------------------------------------------

	private StockRow stock(long skuId) {
		return inventory.findStockRow(SELLER, skuId).orElseThrow();
	}

	private long createSku(int stock) {
		return createSku("재고 테스트 " + uniq(), stock);
	}

	private long createSku(String name, int stock) {
		ProductFields fields = new ProductFields(name, null, null, null, "설명", Map.of(), null, 3_000L, null);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields,
				List.of(new CreateSku(null, Map.of(), ConditionGrade.RETURN_OPENED, null, null, 10_000L, null, stock))));
		return productService.getAdminProduct(SELLER, productId).skus().getFirst().id();
	}

	private static String uniq() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
