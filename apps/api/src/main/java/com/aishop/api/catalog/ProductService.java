package com.aishop.api.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.aishop.api.catalog.CatalogRequests.AdjustStock;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.CatalogRequests.UpdateSku;
import com.aishop.api.catalog.CatalogViews.AdminProductDetail;
import com.aishop.api.catalog.CatalogViews.AdminProductRow;
import com.aishop.api.catalog.CatalogViews.Image;
import com.aishop.api.catalog.CatalogViews.ProductCard;
import com.aishop.api.catalog.CatalogViews.ShopProductDetail;
import com.aishop.api.common.storage.FileStorage;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.inventory.InventoryRepository;

@Service
public class ProductService {

	private final ProductRepository products;
	private final CategoryRepository categories;
	private final InventoryRepository inventory;
	private final FileStorage fileStorage;
	private final WholesaleRepository wholesale;

	public ProductService(ProductRepository products, CategoryRepository categories, InventoryRepository inventory,
			FileStorage fileStorage, WholesaleRepository wholesale) {
		this.products = products;
		this.categories = categories;
		this.inventory = inventory;
		this.fileStorage = fileStorage;
		this.wholesale = wholesale;
	}

	// ------------------------------------------------------------------
	// 쇼핑몰
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<ProductCard> searchShop(String keyword, Long categoryId, ProductSort sort, int page, int size) {
		return searchShop(keyword, categoryId, sort, false, page, size);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProductCard> searchShop(String keyword, Long categoryId, ProductSort sort, boolean ddaengOnly,
			int page, int size) {
		List<Long> categoryIds = categoryId == null ? null : categories.findSelfAndDescendantIds(categoryId);
		return products.findShopProducts(new ShopSearch(keyword, categoryIds, sort, ddaengOnly, page, size));
	}

	@Transactional
	public AdminProductDetail updateSpec(long sellerId, long productId, ProductSpec spec) {
		requireProduct(sellerId, productId);
		products.updateSpec(productId, spec);
		return getAdminProduct(sellerId, productId);
	}

	@Transactional(readOnly = true)
	public ShopProductDetail getShopProduct(long productId) {
		return products.findShopProduct(productId).orElseThrow(() -> ApiException.notFound("상품"));
	}

	// ------------------------------------------------------------------
	// 관리자
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<AdminProductRow> searchAdmin(long sellerId, AdminSearch search) {
		return products.findAdminProducts(sellerId, search);
	}

	@Transactional(readOnly = true)
	public AdminProductDetail getAdminProduct(long sellerId, long productId) {
		return products.findAdminProduct(sellerId, productId).orElseThrow(() -> ApiException.notFound("상품"));
	}

	/** 도매 구매조건 저장. 수량별 할인은 수량이 클수록 할인율도 커야 한다 */
	@Transactional
	public AdminProductDetail updateWholesale(long sellerId, long productId, WholesaleTerms terms) {
		requireProduct(sellerId, productId);
		int lastQty = 0;
		int lastBp = 0;
		for (WholesaleTerms.PriceTier tier : terms.tiers()) {
			if (tier.minQty() == lastQty) {
				throw ApiException.badRequest("DUPLICATE_TIER", tier.minQty() + "개 할인이 두 번 들어 있습니다.");
			}
			if (tier.discountBp() <= lastBp) {
				throw ApiException.badRequest("TIER_ORDER", "많이 살수록 할인율이 커지도록 정해 주세요.");
			}
			lastQty = tier.minQty();
			lastBp = tier.discountBp();
		}
		wholesale.save(productId, terms);
		return getAdminProduct(sellerId, productId);
	}

	/** 상품과 SKU를 만들고 초기 재고를 기본 창고에 넣는다. 상품은 준비중(DRAFT)으로 시작한다. */
	@Transactional
	public long createProduct(long sellerId, CreateProduct request) {
		validateCategory(request.product().categoryId());
		validateSkuCodes(request.skus());

		long productId = products.insertProduct(sellerId, request.product());
		long warehouseId = defaultWarehouse(sellerId);

		int seq = 1;
		for (CreateSku sku : request.skus()) {
			String skuCode = isBlank(sku.skuCode()) ? "P" + productId + "-" + seq : sku.skuCode().strip();
			long skuId = products.insertSku(productId, skuCode, sku);
			receiveInitialStock(skuId, warehouseId, sku.initialStock(), "상품 등록 시 초기 재고");
			seq++;
		}
		return productId;
	}

	@Transactional
	public void updateProduct(long sellerId, long productId, ProductFields fields) {
		requireProduct(sellerId, productId);
		validateCategory(fields.categoryId());
		products.updateProduct(productId, fields);
	}

	@Transactional
	public void changeStatus(long sellerId, long productId, ProductStatus status) {
		requireProduct(sellerId, productId);
		if (status == ProductStatus.ON_SALE && !products.hasActiveSku(productId)) {
			throw ApiException.badRequest("NO_ACTIVE_SKU", "판매중인 SKU가 하나 이상 있어야 판매를 시작할 수 있습니다.");
		}
		products.updateProductStatus(productId, status);
	}

	@Transactional
	public long addSku(long sellerId, long productId, CreateSku sku) {
		requireProduct(sellerId, productId);
		validateSkuCodes(List.of(sku));
		String skuCode = isBlank(sku.skuCode())
				? "P" + productId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
				: sku.skuCode().strip();
		long skuId = products.insertSku(productId, skuCode, sku);
		receiveInitialStock(skuId, defaultWarehouse(sellerId), sku.initialStock(), "SKU 추가 시 초기 재고");
		return skuId;
	}

	@Transactional
	public void updateSku(long sellerId, long skuId, UpdateSku request) {
		products.findProductIdOfSku(sellerId, skuId).orElseThrow(() -> ApiException.notFound("SKU"));
		products.updateSku(skuId, request);
	}

	/** 목록에서 상태등급만 바로 바꾼다 */
	@Transactional
	public void changeSkuGrade(long sellerId, long skuId, ConditionGrade grade) {
		products.findProductIdOfSku(sellerId, skuId).orElseThrow(() -> ApiException.notFound("SKU"));
		products.updateSkuGrade(skuId, grade);
	}

	/** 상품 삭제. 아직 처리 중인 주문(입금 대기·출고 전)이 잡아 둔 재고가 있으면 막는다 */
	@Transactional
	public void deleteProduct(long sellerId, long productId) {
		requireProduct(sellerId, productId);
		int reserved = products.reservedOfProduct(productId);
		if (reserved > 0) {
			throw ApiException.conflict("HAS_OPEN_ORDERS",
					"처리 중인 주문이 " + reserved + "개 잡혀 있어 삭제할 수 없습니다. 주문을 처리하거나 먼저 '숨김'으로 바꿔 주세요.");
		}
		products.softDeleteProduct(productId);
	}

	/** SKU 삭제. 상품에 SKU가 하나만 남으면 상품을 삭제하도록 안내한다 */
	@Transactional
	public void deleteSku(long sellerId, long skuId) {
		long productId = products.findProductIdOfSku(sellerId, skuId).orElseThrow(() -> ApiException.notFound("SKU"));
		if (products.countLiveSkus(productId) <= 1) {
			throw ApiException.badRequest("LAST_SKU", "마지막 판매 단위는 지울 수 없습니다. 상품 자체를 삭제해 주세요.");
		}
		int reserved = products.reservedOfSku(skuId);
		if (reserved > 0) {
			throw ApiException.conflict("HAS_OPEN_ORDERS",
					"처리 중인 주문이 " + reserved + "개 잡혀 있어 삭제할 수 없습니다. 먼저 판매를 '중지'로 바꿔 주세요.");
		}
		products.softDeleteSku(skuId);
	}

	/** 목록에서 판매가만 바로 바꾼다 */
	@Transactional
	public void changeSkuPrice(long sellerId, long skuId, long price) {
		products.findProductIdOfSku(sellerId, skuId).orElseThrow(() -> ApiException.notFound("SKU"));
		products.updateSkuPrice(skuId, price);
	}

	/** 재고 수동 조정. qty가 양수면 입고, 음수면 차감 */
	@Transactional
	public void adjustStock(long sellerId, long skuId, AdjustStock request) {
		products.findProductIdOfSku(sellerId, skuId).orElseThrow(() -> ApiException.notFound("SKU"));
		if (request.qty() == 0) {
			throw ApiException.badRequest("ZERO_QTY", "변경 수량은 0이 될 수 없습니다.");
		}
		long locationId = request.locationId() != null ? request.locationId() : defaultWarehouse(sellerId);
		if (!products.locationBelongsTo(sellerId, locationId)) {
			throw ApiException.notFound("재고 위치");
		}
		if (!inventory.changeOnHand(skuId, locationId, request.qty())) {
			throw ApiException.conflict("INSUFFICIENT_STOCK", "보유 재고나 주문 예약 수량보다 많이 줄일 수 없습니다.");
		}
		inventory.insertMovement(skuId, locationId, request.qty(), "ADJUST", null, null,
				isBlank(request.memo()) ? "수동 조정" : request.memo().strip(), null);
	}

	@Transactional
	public Image addImage(long sellerId, long productId, Long skuId, MultipartFile file) {
		requireProduct(sellerId, productId);
		if (skuId != null) {
			long owner = products.findProductIdOfSku(sellerId, skuId).orElseThrow(() -> ApiException.notFound("SKU"));
			if (owner != productId) {
				throw ApiException.badRequest("SKU_MISMATCH", "이 상품의 SKU가 아닙니다.");
			}
		}
		String url = fileStorage.store("products/" + productId, file);
		// DB 저장이 실패하면 올린 파일도 지운다
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					fileStorage.delete(url);
				}
			}
		});
		long imageId = products.insertImage(productId, skuId, url);
		return products.findImages(productId).stream()
			.filter(i -> i.id() == imageId)
			.findFirst()
			.orElseThrow();
	}

	@Transactional
	public void deleteImage(long sellerId, long productId, long imageId) {
		requireProduct(sellerId, productId);
		String url = products.deleteImage(productId, imageId).orElseThrow(() -> ApiException.notFound("사진"));
		// 커밋된 뒤에만 실제 파일을 지운다
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				fileStorage.delete(url);
			}
		});
	}

	// ------------------------------------------------------------------

	private void receiveInitialStock(long skuId, long warehouseId, int qty, String memo) {
		if (qty > 0) {
			inventory.changeOnHand(skuId, warehouseId, qty);
			inventory.insertMovement(skuId, warehouseId, qty, "ADJUST", null, null, memo, null);
		}
	}

	private long defaultWarehouse(long sellerId) {
		return products.findDefaultWarehouseId(sellerId)
			.orElseThrow(() -> ApiException.conflict("NO_WAREHOUSE", "기본 창고가 없습니다. 창고를 먼저 등록해 주세요."));
	}

	private void requireProduct(long sellerId, long productId) {
		products.findProductRecord(sellerId, productId).orElseThrow(() -> ApiException.notFound("상품"));
	}

	private void validateCategory(Long categoryId) {
		if (categoryId != null && !categories.exists(categoryId)) {
			throw ApiException.badRequest("INVALID_CATEGORY", "존재하지 않는 카테고리입니다.");
		}
	}

	private void validateSkuCodes(List<CreateSku> skus) {
		Set<String> seen = new HashSet<>();
		for (CreateSku sku : skus) {
			if (isBlank(sku.skuCode())) {
				continue;
			}
			String code = sku.skuCode().strip();
			if (!seen.add(code) || products.skuCodeExists(code)) {
				throw ApiException.conflict("DUPLICATE_SKU_CODE", "이미 사용 중인 SKU 코드입니다: " + code);
			}
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
