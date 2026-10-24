package com.aishop.api.catalog;

/** 관리자 상품 검색 조건. keyword는 상품명·브랜드·SKU 코드·바코드 */
public record AdminSearch(String keyword, ProductStatus status, int page, int size) {

	public int offset() {
		return page * size;
	}

}
