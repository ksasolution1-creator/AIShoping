package com.aishop.api.catalog;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 관리자 상품 관리 요청 */
public final class CatalogRequests {

	private CatalogRequests() {
	}

	public record CreateCategory(
			@NotBlank(message = "카테고리 이름을 입력해 주세요.") @Size(max = 50) String name,
			Long parentId,
			Integer sortOrder) {
	}

	public record ProductFields(
			@NotBlank(message = "상품명을 입력해 주세요.") @Size(max = 200) String name,
			@Size(max = 100) String brand,
			@Size(max = 100) String modelName,
			Long categoryId,
			String description,
			Map<String, String> noticeInfo,
			@Size(max = 50) String kcCertNo,
			@NotNull @PositiveOrZero Long shippingFee,
			@PositiveOrZero Long freeShippingThreshold) {
	}

	public record CreateProduct(
			@NotNull @Valid ProductFields product,
			@NotEmpty(message = "판매 단위(SKU)를 하나 이상 입력해 주세요.") @Valid List<CreateSku> skus) {
	}

	public record CreateSku(
			@Size(max = 60) String skuCode,
			Map<String, String> optionValues,
			@NotNull(message = "상태등급을 선택해 주세요.") ConditionGrade conditionGrade,
			@Size(max = 500) String conditionNote,
			@Size(max = 60) String barcode,
			@NotNull(message = "판매가를 입력해 주세요.") @PositiveOrZero Long price,
			@PositiveOrZero Long listPrice,
			@NotNull @Min(0) @Max(100_000) Integer initialStock) {
	}

	/** @param conditionGrade 바꿀 상태등급. null이면 그대로 둔다 (지난 주문은 주문 당시 등급을 따로 저장해 두어 영향 없음) */
	public record UpdateSku(
			ConditionGrade conditionGrade,
			Map<String, String> optionValues,
			@Size(max = 500) String conditionNote,
			@Size(max = 60) String barcode,
			@NotNull @PositiveOrZero Long price,
			@PositiveOrZero Long listPrice,
			@NotNull SkuStatus status) {
	}

	public record ChangeGrade(@NotNull(message = "상태등급을 선택해 주세요.") ConditionGrade conditionGrade) {
	}

	public record DeleteProducts(@NotEmpty @Size(max = 100) List<Long> ids) {
	}

	public record ChangePrice(@NotNull(message = "판매가를 입력해 주세요.") @PositiveOrZero Long price) {
	}

	public record ChangeProductStatus(@NotNull ProductStatus status) {
	}

	public record AdjustStock(
			Long locationId,
			@NotNull(message = "변경 수량을 입력해 주세요.") Integer qty,
			@Size(max = 200) String memo) {
	}

}
