package com.aishop.api.catalog;

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

	public ShopProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping("/products")
	public PageResponse<ProductCard> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(defaultValue = "NEWEST") ProductSort sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "24") int size) {
		return productService.searchShop(q, categoryId, sort, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
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
