package com.aishop.api.catalog;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.catalog.CatalogRequests.CreateCategory;
import com.aishop.api.catalog.CatalogViews.Category;
import com.aishop.api.common.web.ApiException;

@RestController
public class CategoryController {

	private final CategoryRepository categories;

	public CategoryController(CategoryRepository categories) {
		this.categories = categories;
	}

	@GetMapping("/api/categories")
	public List<Category> list() {
		return categories.findActive();
	}

	@PostMapping("/api/admin/categories")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public Map<String, Long> create(@Valid @RequestBody CreateCategory request) {
		if (request.parentId() != null && !categories.exists(request.parentId())) {
			throw ApiException.badRequest("INVALID_CATEGORY", "상위 카테고리가 없습니다.");
		}
		long id = categories.insert(request.name().strip(), request.parentId(),
				request.sortOrder() == null ? 0 : request.sortOrder());
		return Map.of("id", id);
	}

}
