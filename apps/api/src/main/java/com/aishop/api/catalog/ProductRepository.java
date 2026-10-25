package com.aishop.api.catalog;

import static com.aishop.api.jooq.Tables.CART_ITEMS;
import static com.aishop.api.jooq.Tables.CATEGORIES;
import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.LOCATIONS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.PRODUCT_IMAGES;
import static com.aishop.api.jooq.Tables.SELLERS;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.CatalogRequests.UpdateSku;
import com.aishop.api.catalog.CatalogViews.AdminProductDetail;
import com.aishop.api.catalog.CatalogViews.AdminProductRow;
import com.aishop.api.catalog.CatalogViews.AdminSku;
import com.aishop.api.catalog.CatalogViews.CategoryPathItem;
import com.aishop.api.catalog.CatalogViews.Grade;
import com.aishop.api.catalog.CatalogViews.Image;
import com.aishop.api.catalog.CatalogViews.ProductCard;
import com.aishop.api.catalog.CatalogViews.ReturnInfo;
import com.aishop.api.catalog.CatalogViews.ShopProductDetail;
import com.aishop.api.catalog.CatalogViews.ShopSku;
import com.aishop.api.catalog.CatalogViews.ListSku;
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
	private final WholesaleRepository wholesale;

	public ProductRepository(DSLContext dsl, JsonbMapper jsonb, WholesaleRepository wholesale) {
		this.dsl = dsl;
		this.jsonb = jsonb;
		this.wholesale = wholesale;
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
			.and(search.categoryIds() == null ? noCondition() : PRODUCTS.CATEGORY_ID.in(search.categoryIds()))
			// 땡처리관: 가장 싼 SKU가 정가보다 40% 이상 싼 상품
			.and(search.ddaengOnly()
					? maxListPrice.isNotNull()
						.and(minPrice.mul(100).le(maxListPrice.mul(100 - ShopSearch.DDAENG_RATE)))
					: noCondition());

		SortField<?>[] orderBy = switch (search.sort()) {
			case PRICE_ASC -> new SortField<?>[] { minPrice.asc(), PRODUCTS.ID.desc() };
			case PRICE_DESC -> new SortField<?>[] { minPrice.desc(), PRODUCTS.ID.desc() };
			case NEWEST -> new SortField<?>[] { PRODUCTS.ID.desc() };
			case BEST -> new SortField<?>[] { recentSoldQty().desc(), PRODUCTS.ID.desc() };
		};

		List<ProductCard> items = dsl
			.select(PRODUCTS.ID, PRODUCTS.NAME, PRODUCTS.BRAND, thumbnail, minPrice, maxListPrice, grades, available,
				PRODUCTS.BUSINESS_ONLY)
			.from(PRODUCTS)
			.where(condition)
			.orderBy(orderBy)
			.limit(search.size())
			.offset(search.offset())
			.fetch(r -> new ProductCard(
					r.value1(), r.value2(), r.value3(), r.value4(),
					r.value5() == null ? 0 : r.value5(), r.value6(),
					toGrades(r.value7()), toInt(r.value8()), r.value9()));

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
					p.getFreeShippingThreshold(), p.getBusinessOnly(), p.getImageReuseAllowed(), wholesale.find(productId),
					spec(p), returnInfo(p), categoryPath(p.getCategoryId()), findShopSkus(productId),
					findImages(productId)));
	}

	/** 최근 30일 동안 결제된 수량 (취소·결제대기 제외) */
	private Field<BigDecimal> recentSoldQty() {
		return field(select(coalesce(sum(ORDER_ITEMS.QTY), BigDecimal.ZERO))
			.from(ORDER_ITEMS)
			.join(SKUS).on(SKUS.ID.eq(ORDER_ITEMS.SKU_ID))
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID))
			.and(ORDER_ITEMS.STATUS.notIn("PENDING_PAYMENT", "CANCELLED"))
			.and(ORDER_ITEMS.CREATED_AT.gt(currentOffsetDateTime().minus(30))));
	}

	private static ProductSpec spec(ProductsRecord p) {
		return new ProductSpec(p.getOrigin(), p.getManufacturer(), p.getPackageSize(), p.getPackageWeightG());
	}

	private ReturnInfo returnInfo(ProductsRecord p) {
		var seller = dsl.select(SELLERS.RETURN_ZIPCODE, SELLERS.RETURN_ADDRESS)
			.from(SELLERS)
			.where(SELLERS.ID.eq(p.getSellerId()))
			.fetchOne();
		return new ReturnInfo(seller == null ? null : seller.value1(), seller == null ? null : seller.value2(),
				p.getShippingFee());
	}

	/** 최상위 카테고리부터 이 상품의 카테고리까지 (단계가 몇 개 안 되므로 위로 따라 올라간다) */
	private List<CategoryPathItem> categoryPath(Long categoryId) {
		List<CategoryPathItem> path = new ArrayList<>();
		Long current = categoryId;
		for (int depth = 0; current != null && depth < 10; depth++) {
			var row = dsl.select(CATEGORIES.ID, CATEGORIES.NAME, CATEGORIES.PARENT_ID)
				.from(CATEGORIES)
				.where(CATEGORIES.ID.eq(current))
				.fetchOne();
			if (row == null) {
				break;
			}
			path.addFirst(new CategoryPathItem(row.value1(), row.value2()));
			current = row.value3();
		}
		return path;
	}

	public void updateSpec(long productId, ProductSpec spec) {
		dsl.update(PRODUCTS)
			.set(PRODUCTS.ORIGIN, blankToNull(spec.origin()))
			.set(PRODUCTS.MANUFACTURER, blankToNull(spec.manufacturer()))
			.set(PRODUCTS.PACKAGE_SIZE, blankToNull(spec.packageSize()))
			.set(PRODUCTS.PACKAGE_WEIGHT_G, spec.packageWeightG())
			.where(PRODUCTS.ID.eq(productId))
			.execute();
	}

	private List<ShopSku> findShopSkus(long productId) {
		Field<BigDecimal> available = skuAvailable();
		return dsl.select(SKUS.ID, SKUS.OPTION_VALUES, SKUS.CONDITION_GRADE, SKUS.CONDITION_NOTE, SKUS.PRICE,
				SKUS.LIST_PRICE, SKUS.BUSINESS_PRICE, available)
			.from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(productId), SKUS.STATUS.eq(ACTIVE))
			.orderBy(SKUS.PRICE, SKUS.ID)
			.fetch(r -> new ShopSku(r.value1(), jsonb.toStringMap(r.value2()),
					Grade.of(ConditionGrade.valueOf(r.value3())), r.value4(), r.value5(), r.value6(), r.value7(),
					toInt(r.value8())));
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
					toInt(r.value9()), toInt(r.value10()), r.value11(), List.of()));

		// 목록에서 등급·판매가를 바로 바꿀 수 있게 상품별 SKU를 붙인다 (페이지의 상품만 한 번에 조회)
		Map<Long, List<ListSku>> skus = listSkus(items.stream().map(AdminProductRow::id).toList());
		items = items.stream()
			.map(r -> new AdminProductRow(r.id(), r.name(), r.brand(), r.categoryName(), r.status(), r.thumbnailUrl(),
					r.skuCount(), r.minPrice(), r.onHand(), r.reserved(), r.createdAt(),
					skus.getOrDefault(r.id(), List.of())))
			.toList();

		long total = dsl.fetchCount(PRODUCTS, condition);
		return new PageResponse<>(items, search.page(), search.size(), total);
	}

	private Map<Long, List<ListSku>> listSkus(List<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		return dsl.select(SKUS.PRODUCT_ID, SKUS.ID, SKUS.OPTION_VALUES, SKUS.CONDITION_GRADE, SKUS.PRICE)
			.from(SKUS)
			.where(SKUS.PRODUCT_ID.in(productIds), SKUS.STATUS.ne(DELETED_SKU))
			.orderBy(SKUS.ID)
			.fetchGroups(r -> r.value1(), r -> new ListSku(r.value2(), optionText(jsonb.toStringMap(r.value3())),
					Grade.of(ConditionGrade.valueOf(r.value4())), r.value5()));
	}

	private static String optionText(Map<String, String> options) {
		return options.isEmpty() ? null : options.entrySet().stream()
			.map(e -> e.getKey() + "=" + e.getValue())
			.collect(java.util.stream.Collectors.joining(", "));
	}

	public void updateSkuPrice(long skuId, long price) {
		dsl.update(SKUS).set(SKUS.PRICE, price).where(SKUS.ID.eq(skuId)).execute();
	}

	public void updateSkuGrade(long skuId, ConditionGrade grade) {
		dsl.update(SKUS).set(SKUS.CONDITION_GRADE, grade.name()).where(SKUS.ID.eq(skuId)).execute();
	}

	public Optional<AdminProductDetail> findAdminProduct(long sellerId, long productId) {
		return findProductRecord(sellerId, productId)
			.map(p -> new AdminProductDetail(
					p.getId(), p.getSellerId(), p.getName(), p.getBrand(), p.getModelName(), p.getCategoryId(),
					p.getDescription(), jsonb.toStringMap(p.getNoticeInfo()), p.getKcCertNo(), p.getShippingFee(),
					p.getFreeShippingThreshold(), ProductStatus.valueOf(p.getStatus()), p.getCreatedAt(),
					p.getUpdatedAt(), p.getBusinessOnly(), p.getImageReuseAllowed(), wholesale.find(productId),
					spec(p), findAdminSkus(productId), findImages(productId)));
	}

	private List<AdminSku> findAdminSkus(long productId) {
		Field<BigDecimal> onHand = field(select(coalesce(sum(INVENTORIES.ON_HAND), BigDecimal.ZERO))
			.from(INVENTORIES).where(INVENTORIES.SKU_ID.eq(SKUS.ID)));
		Field<BigDecimal> reserved = field(select(coalesce(sum(INVENTORIES.RESERVED), BigDecimal.ZERO))
			.from(INVENTORIES).where(INVENTORIES.SKU_ID.eq(SKUS.ID)));
		return dsl.select(SKUS.ID, SKUS.SKU_CODE, SKUS.OPTION_VALUES, SKUS.CONDITION_GRADE, SKUS.CONDITION_NOTE,
				SKUS.BARCODE, SKUS.PRICE, SKUS.LIST_PRICE, SKUS.BUSINESS_PRICE, SKUS.STATUS, onHand, reserved)
			.from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(productId), SKUS.STATUS.ne(DELETED_SKU))
			.orderBy(SKUS.ID)
			.fetch(r -> new AdminSku(r.value1(), r.value2(), jsonb.toStringMap(r.value3()),
					Grade.of(ConditionGrade.valueOf(r.value4())), r.value5(), r.value6(), r.value7(), r.value8(),
					r.value9(), SkuStatus.valueOf(r.value10()), toInt(r.value11()), toInt(r.value12())));
	}

	public Optional<ProductsRecord> findProductRecord(long sellerId, long productId) {
		return dsl.selectFrom(PRODUCTS)
			.where(PRODUCTS.ID.eq(productId), PRODUCTS.SELLER_ID.eq(sellerId),
					PRODUCTS.STATUS.ne(ProductStatus.DELETED.name()))
			.fetchOptional();
	}

	/** 주문(입금 대기·출고 전)이 잡아 둔 재고. 0보다 크면 삭제하지 않는다 */
	public int reservedOfProduct(long productId) {
		return dsl.select(coalesce(sum(INVENTORIES.RESERVED), BigDecimal.ZERO))
			.from(INVENTORIES).join(SKUS).on(SKUS.ID.eq(INVENTORIES.SKU_ID))
			.where(SKUS.PRODUCT_ID.eq(productId))
			.fetchOne(0, BigDecimal.class).intValue();
	}

	public int reservedOfSku(long skuId) {
		return dsl.select(coalesce(sum(INVENTORIES.RESERVED), BigDecimal.ZERO))
			.from(INVENTORIES)
			.where(INVENTORIES.SKU_ID.eq(skuId))
			.fetchOne(0, BigDecimal.class).intValue();
	}

	public int countLiveSkus(long productId) {
		return dsl.fetchCount(SKUS, SKUS.PRODUCT_ID.eq(productId).and(SKUS.STATUS.ne(DELETED_SKU)));
	}

	/**
	 * 삭제: 행은 남기고 상태만 DELETED로 바꾼다 (지난 주문·재고 기록이 이 상품을 가리키므로).
	 * 고객 장바구니에 담긴 것은 지운다.
	 */
	public void softDeleteProduct(long productId) {
		dsl.deleteFrom(CART_ITEMS)
			.where(CART_ITEMS.SKU_ID.in(select(SKUS.ID).from(SKUS).where(SKUS.PRODUCT_ID.eq(productId))))
			.execute();
		dsl.update(SKUS).set(SKUS.STATUS, DELETED_SKU).where(SKUS.PRODUCT_ID.eq(productId)).execute();
		updateProductStatus(productId, ProductStatus.DELETED);
	}

	public void softDeleteSku(long skuId) {
		dsl.deleteFrom(CART_ITEMS).where(CART_ITEMS.SKU_ID.eq(skuId)).execute();
		dsl.update(SKUS).set(SKUS.STATUS, DELETED_SKU).where(SKUS.ID.eq(skuId)).execute();
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
			.set(SKUS.CONDITION_GRADE, s.conditionGrade() == null ? SKUS.CONDITION_GRADE : DSL.val(s.conditionGrade().name()))
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
