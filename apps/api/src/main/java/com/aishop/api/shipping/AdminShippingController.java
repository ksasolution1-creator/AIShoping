package com.aishop.api.shipping;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.shipping.ShippingViews.BulkResult;
import com.aishop.api.shipping.ShippingViews.ImportResult;
import com.aishop.api.shipping.ShippingViews.OrderNos;
import com.aishop.api.shipping.ShippingViews.ReadyOrder;
import com.aishop.api.shipping.ShippingViews.SenderSettings;
import com.aishop.api.shipping.ShippingViews.ShipmentRow;

/** 운영자 배송·송장: 발주확인 → 송장 엑셀 내려받기 → 송장번호 엑셀 올리기(일괄 출고) → 배송 현황 */
@RestController
@RequestMapping("/api/admin/shipping")
public class AdminShippingController {

	private static final MediaType XLSX = MediaType
		.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ShippingService service;
	private final AdminContext admin;

	public AdminShippingController(ShippingService service, AdminContext admin) {
		this.service = service;
		this.admin = admin;
	}

	@GetMapping("/new-orders")
	public List<ReadyOrder> newOrders() {
		return service.newOrders(admin.sellerId());
	}

	@GetMapping("/ready")
	public List<ReadyOrder> ready() {
		return service.readyOrders(admin.sellerId());
	}

	@PostMapping("/accept")
	public BulkResult accept(@Valid @RequestBody OrderNos body) {
		return service.acceptAll(admin.sellerId(), body.orderNos());
	}

	@PostMapping("/export")
	public ResponseEntity<byte[]> export(@Valid @RequestBody OrderNos body, HttpServletRequest request) {
		byte[] file = service.exportSheet(admin.sellerId(), admin.user().id(), body.orderNos(), request.getRemoteAddr());
		String name = "송장_" + LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
				+ ".xlsx";
		return ResponseEntity.ok()
			.contentType(XLSX)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(file);
	}

	@PostMapping("/import")
	public ImportResult importTracking(@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) Long carrierId) {
		return service.importTracking(admin.sellerId(), admin.user().id(), file, carrierId);
	}

	@GetMapping("/shipments")
	public PageResponse<ShipmentRow> shipments(@RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
		Set<String> statuses = switch (status == null ? "" : status) {
			case "IN_TRANSIT" -> Set.of("SHIPPED", "IN_TRANSIT", "OUT_FOR_DELIVERY");
			case "DELIVERED" -> Set.of("DELIVERED");
			default -> null;
		};
		return service.shipments(admin.sellerId(), statuses, q, Math.max(page, 0));
	}

	@PostMapping("/shipments/{id}/delivered")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delivered(@PathVariable long id) {
		service.markDelivered(admin.sellerId(), id);
	}

	@GetMapping("/sender")
	public SenderSettings sender() {
		return service.senderSettings(admin.sellerId());
	}

	@PutMapping("/sender")
	public SenderSettings updateSender(@Valid @RequestBody SenderSettings body) {
		return service.updateSenderSettings(admin.sellerId(), body);
	}

}
