package com.aishop.api.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 상품 기본 정보 (도매 거래에서 자주 묻는 항목).
 *
 * @param packageSize 포장 부피 (예: 30x20x10cm)
 * @param packageWeightG 포장 무게 (g)
 */
public record ProductSpec(
		@Size(max = 50) String origin,
		@Size(max = 100) String manufacturer,
		@Size(max = 50) String packageSize,
		@PositiveOrZero @Max(1_000_000) Integer packageWeightG) {
}
