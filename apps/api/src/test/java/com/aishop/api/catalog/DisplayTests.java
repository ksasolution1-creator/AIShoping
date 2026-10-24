package com.aishop.api.catalog;

import static com.aishop.api.jooq.Tables.SEARCH_LOGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.aishop.api.TestcontainersConfiguration;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.CatalogViews.ProductCard;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DisplayTests {

	private static final long SELLER = 1L;

	@Autowired
	ProductService productService;

	@Autowired
	CategoryRepository categories;

	@Autowired
	SearchLogRepository searchLogs;

	@Autowired
	MockMvc mvc;

	@Autowired
	DSLContext dsl;

	@Test
	void specReturnInfoAndCategoryPathOnDetail() {
		long parent = categories.insert("생활 " + uniq(), null, 0);
		long child = categories.insert("주방 " + uniq(), parent, 0);
		long productId = product("냄비 " + uniq(), child, 10_000L, 20_000L);

		productService.updateSpec(SELLER, productId, new ProductSpec("중국", "떨고래상사", "30x20x10cm", 1200));
		var detail = productService.getShopProduct(productId);

		assertThat(detail.spec().origin()).isEqualTo("중국");
		assertThat(detail.spec().packageWeightG()).isEqualTo(1200);
		assertThat(detail.returnInfo().oneWayFee()).isEqualTo(3_000L);
		assertThat(detail.categoryPath()).extracting(CatalogViews.CategoryPathItem::id).containsExactly(parent, child);
		assertThat(productService.getAdminProduct(SELLER, productId).spec().manufacturer()).isEqualTo("떨고래상사");
	}

	@Test
	void ddaengFilterKeepsOnlyFortyPercentOff() {
		String tag = "땡필터" + uniq();
		long cheap = product(tag + " 반값", null, 5_900L, 10_000L);
		product(tag + " 조금할인", null, 9_000L, 10_000L);
		product(tag + " 정가없음", null, 1_000L, null);

		List<ProductCard> ddaeng = productService.searchShop(tag, null, ProductSort.NEWEST, true, 0, 20).items();
		assertThat(ddaeng).extracting(ProductCard::id).containsExactly(cheap);
		assertThat(productService.searchShop(tag, null, ProductSort.NEWEST, 0, 20).items()).hasSize(3);
	}

	@Test
	void bestSortRunsAndListsProducts() {
		String tag = "베스트" + uniq();
		product(tag + " A", null, 1_000L, null);
		product(tag + " B", null, 2_000L, null);

		assertThat(productService.searchShop(tag, null, ProductSort.BEST, 0, 20).items()).hasSize(2);
	}

	@Test
	void searchesAreLoggedAndRankedAsPopular() throws Exception {
		String word = "인기" + uniq();
		mvc.perform(get("/api/products").param("q", word)).andExpect(status().isOk());
		mvc.perform(get("/api/products").param("q", " " + word.toUpperCase() + " ")).andExpect(status().isOk());
		// 다음 페이지 넘김은 다시 세지 않는다
		mvc.perform(get("/api/products").param("q", word).param("page", "1")).andExpect(status().isOk());

		assertThat(dsl.fetchCount(SEARCH_LOGS, SEARCH_LOGS.KEYWORD.equalIgnoreCase(word))).isEqualTo(2);
		assertThat(searchLogs.popular(Duration.ofDays(7), 50)).contains(word.toLowerCase());
		mvc.perform(get("/api/search/popular").with(csrf())).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------

	private long product(String name, Long categoryId, long price, Long listPrice) {
		ProductFields fields = new ProductFields(name, null, null, categoryId, null, Map.of(), null, 3_000L, null);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields,
				List.of(new CreateSku(null, Map.of(), ConditionGrade.NEW, null, null, price, listPrice, 5))));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		return productId;
	}

	private static String uniq() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
