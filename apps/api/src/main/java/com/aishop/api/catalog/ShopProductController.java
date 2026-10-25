package com.aishop.api.catalog;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.catalog.CatalogViews.Grade;
import com.aishop.api.catalog.CatalogViews.ProductCard;
import com.aishop.api.catalog.CatalogViews.ShopProductDetail;
import com.aishop.api.common.web.PageResponse;

/** 쇼핑몰 공개 API */
@RestController
@RequestMapping("/api")
public class ShopProductController {

	private static final int MAX_PAGE_SIZE = 60;

	private final ProductService productService;
	private final SearchLogRepository searchLogs;

	public ShopProductController(ProductService productService, SearchLogRepository searchLogs) {
		this.productService = productService;
		this.searchLogs = searchLogs;
	}

	@GetMapping("/products")
	public PageResponse<ProductCard> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(defaultValue = "NEWEST") ProductSort sort,
			@RequestParam(defaultValue = "false") boolean ddaeng,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "24") int size) {
		PageResponse<ProductCard> result = productService.searchShop(q, categoryId, sort, ddaeng, Math.max(page, 0),
				Math.clamp(size, 1, MAX_PAGE_SIZE));
		// 첫 페이지 검색만 인기검색어 집계에 남긴다 (다음 페이지 넘김은 같은 검색).
		// 결과가 없는 검색어(오타·장난 입력)는 인기검색어에 올라가지 않게 뺀다
		if (q != null && !q.isBlank() && q.strip().length() <= 30 && page == 0 && result.total() > 0) {
			searchLogs.log(q.strip());
		}
		return result;
	}

	/** 최근 7일 인기검색어 10개 */
	@GetMapping("/search/popular")
	public List<String> popularKeywords() {
		return searchLogs.popular(Duration.ofDays(7), 10);
	}

	@GetMapping("/products/{id}")
	public ShopProductDetail detail(@PathVariable long id) {
		return productService.getShopProduct(id);
	}

	@GetMapping("/condition-grades")
	public List<Grade> conditionGrades() {
		return Arrays.stream(ConditionGrade.values()).map(Grade::of).toList();
	}

}
