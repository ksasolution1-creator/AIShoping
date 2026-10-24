package com.aishop.api.catalog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 상품 조회 응답 */
public final class CatalogViews {

	private CatalogViews() {
	}

	public record Category(long id, Long parentId, String name, int sortOrder) {
	}

	public record Grade(ConditionGrade code, String label) {

		public static Grade of(ConditionGrade grade) {
			return new Grade(grade, grade.label());
		}

	}

	/** 쇼핑몰 상품 목록 카드 */
	public record ProductCard(
			long id,
			String name,
			String brand,
			String thumbnailUrl,
			long minPrice,
			Long maxListPrice,
			List<Grade> grades,
			int availableQty) {
	}

	public record Image(long id, Long skuId, String url, int sortOrder) {
	}

	/** 쇼핑몰 상품 상세의 판매 단위 */
	public record ShopSku(
			long id,
			Map<String, String> optionValues,
			Grade grade,
			String conditionNote,
			long price,
			Long listPrice,
			int availableQty) {
	}

	public record ShopProductDetail(
			long id,
			String name,
			String brand,
			String modelName,
			Long categoryId,
			String description,
			Map<String, String> noticeInfo,
			String kcCertNo,
			long shippingFee,
			Long freeShippingThreshold,
			List<ShopSku> skus,
			List<Image> images) {
	}

	/** 관리자 상품 목록 행 */
	public record AdminProductRow(
			long id,
			String name,
			String brand,
			String categoryName,
			ProductStatus status,
			String thumbnailUrl,
			int skuCount,
			Long minPrice,
			int onHand,
			int reserved,
			OffsetDateTime createdAt) {
	}

	public record AdminSku(
			long id,
			String skuCode,
			Map<String, String> optionValues,
			Grade grade,
			String conditionNote,
			String barcode,
			long price,
			Long listPrice,
			SkuStatus status,
			int onHand,
			int reserved) {
	}

	public record AdminProductDetail(
			long id,
			long sellerId,
			String name,
			String brand,
			String modelName,
			Long categoryId,
			String description,
			Map<String, String> noticeInfo,
			String kcCertNo,
			long shippingFee,
			Long freeShippingThreshold,
			ProductStatus status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt,
			List<AdminSku> skus,
			List<Image> images) {
	}

}
