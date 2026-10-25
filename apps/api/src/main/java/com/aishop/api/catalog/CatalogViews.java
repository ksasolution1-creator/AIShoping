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
			int availableQty,
			boolean businessOnly) {
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
			Long businessPrice,
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
			boolean businessOnly,
			boolean imageReuseAllowed,
			WholesaleTerms wholesale,
			ProductSpec spec,
			ReturnInfo returnInfo,
			List<CategoryPathItem> categoryPath,
			List<ShopSku> skus,
			List<Image> images) {
	}

	/**
	 * 반품 안내. 단순 변심 반품비는 편도 배송비, 무료배송으로 받은 주문은 왕복.
	 *
	 * @param returnAddress 판매자가 정한 반품지 (없으면 null)
	 */
	public record ReturnInfo(String returnZipcode, String returnAddress, long oneWayFee) {
	}

	/** 상품 상세 위쪽의 카테고리 경로 (최상위부터) */
	public record CategoryPathItem(long id, String name) {
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
			OffsetDateTime createdAt,
			List<ListSku> skus) {
	}

	/** 목록에서 바로 바꾸는 SKU별 상태등급·판매가. optionText는 SKU가 여러 개일 때 구분용 (예: 색상=블랙) */
	public record ListSku(long skuId, String optionText, Grade grade, long price) {
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
			Long businessPrice,
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
			boolean businessOnly,
			boolean imageReuseAllowed,
			WholesaleTerms wholesale,
			ProductSpec spec,
			List<AdminSku> skus,
			List<Image> images) {
	}

}
