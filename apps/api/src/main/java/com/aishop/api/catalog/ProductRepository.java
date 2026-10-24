package com.aishop.api.catalog;

import static com.aishop.api.jooq.Tables.CATEGORIES;
import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.INVENTORY_MOVEMENTS;
import static com.aishop.api.jooq.Tables.LOCATIONS;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.PRODUCT_IMAGES;
import static com.aishop.api.jooq.Tables.SKUS;
import static org.jooq.impl.DSL.arrayAggDistinct;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.sum;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.springframework.stereotype.Repository;

import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.CatalogRequests.UpdateSku;
import com.aishop.api.catalog.CatalogViews.AdminProductDetail;
import com.aishop.api.catalog.CatalogViews.AdminProductRow;
import com.aishop.api.catalog.CatalogViews.AdminSku;
import com.aishop.api.catalog.CatalogViews.Grade;
import com.aishop.api.catalog.CatalogViews.Image;
import com.aishop.api.catalog.CatalogViews.ProductCard;
import com.aishop.api.catalog.CatalogViews.ShopProductDetail;
import com.aishop.api.catalog.CatalogViews.ShopSku;
import com.aishop.api.common.db.JsonbMapper;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.jooq.tables.records.ProductsRecord;

@Repository
public class ProductRepository {

	private static final String ACTIVE = SkuStatus.ACTIVE.name();
	private static final String DELETED_SKU = SkuStatus.DELETED.name();
	private static final List<String> SHOP_VISIBLE = List.of(ProductStatus.ON_SALE.name(), ProductStatus.SOLD_OUT.name());

	private final DSLContext dsl;
	private final JsonbMapper jsonb;

	public ProductRepository(DSLContext dsl, JsonbMapper jsonb) {
		this.dsl = dsl;
		this.jsonb = jsonb;
	}

	// ------------------------------------------------------------------
	// 쇼핑몰 조회
	// ------------------------------------------------------------------

	public PageResponse<ProductCard> findShopProducts(ShopSearch search) {
		Field<Long> minPrice = field(select(min(SKUS.PRICE)).from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.eq(ACTIVE)));
		Field<Long> maxListPrice = field(select(max(SKUS.LIST_PRICE)).from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.eq(ACTIVE)));
		Field<String[]> grades = field(select(arrayAggDistinct(SKUS.CONDITION_GRADE)).from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.eq(ACTIVE)));
		Field<BigDecimal> available = productAvailable();
		Field<String> thumbnail = thumbnail();

		Condition condition = PRODUCTS.STATUS.in(SHOP_VISIBLE)
			.andExists(selectOne().from(SKUS).where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.eq(ACTIVE)))
			.and(keywordCondition(search.keyword()))
			.and(search.categoryIds() == null ? noCondition() : PRODUCTS.CATEGORY_ID.in(search.categoryIds()));

		SortField<?>[] orderBy = switch (search.sort()) {
			case PRICE_ASC -> new SortField<?>[] { minPrice.asc(), PRODUCTS.ID.desc() };
			case PRICE_DESC -> new SortField<?>[] { minPrice.desc(), PRODUCTS.ID.desc() };
			case NEWEST -> new SortField<?>[] { PRODUCTS.ID.desc() };
		};

		List<ProductCard> items = dsl
			.select(PRODUCTS.ID, PRODUCTS.NAME, PRODUCTS.BRAND, thumbnail, minPrice, maxListPrice, grades, available)
			.from(PRODUCTS)
			.where(condition)
			.orderBy(orderBy)
			.limit(search.size())
			.offset(search.offset())
			.fetch(r -> new ProductCard(
					r.value1(), r.value2(), r.value3(), r.value4(),
					r.value5() == null ? 0 : r.value5(), r.value6(),
					toGrades(r.value7()), toInt(r.value8())));

		long total = dsl.fetchCount(PRODUCTS, condition);
		return new PageResponse<>(items, search.page(), search.size(), total);
	}

	public Optional<ShopProductDetail> findShopProduct(long productId) {
		return dsl.selectFrom(PRODUCTS)
			.where(PRODUCTS.ID.eq(productId), PRODUCTS.STATUS.in(SHOP_VISIBLE))
			.fetchOptional()
			.map(p -> new ShopProductDetail(
					p.getId(), p.getName(), p.getBrand(), p.getModelName(), p.getCategoryId(), p.getDescription(),
					jsonb.toStringMap(p.getNoticeInfo()), p.getKcCertNo(), p.getShippingFee(),
					p.getFreeShippingThreshold(), findShopSkus(productId), findImages(productId)));
	}

	private List<ShopSku> findShopSkus(long productId) {
		Field<BigDecimal> available = skuAvailable();
		return dsl.select(SKUS.ID, SKUS.OPTION_VALUES, SKUS.CONDITION_GRADE, SKUS.CONDITION_NOTE, SKUS.PRICE,
				SKUS.LIST_PRICE, available)
			.from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(productId), SKUS.STATUS.eq(ACTIVE))
			.orderBy(SKUS.PRICE, SKUS.ID)
			.fetch(r -> new ShopSku(r.value1(), jsonb.toStringMap(r.value2()),
					Grade.of(ConditionGrade.valueOf(r.value3())), r.value4(), r.value5(), r.value6(),
					toInt(r.value7())));
	}

	// ------------------------------------------------------------------
	// 관리자 조회
	// ------------------------------------------------------------------

	public PageResponse<AdminProductRow> findAdminProducts(long sellerId, AdminSearch search) {
		Field<Integer> skuCount = field(select(count()).from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.ne(DELETED_SKU)));
		Field<Long> minPrice = field(select(min(SKUS.PRICE)).from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.eq(ACTIVE)));
		Field<BigDecimal> onHand = field(select(coalesce(sum(INVENTORIES.ON_HAND), BigDecimal.ZERO))
			.from(INVENTORIES).join(SKUS).on(SKUS.ID.eq(INVENTORIES.SKU_ID))
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.ne(DELETED_SKU)));
		Field<BigDecimal> reserved = field(select(coalesce(sum(INVENTORIES.RESERVED), BigDecimal.ZERO))
			.from(INVENTORIES).join(SKUS).on(SKUS.ID.eq(INVENTORIES.SKU_ID))
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.ne(DELETED_SKU)));

		Condition condition = PRODUCTS.SELLER_ID.eq(sellerId)
			.and(search.status() == null
					? PRODUCTS.STATUS.ne(ProductStatus.DELETED.name())
					: PRODUCTS.STATUS.eq(search.status().name()))
			.and(adminKeywordCondition(search.keyword()));

		List<AdminProductRow> items = dsl
			.select(PRODUCTS.ID, PRODUCTS.NAME, PRODUCTS.BRAND, CATEGORIES.NAME, PRODUCTS.STATUS, thumbnail(),
					skuCount, minPrice, onHand, reserved, PRODUCTS.CREATED_AT)
			.from(PRODUCTS)
			.leftJoin(CATEGORIES).on(CATEGORIES.ID.eq(PRODUCTS.CATEGORY_ID))
			.where(condition)
			.orderBy(PRODUCTS.ID.desc())
			.limit(search.size())
			.offset(search.offset())
			.fetch(r -> new AdminProductRow(r.value1(), r.value2(), r.value3(), r.value4(),
					ProductStatus.valueOf(r.value5()), r.value6(), r.value7(), r.value8(),
					toInt(r.value9()), toInt(r.value10()), r.value11()));

		long total = dsl.fetchCount(PRODUCTS, condition);
		return new PageResponse<>(items, search.page(), search.size(), total);
	}

	public Optional<AdminProductDetail> findAdminProduct(long sellerId, long productId) {
		return findProductRecord(sellerId, productId)
			.map(p -> new AdminProductDetail(
					p.getId(), p.getSellerId(), p.getName(), p.getBrand(), p.getModelName(), p.getCategoryId(),
					p.getDescription(), jsonb.toStringMap(p.getNoticeInfo()), p.getKcCertNo(), p.getShippingFee(),
					p.getFreeShippingThreshold(), ProductStatus.valueOf(p.getStatus()), p.getCreatedAt(),
					p.getUpdatedAt(), findAdminSkus(productId), findImages(productId)));
	}

	private List<AdminSku> findAdminSkus(long productId) {
		Field<BigDecimal> onHand = field(select(coalesce(sum(INVENTORIES.ON_HAND), BigDecimal.ZERO))
			.from(INVENTORIES).where(INVENTORIES.SKU_ID.eq(SKUS.ID)));
		Field<BigDecimal> reserved = field(select(coalesce(sum(INVENTORIES.RESERVED), BigDecimal.ZERO))
			.from(INVENTORIES).where(INVENTORIES.SKU_ID.eq(SKUS.ID)));
		return dsl.select(SKUS.ID, SKUS.SKU_CODE, SKUS.OPTION_VALUES, SKUS.CONDITION_GRADE, SKUS.CONDITION_NOTE,
				SKUS.BARCODE, SKUS.PRICE, SKUS.LIST_PRICE, SKUS.STATUS, onHand, reserved)
			.from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(productId), SKUS.STATUS.ne(DELETED_SKU))
			.orderBy(SKUS.ID)
			.fetch(r -> new AdminSku(r.value1(), r.value2(), jsonb.toStringMap(r.value3()),
					Grade.of(ConditionGrade.valueOf(r.value4())), r.value5(), r.value6(), r.value7(), r.value8(),
					SkuStatus.valueOf(r.value9()), toInt(r.value10()), toInt(r.value11())));
	}

	public Optional<ProductsRecord> findProductRecord(long sellerId, long productId) {
		return dsl.selectFrom(PRODUCTS)
			.where(PRODUCTS.ID.eq(productId), PRODUCTS.SELLER_ID.eq(sellerId),
					PRODUCTS.STATUS.ne(ProductStatus.DELETED.name()))
			.fetchOptional();
	}

	public boolean hasActiveSku(long productId) {
		return dsl.fetchExists(SKUS, SKUS.PRODUCT_ID.eq(productId).and(SKUS.STATUS.eq(ACTIVE)));
	}

	/** SKU가 이 판매자의 상품에 속하면 상품 ID를 돌려준다. */
	public Optional<Long> findProductIdOfSku(long sellerId, long skuId) {
		return dsl.select(PRODUCTS.ID)
			.from(SKUS).join(PRODUCTS).on(PRODUCTS.ID.eq(SKUS.PRODUCT_ID))
			.where(SKUS.ID.eq(skuId), SKUS.STATUS.ne(DELETED_SKU), PRODUCTS.SELLER_ID.eq(sellerId))
			.fetchOptional(PRODUCTS.ID);
	}

	public boolean skuCodeExists(String skuCode) {
		return dsl.fetchExists(SKUS, SKUS.SKU_CODE.eq(skuCode));
	}

	// ------------------------------------------------------------------
	// 상품·SKU 저장
	// ------------------------------------------------------------------

	public long insertProduct(long sellerId, ProductFields f) {
		return dsl.insertInto(PRODUCTS)
			.set(PRODUCTS.SELLER_ID, sellerId)
			.set(PRODUCTS.NAME, f.name().strip())
			.set(PRODUCTS.BRAND, blankToNull(f.brand()))
			.set(PRODUCTS.MODEL_NAME, blankToNull(f.modelName()))
			.set(PRODUCTS.CATEGORY_ID, f.categoryId())
			.set(PRODUCTS.DESCRIPTION, f.description())
			.set(PRODUCTS.NOTICE_INFO, jsonb.toJsonb(f.noticeInfo()))
			.set(PRODUCTS.KC_CERT_NO, blankToNull(f.kcCertNo()))
			.set(PRODUCTS.SHIPPING_FEE, f.shippingFee())
			.set(PRODUCTS.FREE_SHIPPING_THRESHOLD, f.freeShippingThreshold())
			.returning(PRODUCTS.ID)
			.fetchOne(PRODUCTS.ID);
	}

	public void updateProduct(long productId, ProductFields f) {
		dsl.update(PRODUCTS)
			.set(PRODUCTS.NAME, f.name().strip())
			.set(PRODUCTS.BRAND, blankToNull(f.brand()))
			.set(PRODUCTS.MODEL_NAME, blankToNull(f.modelName()))
			.set(PRODUCTS.CATEGORY_ID, f.categoryId())
			.set(PRODUCTS.DESCRIPTION, f.description())
			.set(PRODUCTS.NOTICE_INFO, jsonb.toJsonb(f.noticeInfo()))
			.set(PRODUCTS.KC_CERT_NO, blankToNull(f.kcCertNo()))
			.set(PRODUCTS.SHIPPING_FEE, f.shippingFee())
			.set(PRODUCTS.FREE_SHIPPING_THRESHOLD, f.freeShippingThreshold())
			.where(PRODUCTS.ID.eq(productId))
			.execute();
	}

	public void updateProductStatus(long productId, ProductStatus status) {
		dsl.update(PRODUCTS).set(PRODUCTS.STATUS, status.name()).where(PRODUCTS.ID.eq(productId)).execute();
	}

	public long insertSku(long productId, String skuCode, CreateSku s) {
		return dsl.insertInto(SKUS)
			.set(SKUS.PRODUCT_ID, productId)
			.set(SKUS.SKU_CODE, skuCode)
			.set(SKUS.OPTION_VALUES, jsonb.toJsonb(s.optionValues()))
			.set(SKUS.CONDITION_GRADE, s.conditionGrade().name())
			.set(SKUS.CONDITION_NOTE, blankToNull(s.conditionNote()))
			.set(SKUS.BARCODE, blankToNull(s.barcode()))
			.set(SKUS.PRICE, s.price())
			.set(SKUS.LIST_PRICE, s.listPrice())
			.returning(SKUS.ID)
			.fetchOne(SKUS.ID);
	}

	public void updateSku(long skuId, UpdateSku s) {
		dsl.update(SKUS)
			.set(SKUS.OPTION_VALUES, jsonb.toJsonb(s.optionValues()))
			.set(SKUS.CONDITION_NOTE, blankToNull(s.conditionNote()))
			.set(SKUS.BARCODE, blankToNull(s.barcode()))
			.set(SKUS.PRICE, s.price())
			.set(SKUS.LIST_PRICE, s.listPrice())
			.set(SKUS.STATUS, s.status().name())
			.where(SKUS.ID.eq(skuId))
			.execute();
	}

	// ------------------------------------------------------------------
	// 재고
	// ------------------------------------------------------------------

	public Optional<Long> findDefaultWarehouseId(long sellerId) {
		return dsl.select(LOCATIONS.ID)
			.from(LOCATIONS)
			.where(LOCATIONS.SELLER_ID.eq(sellerId), LOCATIONS.TYPE.eq("WAREHOUSE"), LOCATIONS.IS_ACTIVE.isTrue())
			.orderBy(LOCATIONS.ID)
			.limit(1)
			.fetchOptional(LOCATIONS.ID);
	}

	public boolean locationBelongsTo(long sellerId, long locationId) {
		return dsl.fetchExists(LOCATIONS, LOCATIONS.ID.eq(locationId).and(LOCATIONS.SELLER_ID.eq(sellerId)));
	}

	/**
	 * 보유 재고(on_hand)를 qty만큼 늘리거나 줄인다.
	 * 줄일 때는 예약된 수량 아래로 내려가지 않는 경우에만 반영한다.
	 *
	 * @return 반영 여부
	 */
	public boolean changeOnHand(long skuId, long locationId, int qty) {
		if (qty >= 0) {
			dsl.insertInto(INVENTORIES)
				.set(INVENTORIES.SKU_ID, skuId)
				.set(INVENTORIES.LOCATION_ID, locationId)
				.set(INVENTORIES.ON_HAND, qty)
				.onConflict(INVENTORIES.SKU_ID, INVENTORIES.LOCATION_ID)
				.doUpdate()
				.set(INVENTORIES.ON_HAND, INVENTORIES.ON_HAND.plus(qty))
				.set(INVENTORIES.UPDATED_AT, currentOffsetDateTime())
				.execute();
			return true;
		}
		return dsl.update(INVENTORIES)
			.set(INVENTORIES.ON_HAND, INVENTORIES.ON_HAND.plus(qty))
			.set(INVENTORIES.UPDATED_AT, currentOffsetDateTime())
			.where(INVENTORIES.SKU_ID.eq(skuId), INVENTORIES.LOCATION_ID.eq(locationId))
			.and(INVENTORIES.ON_HAND.plus(qty).ge(INVENTORIES.RESERVED))
			.execute() == 1;
	}

	public void insertMovement(long skuId, long locationId, int qty, String type, String memo) {
		dsl.insertInto(INVENTORY_MOVEMENTS)
			.set(INVENTORY_MOVEMENTS.SKU_ID, skuId)
			.set(INVENTORY_MOVEMENTS.LOCATION_ID, locationId)
			.set(INVENTORY_MOVEMENTS.QTY, qty)
			.set(INVENTORY_MOVEMENTS.TYPE, type)
			.set(INVENTORY_MOVEMENTS.MEMO, memo)
			.execute();
	}

	// ------------------------------------------------------------------
	// 이미지
	// ------------------------------------------------------------------

	public List<Image> findImages(long productId) {
		return dsl.select(PRODUCT_IMAGES.ID, PRODUCT_IMAGES.SKU_ID, PRODUCT_IMAGES.URL, PRODUCT_IMAGES.SORT_ORDER)
			.from(PRODUCT_IMAGES)
			.where(PRODUCT_IMAGES.PRODUCT_ID.eq(productId))
			.orderBy(PRODUCT_IMAGES.SKU_ID.nullsFirst(), PRODUCT_IMAGES.SORT_ORDER, PRODUCT_IMAGES.ID)
			.fetch(r -> new Image(r.value1(), r.value2(), r.value3(), r.value4()));
	}

	public long insertImage(long productId, Long skuId, String url) {
		Integer nextOrder = dsl.select(coalesce(max(PRODUCT_IMAGES.SORT_ORDER), -1).plus(1))
			.from(PRODUCT_IMAGES)
			.where(PRODUCT_IMAGES.PRODUCT_ID.eq(productId))
			.fetchOne(0, Integer.class);
		return dsl.insertInto(PRODUCT_IMAGES)
			.set(PRODUCT_IMAGES.PRODUCT_ID, productId)
			.set(PRODUCT_IMAGES.SKU_ID, skuId)
			.set(PRODUCT_IMAGES.URL, url)
			.set(PRODUCT_IMAGES.SORT_ORDER, nextOrder)
			.returning(PRODUCT_IMAGES.ID)
			.fetchOne(PRODUCT_IMAGES.ID);
	}

	public Optional<String> deleteImage(long productId, long imageId) {
		return dsl.deleteFrom(PRODUCT_IMAGES)
			.where(PRODUCT_IMAGES.ID.eq(imageId), PRODUCT_IMAGES.PRODUCT_ID.eq(productId))
			.returning(PRODUCT_IMAGES.URL)
			.fetchOptional(PRODUCT_IMAGES.URL);
	}

	// ------------------------------------------------------------------

	/** 대표 사진: 상품 공통 사진 우선, 정렬 순서대로 첫 장 */
	private static Field<String> thumbnail() {
		return field(select(PRODUCT_IMAGES.URL).from(PRODUCT_IMAGES)
			.where(PRODUCT_IMAGES.PRODUCT_ID.eq(PRODUCTS.ID))
			.orderBy(PRODUCT_IMAGES.SKU_ID.nullsFirst(), PRODUCT_IMAGES.SORT_ORDER, PRODUCT_IMAGES.ID)
			.limit(1));
	}

	private static Field<BigDecimal> productAvailable() {
		return field(select(coalesce(sum(INVENTORIES.ON_HAND.minus(INVENTORIES.RESERVED)), BigDecimal.ZERO))
			.from(INVENTORIES).join(SKUS).on(SKUS.ID.eq(INVENTORIES.SKU_ID))
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.eq(ACTIVE)));
	}

	private static Field<BigDecimal> skuAvailable() {
		return field(select(coalesce(sum(INVENTORIES.ON_HAND.minus(INVENTORIES.RESERVED)), BigDecimal.ZERO))
			.from(INVENTORIES).where(INVENTORIES.SKU_ID.eq(SKUS.ID)));
	}

	private static Condition keywordCondition(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return noCondition();
		}
		String k = keyword.strip();
		return PRODUCTS.NAME.containsIgnoreCase(k).or(PRODUCTS.BRAND.containsIgnoreCase(k));
	}

	private static Condition adminKeywordCondition(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return noCondition();
		}
		String k = keyword.strip();
		return keywordCondition(k)
			.orExists(selectOne().from(SKUS).where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID),
					SKUS.SKU_CODE.eq(k).or(SKUS.BARCODE.eq(k))));
	}

	private static List<Grade> toGrades(String[] codes) {
		if (codes == null) {
			return List.of();
		}
		return Arrays.stream(codes).map(ConditionGrade::valueOf).sorted().map(Grade::of).toList();
	}

	private static int toInt(BigDecimal value) {
		return value == null ? 0 : value.intValue();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
