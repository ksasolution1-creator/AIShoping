package com.aishop.api.catalog;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aishop.api.catalog.CatalogRequests.AdjustStock;
import com.aishop.api.catalog.CatalogRequests.ChangeProductStatus;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.CatalogRequests.UpdateSku;
import com.aishop.api.catalog.CatalogViews.AdminProductDetail;
import com.aishop.api.catalog.CatalogViews.AdminProductRow;
import com.aishop.api.catalog.CatalogViews.Image;
import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.PageResponse;

/** 관리자 상품 관리 API. 로그인 기능을 붙이면 /api/admin/** 전체에 권한 검사를 건다. */
@RestController
@RequestMapping("/api/admin")
public class AdminProductController {

	private static final int MAX_PAGE_SIZE = 100;

	private final ProductService productService;
	private final AdminContext admin;

	public AdminProductController(ProductService productService, AdminContext admin) {
		this.productService = productService;
		this.admin = admin;
	}

	@GetMapping("/products")
	public PageResponse<AdminProductRow> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) ProductStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "30") int size) {
		return productService.searchAdmin(admin.sellerId(),
				new AdminSearch(q, status, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
	}

	@GetMapping("/products/{id}")
	public AdminProductDetail detail(@PathVariable long id) {
		return productService.getAdminProduct(admin.sellerId(), id);
	}

	@PostMapping("/products")
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Long> create(@Valid @RequestBody CreateProduct request) {
		return Map.of("id", productService.createProduct(admin.sellerId(), request));
	}

	@PutMapping("/products/{id}")
	public AdminProductDetail update(@PathVariable long id, @Valid @RequestBody ProductFields request) {
		productService.updateProduct(admin.sellerId(), id, request);
		return productService.getAdminProduct(admin.sellerId(), id);
	}

	@PutMapping("/products/{id}/spec")
	public AdminProductDetail updateSpec(@PathVariable long id, @Valid @RequestBody ProductSpec request) {
		return productService.updateSpec(admin.sellerId(), id, request);
	}

	@PutMapping("/products/{id}/wholesale")
	public AdminProductDetail updateWholesale(@PathVariable long id, @Valid @RequestBody WholesaleTerms request) {
		return productService.updateWholesale(admin.sellerId(), id, request);
	}

	@PatchMapping("/products/{id}/status")
	public AdminProductDetail changeStatus(@PathVariable long id, @Valid @RequestBody ChangeProductStatus request) {
		productService.changeStatus(admin.sellerId(), id, request.status());
		return productService.getAdminProduct(admin.sellerId(), id);
	}

	@PostMapping("/products/{id}/skus")
	@ResponseStatus(HttpStatus.CREATED)
	public AdminProductDetail addSku(@PathVariable long id, @Valid @RequestBody CreateSku request) {
		productService.addSku(admin.sellerId(), id, request);
		return productService.getAdminProduct(admin.sellerId(), id);
	}

	@PutMapping("/skus/{skuId}")
	public void updateSku(@PathVariable long skuId, @Valid @RequestBody UpdateSku request) {
		productService.updateSku(admin.sellerId(), skuId, request);
	}

	@PostMapping("/skus/{skuId}/stock-adjustments")
	public void adjustStock(@PathVariable long skuId, @Valid @RequestBody AdjustStock request) {
		productService.adjustStock(admin.sellerId(), skuId, request);
	}

	@PostMapping(path = "/products/{id}/images", consumes = "multipart/form-data")
	@ResponseStatus(HttpStatus.CREATED)
	public Image uploadImage(@PathVariable long id,
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) Long skuId) {
		return productService.addImage(admin.sellerId(), id, skuId, file);
	}

	@DeleteMapping("/products/{id}/images/{imageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteImage(@PathVariable long id, @PathVariable long imageId) {
		productService.deleteImage(admin.sellerId(), id, imageId);
	}

}
