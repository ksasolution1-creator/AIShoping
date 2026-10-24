package com.aishop.api.inventory;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.inventory.InventoryViews.AdjustStock;
import com.aishop.api.inventory.InventoryViews.Location;
import com.aishop.api.inventory.InventoryViews.Movement;
import com.aishop.api.inventory.InventoryViews.PurchaseDetail;
import com.aishop.api.inventory.InventoryViews.PurchaseForm;
import com.aishop.api.inventory.InventoryViews.PurchaseSummary;
import com.aishop.api.inventory.InventoryViews.StockRow;
import com.aishop.api.inventory.InventoryViews.Supplier;
import com.aishop.api.inventory.InventoryViews.SupplierForm;
import com.aishop.api.inventory.InventoryViews.TransferStock;

/** 운영자 재고·매입: 재고 현황, 조정·이동, 바코드 조회, 매입처, 매입서(입고) */
@RestController
@RequestMapping("/api/admin")
public class AdminInventoryController {

	private final InventoryService service;
	private final AdminContext admin;

	public AdminInventoryController(InventoryService service, AdminContext admin) {
		this.service = service;
		this.admin = admin;
	}

	@GetMapping("/locations")
	public List<Location> locations() {
		return service.locations(admin.sellerId());
	}

	@GetMapping("/inventory")
	public PageResponse<StockRow> stock(@RequestParam(required = false) String q,
			@RequestParam(required = false) Long locationId, @RequestParam(required = false) Integer maxAvailable,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		return service.stock(admin.sellerId(), q, locationId, maxAvailable, Math.max(page, 0),
				Math.clamp(size, 1, 500));
	}

	@GetMapping("/inventory/lookup")
	public StockRow lookup(@RequestParam String code) {
		return service.lookup(admin.sellerId(), code);
	}

	@GetMapping("/inventory/{skuId}/movements")
	public List<Movement> movements(@PathVariable long skuId) {
		return service.movements(admin.sellerId(), skuId);
	}

	@PostMapping("/inventory/adjust")
	public StockRow adjust(@Valid @RequestBody AdjustStock body) {
		return service.adjust(admin.sellerId(), admin.user().id(), body);
	}

	@PostMapping("/inventory/transfer")
	public StockRow transfer(@Valid @RequestBody TransferStock body) {
		return service.transfer(admin.sellerId(), admin.user().id(), body);
	}

	// ------------------------------------------------------------------

	@GetMapping("/suppliers")
	public List<Supplier> suppliers(@RequestParam(defaultValue = "false") boolean all) {
		return service.suppliers(admin.sellerId(), all);
	}

	@PostMapping("/suppliers")
	@ResponseStatus(HttpStatus.CREATED)
	public Map<String, Long> createSupplier(@Valid @RequestBody SupplierForm body) {
		return Map.of("id", service.createSupplier(admin.sellerId(), body));
	}

	@PutMapping("/suppliers/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierForm body) {
		service.updateSupplier(admin.sellerId(), id, body);
	}

	// ------------------------------------------------------------------

	@GetMapping("/purchases")
	public PageResponse<PurchaseSummary> purchases(@RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
		return service.purchaseList(admin.sellerId(), status == null || status.isBlank() ? null : status, q,
				Math.max(page, 0), 30);
	}

	@GetMapping("/purchases/{id}")
	public PurchaseDetail purchase(@PathVariable long id) {
		return service.purchase(admin.sellerId(), id);
	}

	@PostMapping("/purchases")
	@ResponseStatus(HttpStatus.CREATED)
	public PurchaseDetail createPurchase(@Valid @RequestBody PurchaseForm body) {
		return service.createPurchase(admin.sellerId(), admin.user().id(), body);
	}

	@PutMapping("/purchases/{id}")
	public PurchaseDetail updatePurchase(@PathVariable long id, @Valid @RequestBody PurchaseForm body) {
		return service.updatePurchase(admin.sellerId(), id, body);
	}

	@PostMapping("/purchases/{id}/receive")
	public PurchaseDetail receive(@PathVariable long id) {
		return service.receive(admin.sellerId(), admin.user().id(), id);
	}

	@PostMapping("/purchases/{id}/cancel")
	public PurchaseDetail cancel(@PathVariable long id) {
		return service.cancelPurchase(admin.sellerId(), id);
	}

}
