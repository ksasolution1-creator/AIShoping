package com.aishop.api.catalog;

import java.util.List;

/** 쇼핑몰 상품 검색 조건. categoryIds가 null이면 전체 카테고리 */
public record ShopSearch(String keyword, List<Long> categoryIds, ProductSort sort, int page, int size) {

	public int offset() {
		return page * size;
	}

}
