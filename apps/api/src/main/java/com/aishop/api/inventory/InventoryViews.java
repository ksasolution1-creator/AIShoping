package com.aishop.api.inventory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.aishop.api.catalog.CatalogViews.Grade;

/** 재고·매입 화면의 요청과 응답 */
public final class InventoryViews {

	private InventoryViews() {
	}

	public record Location(long id, String name, String type) {
	}

	public record LocationStock(long locationId, String locationName, int onHand, int reserved) {
	}

	/** 재고 현황 한 줄 (SKU 단위) */
	public record StockRow(
			long skuId,
			String skuCode,
			String barcode,
			long productId,
			String productName,
			String optionText,
			Grade grade,
			long price,
			Long cost,
			int onHand,
			int reserved,
			List<LocationStock> locations) {

		public int available() {
			return onHand - reserved;
		}

	}

	public record Movement(
			long id,
			OffsetDateTime createdAt,
			String locationName,
			int qty,
			String type,
			String refType,
			Long refId,
			String memo,
			String actorName) {
	}

	/** 재고 조정: qty가 양수면 늘리고 음수면 줄인다 */
	public record AdjustStock(
			@NotNull long skuId,
			@NotNull long locationId,
			@Min(-100_000) @Max(100_000) int qty,
			@NotBlank(message = "조정 사유를 입력해 주세요.") @Size(max = 200) String memo) {
	}

	public record TransferStock(
			@NotNull long skuId,
			@NotNull long fromLocationId,
			@NotNull long toLocationId,
			@Min(value = 1, message = "옮길 수량을 입력해 주세요.") @Max(100_000) int qty,
			@Size(max = 200) String memo) {
	}

	// ------------------------------------------------------------------
	// 매입처
	// ------------------------------------------------------------------

	public record Supplier(long id, String name, String businessNo, String contactName, String phone, String memo,
			boolean active) {
	}

	public record SupplierForm(
			@NotBlank(message = "매입처 이름을 입력해 주세요.") @Size(max = 100) String name,
			@Size(max = 20) String businessNo,
			@Size(max = 50) String contactName,
			@Size(max = 30) String phone,
			@Size(max = 500) String memo,
			Boolean active) {
	}

	// ------------------------------------------------------------------
	// 매입서
	// ------------------------------------------------------------------

	public record PurchaseSummary(
			long id,
			String purchaseNo,
			LocalDate purchasedOn,
			String status,
			String supplierName,
			String locationName,
			int itemCount,
			int totalQty,
			long totalCost) {
	}

	public record PurchaseItem(long skuId, String skuCode, String productName, String optionText, Grade grade,
			long price, int qty, long unitCost) {
	}

	public record PurchaseDetail(
			long id,
			String purchaseNo,
			LocalDate purchasedOn,
			String status,
			Long supplierId,
			String supplierName,
			long locationId,
			String locationName,
			String memo,
			long totalCost,
			OffsetDateTime receivedAt,
			List<PurchaseItem> items) {
	}

	public record PurchaseItemForm(
			@NotNull long skuId,
			@Min(value = 1, message = "수량은 1개 이상이어야 합니다.") @Max(1_000_000) int qty,
			@PositiveOrZero(message = "매입단가는 0원 이상이어야 합니다.") long unitCost) {
	}

	public record PurchaseForm(
			Long supplierId,
			@NotNull(message = "입고 위치를 선택해 주세요.") Long locationId,
			LocalDate purchasedOn,
			@Size(max = 500) String memo,
			@NotEmpty(message = "매입 상품을 한 개 이상 넣어 주세요.") @Size(max = 300) List<@Valid PurchaseItemForm> items) {
	}

}
