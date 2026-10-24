package com.aishop.api.business;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.business.BusinessViews.AdminApplication;
import com.aishop.api.business.BusinessViews.ProductBusinessTerms;
import com.aishop.api.business.BusinessViews.Review;
import com.aishop.api.catalog.CatalogViews.AdminProductDetail;
import com.aishop.api.catalog.ProductService;
import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.PageResponse;

/** 운영자: 사업자 인증 신청 심사, 상품의 사업자 조건 */
@RestController
@RequestMapping("/api/admin")
public class AdminBusinessController {

	private final BusinessService service;
	private final ProductService productService;
	private final AdminContext admin;

	public AdminBusinessController(BusinessService service, ProductService productService, AdminContext admin) {
		this.service = service;
		this.productService = productService;
		this.admin = admin;
	}

	@GetMapping("/business-members")
	public PageResponse<AdminApplication> applications(@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page) {
		return service.applications(status == null || status.isBlank() ? null : status, Math.max(page, 0));
	}

	@PostMapping("/business-members/{userId}/review")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void review(@PathVariable long userId, @Valid @RequestBody Review body) {
		service.review(userId, admin.user().id(), body.approve(), body.rejectReason());
	}

	@PutMapping("/products/{id}/business")
	public AdminProductDetail updateProductTerms(@PathVariable long id, @Valid @RequestBody ProductBusinessTerms body) {
		long sellerId = admin.sellerId();
		productService.getAdminProduct(sellerId, id);
		service.updateProductTerms(id, body);
		return productService.getAdminProduct(sellerId, id);
	}

}
