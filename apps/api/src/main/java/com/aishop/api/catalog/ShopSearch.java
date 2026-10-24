package com.aishop.api.catalog;

import java.util.List;

/**
 * 쇼핑몰 상품 검색 조건. categoryIds가 null이면 전체 카테고리.
 *
 * @param ddaengOnly 정가 대비 40% 이상 싼 상품만 (땡처리관)
 */
public record ShopSearch(String keyword, List<Long> categoryIds, ProductSort sort, boolean ddaengOnly, int page,
		int size) {

	/** 땡처리로 보는 할인율 (%) */
	public static final int DDAENG_RATE = 40;

	public int offset() {
		return page * size;
	}

}
