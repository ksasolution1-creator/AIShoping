package com.aishop.api.inventory;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.inventory.InventoryViews.AdjustStock;
import com.aishop.api.inventory.InventoryViews.Location;
import com.aishop.api.inventory.InventoryViews.Movement;
import com.aishop.api.inventory.InventoryViews.PurchaseDetail;
import com.aishop.api.inventory.InventoryViews.PurchaseForm;
import com.aishop.api.inventory.InventoryViews.PurchaseItem;
import com.aishop.api.inventory.InventoryViews.PurchaseItemForm;
import com.aishop.api.inventory.InventoryViews.PurchaseSummary;
import com.aishop.api.inventory.InventoryViews.StockRow;
import com.aishop.api.inventory.InventoryViews.Supplier;
import com.aishop.api.inventory.InventoryViews.SupplierForm;
import com.aishop.api.inventory.InventoryViews.TransferStock;
import com.aishop.api.jooq.tables.records.PurchasesRecord;

/** 재고 현황·조정·이동과 매입(입고) 처리 */
@Service
public class InventoryService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final String PURCHASE_NO_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

	private final InventoryRepository inventory;
	private final PurchaseRepository purchases;
	private final SecureRandom random = new SecureRandom();

	public InventoryService(InventoryRepository inventory, PurchaseRepository purchases) {
		this.inventory = inventory;
		this.purchases = purchases;
	}

	// ------------------------------------------------------------------
	// 재고
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<Location> locations(long sellerId) {
		return inventory.findLocations(sellerId);
	}

	@Transactional(readOnly = true)
	public PageResponse<StockRow> stock(long sellerId, String keyword, Long locationId, Integer maxAvailable,
			int page, int size) {
		return inventory.findStock(sellerId, keyword, locationId, maxAvailable, page, size);
	}

	/** 바코드 스캔: SKU코드나 바코드로 한 개를 찾는다 */
	@Transactional(readOnly = true)
	public StockRow lookup(long sellerId, String code) {
		if (code == null || code.isBlank()) {
			throw ApiException.badRequest("EMPTY_CODE", "바코드를 입력해 주세요.");
		}
		long skuId = inventory.findSkuIdByCode(sellerId, code.strip())
			.orElseThrow(() -> ApiException.notFound("바코드 '" + code.strip() + "' 상품"));
		return requireStockRow(sellerId, skuId);
	}

	@Transactional(readOnly = true)
	public List<Movement> movements(long sellerId, long skuId) {
		requireSku(sellerId, skuId);
		return inventory.findMovements(skuId, 200);
	}

	@Transactional
	public StockRow adjust(long sellerId, long actorId, AdjustStock request) {
		requireSku(sellerId, request.skuId());
		requireLocation(sellerId, request.locationId());
		if (request.qty() == 0) {
			throw ApiException.badRequest("ZERO_QTY", "변경 수량은 0이 될 수 없습니다.");
		}
		if (!inventory.changeOnHand(request.skuId(), request.locationId(), request.qty())) {
			throw ApiException.conflict("INSUFFICIENT_STOCK", "보유 재고나 주문 예약 수량보다 많이 줄일 수 없습니다.");
		}
		inventory.insertMovement(request.skuId(), request.locationId(), request.qty(), "ADJUST", null, null,
				request.memo().strip(), actorId);
		return requireStockRow(sellerId, request.skuId());
	}

	/** 위치 간 이동 (창고 → 매장 등). 출발지에서 빼고 도착지에 더한다 */
	@Transactional
	public StockRow transfer(long sellerId, long actorId, TransferStock request) {
		requireSku(sellerId, request.skuId());
		requireLocation(sellerId, request.fromLocationId());
		requireLocation(sellerId, request.toLocationId());
		if (request.fromLocationId() == request.toLocationId()) {
			throw ApiException.badRequest("SAME_LOCATION", "출발지와 도착지가 같습니다.");
		}
		if (!inventory.changeOnHand(request.skuId(), request.fromLocationId(), -request.qty())) {
			throw ApiException.conflict("INSUFFICIENT_STOCK", "출발지의 판매가능 재고보다 많이 옮길 수 없습니다.");
		}
		inventory.changeOnHand(request.skuId(), request.toLocationId(), request.qty());
		String memo = isBlank(request.memo()) ? null : request.memo().strip();
		inventory.insertMovement(request.skuId(), request.fromLocationId(), -request.qty(), "TRANSFER_OUT", null,
				null, memo, actorId);
		inventory.insertMovement(request.skuId(), request.toLocationId(), request.qty(), "TRANSFER_IN", null, null,
				memo, actorId);
		return requireStockRow(sellerId, request.skuId());
	}

	// ------------------------------------------------------------------
	// 매입처
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<Supplier> suppliers(long sellerId, boolean includeInactive) {
		return purchases.findSuppliers(sellerId, includeInactive);
	}

	@Transactional
	public long createSupplier(long sellerId, SupplierForm form) {
		return purchases.insertSupplier(sellerId, form);
	}

	@Transactional
	public void updateSupplier(long sellerId, long supplierId, SupplierForm form) {
		if (!purchases.supplierBelongsTo(sellerId, supplierId)) {
			throw ApiException.notFound("매입처");
		}
		purchases.updateSupplier(supplierId, form);
	}

	/** 매입처 삭제. 매입서에 쓰인 곳은 기록이 남아야 하므로 '거래 중지'로 바꾸도록 안내한다 */
	@Transactional
	public void deleteSupplier(long sellerId, long supplierId) {
		if (!purchases.supplierBelongsTo(sellerId, supplierId)) {
			throw ApiException.notFound("매입처");
		}
		if (purchases.supplierUsed(supplierId)) {
			throw ApiException.conflict("SUPPLIER_USED", "매입 기록이 있는 매입처는 지울 수 없습니다. '거래 중지'로 바꿔 주세요.");
		}
		purchases.deleteSupplier(supplierId);
	}

	// ------------------------------------------------------------------
	// 매입서: 작성(DRAFT) → 입고(RECEIVED) 시 재고가 늘어난다
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<PurchaseSummary> purchaseList(long sellerId, String status, String keyword, int page,
			int size) {
		return purchases.findPurchases(sellerId, status, keyword, page, size);
	}

	@Transactional(readOnly = true)
	public PurchaseDetail purchase(long sellerId, long purchaseId) {
		return purchases.findPurchase(sellerId, purchaseId).orElseThrow(() -> ApiException.notFound("매입서"));
	}

	@Transactional
	public PurchaseDetail createPurchase(long sellerId, long actorId, PurchaseForm form) {
		validatePurchaseForm(sellerId, form);
		long id = purchases.insertPurchase(sellerId, newPurchaseNo(), form.supplierId(), form.locationId(),
				purchasedOn(form), form.memo(), actorId);
		purchases.replaceItems(id, form.items());
		return purchase(sellerId, id);
	}

	@Transactional
	public PurchaseDetail updatePurchase(long sellerId, long purchaseId, PurchaseForm form) {
		requireDraft(sellerId, purchaseId);
		validatePurchaseForm(sellerId, form);
		purchases.updatePurchaseHeader(purchaseId, form.supplierId(), form.locationId(), purchasedOn(form),
				form.memo());
		purchases.replaceItems(purchaseId, form.items());
		return purchase(sellerId, purchaseId);
	}

	/** 입고 확정: 품목 수량만큼 재고를 늘리고, 매입단가를 SKU의 최근 매입단가로 남긴다 */
	@Transactional
	public PurchaseDetail receive(long sellerId, long actorId, long purchaseId) {
		PurchasesRecord purchase = requireDraft(sellerId, purchaseId);
		List<PurchaseItem> items = purchases.findItems(purchaseId);
		if (items.isEmpty()) {
			throw ApiException.badRequest("EMPTY_PURCHASE", "매입 상품이 없습니다.");
		}
		for (PurchaseItem item : items) {
			inventory.changeOnHand(item.skuId(), purchase.getLocationId(), item.qty());
			inventory.insertMovement(item.skuId(), purchase.getLocationId(), item.qty(), "PURCHASE_IN", "PURCHASE",
					purchaseId, purchase.getPurchaseNo(), actorId);
			purchases.updateSkuCost(item.skuId(), item.unitCost());
		}
		purchases.markReceived(purchaseId, actorId);
		return purchase(sellerId, purchaseId);
	}

	@Transactional
	public PurchaseDetail cancelPurchase(long sellerId, long purchaseId) {
		requireDraft(sellerId, purchaseId);
		purchases.markCancelled(purchaseId);
		return purchase(sellerId, purchaseId);
	}

	// ------------------------------------------------------------------

	private void validatePurchaseForm(long sellerId, PurchaseForm form) {
		requireLocation(sellerId, form.locationId());
		if (form.supplierId() != null && !purchases.supplierBelongsTo(sellerId, form.supplierId())) {
			throw ApiException.notFound("매입처");
		}
		Set<Long> seen = new HashSet<>();
		for (PurchaseItemForm item : form.items()) {
			requireSku(sellerId, item.skuId());
			if (!seen.add(item.skuId())) {
				throw ApiException.badRequest("DUPLICATE_SKU", "같은 상품이 두 줄 이상 들어 있습니다. 한 줄로 합쳐 주세요.");
			}
		}
	}

	private PurchasesRecord requireDraft(long sellerId, long purchaseId) {
		PurchasesRecord purchase = purchases.lockPurchase(sellerId, purchaseId)
			.orElseThrow(() -> ApiException.notFound("매입서"));
		if (!"DRAFT".equals(purchase.getStatus())) {
			throw ApiException.conflict("PURCHASE_NOT_DRAFT", "이미 입고했거나 취소한 매입서는 바꿀 수 없습니다.");
		}
		return purchase;
	}

	private void requireSku(long sellerId, long skuId) {
		if (!inventory.skuBelongsTo(sellerId, skuId)) {
			throw ApiException.notFound("SKU");
		}
	}

	private void requireLocation(long sellerId, long locationId) {
		if (!inventory.locationBelongsTo(sellerId, locationId)) {
			throw ApiException.notFound("재고 위치");
		}
	}

	private StockRow requireStockRow(long sellerId, long skuId) {
		return inventory.findStockRow(sellerId, skuId).orElseThrow(() -> ApiException.notFound("SKU"));
	}

	private static LocalDate purchasedOn(PurchaseForm form) {
		return form.purchasedOn() != null ? form.purchasedOn() : LocalDate.now(SEOUL);
	}

	/** 매입번호: PO-날짜-임의 5자리 (예: PO-20261024-7KQ2M) */
	private String newPurchaseNo() {
		String date = LocalDate.now(SEOUL).format(DateTimeFormatter.BASIC_ISO_DATE);
		for (int attempt = 0; attempt < 5; attempt++) {
			StringBuilder sb = new StringBuilder("PO-").append(date).append('-');
			for (int i = 0; i < 5; i++) {
				sb.append(PURCHASE_NO_CHARS.charAt(random.nextInt(PURCHASE_NO_CHARS.length())));
			}
			if (!purchases.purchaseNoExists(sb.toString())) {
				return sb.toString();
			}
		}
		throw new IllegalStateException("매입번호 생성 실패");
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
