package com.aishop.api.catalog;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	/** 카테고리 삭제. 하위 카테고리가 있으면 막고, 들어 있던 상품은 상위 카테고리로 옮긴다 */
	@DeleteMapping("/api/admin/categories/{id}")
	@Transactional
	public Map<String, Integer> delete(@PathVariable long id) {
		if (!categories.exists(id)) {
			throw ApiException.notFound("카테고리");
		}
		if (categories.hasActiveChildren(id)) {
			throw ApiException.badRequest("HAS_CHILDREN", "하위 카테고리가 있습니다. 하위 카테고리를 먼저 삭제해 주세요.");
		}
		int moved = categories.moveProducts(id, categories.findParentId(id).orElse(null));
		categories.deactivate(id);
		return Map.of("movedProducts", moved);
	}

}
