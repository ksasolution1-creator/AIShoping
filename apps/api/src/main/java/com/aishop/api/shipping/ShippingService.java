package com.aishop.api.shipping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.aishop.api.common.crypto.FieldCipher;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.jooq.tables.records.OrdersRecord;
import com.aishop.api.jooq.tables.records.SellersRecord;
import com.aishop.api.order.OrderRepository.CarrierOption;
import com.aishop.api.order.OrderService;
import com.aishop.api.shipping.ShippingRepository.OrderWithItems;
import com.aishop.api.shipping.ShippingViews.BulkResult;
import com.aishop.api.shipping.ShippingViews.ImportLine;
import com.aishop.api.shipping.ShippingViews.ImportResult;
import com.aishop.api.shipping.ShippingViews.ReadyOrder;
import com.aishop.api.shipping.ShippingViews.SenderSettings;
import com.aishop.api.shipping.ShippingViews.ShipmentRow;

/**
 * 배송·송장 일괄 처리.
 * 출고 대기 주문을 엑셀로 내려받아 택배사 프로그램에서 송장을 출력하고,
 * 송장번호가 채워진 엑셀을 다시 올리면 한 번에 출고 처리한다.
 */
@Service
public class ShippingService {

	static final Set<String> NEW_STATUSES = Set.of("PAID");
	static final Set<String> READY_STATUSES = Set.of("PREPARING");
	static final Set<String> EXPORTABLE = Set.of("PAID", "PREPARING");
	private static final int MAX_IMPORT_ROWS = 2_000;

	static final List<String> EXPORT_HEADERS = List.of("주문번호", "받는분", "받는분 전화", "우편번호", "주소", "상세주소",
			"배송메모", "상품명", "수량", "보내는분", "보내는분 전화", "보내는분 우편번호", "보내는분 주소", "운송장번호");

	private final ShippingRepository shipping;
	private final OrderService orderService;
	private final FieldCipher cipher;

	public ShippingService(ShippingRepository shipping, OrderService orderService, FieldCipher cipher) {
		this.shipping = shipping;
		this.orderService = orderService;
		this.cipher = cipher;
	}

	@Transactional(readOnly = true)
	public List<ReadyOrder> newOrders(long sellerId) {
		return shipping.findReadyOrders(sellerId, NEW_STATUSES);
	}

	@Transactional(readOnly = true)
	public List<ReadyOrder> readyOrders(long sellerId) {
		return shipping.findReadyOrders(sellerId, READY_STATUSES);
	}

	@Transactional(readOnly = true)
	public PageResponse<ShipmentRow> shipments(long sellerId, Set<String> statuses, String keyword, int page) {
		return shipping.findShipments(sellerId, statuses, keyword, page, 50);
	}

	/** 선택한 신규 주문을 한 번에 발주확인 (주문마다 따로 처리해서 하나가 실패해도 나머지는 진행) */
	public BulkResult acceptAll(long sellerId, List<String> orderNos) {
		int success = 0;
		List<String> errors = new ArrayList<>();
		for (String orderNo : new LinkedHashSet<>(orderNos)) {
			try {
				orderService.acceptOrder(sellerId, orderNo);
				success++;
			} catch (ApiException e) {
				errors.add(orderNo + ": " + e.getMessage());
			}
		}
		return new BulkResult(success, errors.size(), errors);
	}

	@Transactional
	public void markDelivered(long sellerId, long shipmentId) {
		if (!shipping.markDelivered(sellerId, shipmentId)) {
			throw ApiException.conflict("INVALID_STATUS", "배송 중인 송장만 배송완료로 바꿀 수 있습니다.");
		}
	}

	// ------------------------------------------------------------------
	// 송장 엑셀 내려받기
	// ------------------------------------------------------------------

	/** 선택한 주문의 수령인·상품을 택배사 업로드용 엑셀로 만든다. 개인정보 반출이므로 기록을 남긴다 */
	@Transactional
	public byte[] exportSheet(long sellerId, long actorId, List<String> orderNos, String ip) {
		List<OrderWithItems> orders = shipping.findOrdersForExport(sellerId, new LinkedHashSet<>(orderNos), EXPORTABLE);
		if (orders.isEmpty()) {
			throw ApiException.badRequest("NOTHING_TO_EXPORT", "내려받을 출고 대기 주문이 없습니다.");
		}
		SellersRecord seller = shipping.findSeller(sellerId).orElseThrow(() -> ApiException.notFound("판매자"));
		String senderName = firstNonBlank(seller.getSenderName(), seller.getBusinessName());
		String senderPhone = firstNonBlank(seller.getSenderPhone(), seller.getContactPhone());

		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("송장");
			CellStyle header = headerStyle(workbook);
			Row head = sheet.createRow(0);
			for (int i = 0; i < EXPORT_HEADERS.size(); i++) {
				Cell cell = head.createCell(i);
				cell.setCellValue(EXPORT_HEADERS.get(i));
				cell.setCellStyle(header);
			}
			int rowIndex = 1;
			for (OrderWithItems o : orders) {
				OrdersRecord order = o.order();
				Row row = sheet.createRow(rowIndex++);
				List<String> values = List.of(order.getOrderNo(), nullToEmpty(order.getReceiverName()),
						nullToEmpty(cipher.decrypt(order.getReceiverPhoneEnc())), nullToEmpty(order.getReceiverZipcode()),
						nullToEmpty(cipher.decrypt(order.getReceiverAddress1Enc())),
						nullToEmpty(cipher.decrypt(order.getReceiverAddress2Enc())), nullToEmpty(order.getDeliveryMemo()),
						ShippingRepository.itemsText(o.lines()),
						String.valueOf(o.lines().stream().mapToInt(ShippingRepository.Line::qty).sum()), senderName,
						senderPhone, nullToEmpty(seller.getShipFromZipcode()), nullToEmpty(seller.getShipFromAddress()), "");
				for (int i = 0; i < values.size(); i++) {
					// 전화번호·우편번호 앞자리 0이 사라지지 않도록 모두 글자로 넣는다
					row.createCell(i).setCellValue(values.get(i));
				}
			}
			int[] widths = { 22, 10, 15, 8, 40, 20, 20, 40, 6, 12, 15, 10, 40, 18 };
			for (int i = 0; i < widths.length; i++) {
				sheet.setColumnWidth(i, widths[i] * 256);
			}
			sheet.createFreezePane(0, 1);
			workbook.write(out);
			shipping.auditExport(actorId, orders.size(), ip);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("송장 엑셀 생성 실패", e);
		}
	}

	// ------------------------------------------------------------------
	// 송장번호 엑셀 올리기
	// ------------------------------------------------------------------

	/**
	 * 주문번호·운송장번호 열이 있는 엑셀(xlsx·xls) 또는 CSV를 읽어 출고 처리한다.
	 * 택배사 열이 있으면 그 택배사로, 없으면 화면에서 고른 기본 택배사로 등록한다.
	 */
	public ImportResult importTracking(long sellerId, long actorId, MultipartFile file, Long defaultCarrierId) {
		List<List<String>> table = readTable(file);
		int headerRow = -1;
		int orderCol = -1;
		int trackingCol = -1;
		int carrierCol = -1;
		for (int r = 0; r < Math.min(table.size(), 10) && headerRow < 0; r++) {
			List<String> cells = table.get(r);
			for (int c = 0; c < cells.size(); c++) {
				String h = cells.get(c).replace(" ", "");
				if (orderCol < 0 && h.contains("주문번호")) {
					orderCol = c;
				} else if (trackingCol < 0 && (h.contains("운송장") || h.contains("송장번호"))) {
					trackingCol = c;
				} else if (carrierCol < 0 && h.contains("택배사")) {
					carrierCol = c;
				}
			}
			if (orderCol >= 0 && trackingCol >= 0) {
				headerRow = r;
			} else {
				orderCol = trackingCol = carrierCol = -1;
			}
		}
		if (headerRow < 0) {
			throw ApiException.badRequest("INVALID_SHEET", "엑셀 첫 줄에 '주문번호'와 '운송장번호' 열이 있어야 합니다.");
		}
		if (table.size() - headerRow - 1 > MAX_IMPORT_ROWS) {
			throw ApiException.badRequest("TOO_MANY_ROWS", "한 번에 " + MAX_IMPORT_ROWS + "건까지 올릴 수 있습니다.");
		}

		List<CarrierOption> carriers = orderService.carriers();
		List<ImportLine> lines = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (int r = headerRow + 1; r < table.size(); r++) {
			List<String> cells = table.get(r);
			String orderNo = cell(cells, orderCol);
			String trackingNo = cell(cells, trackingCol).replaceAll("[^0-9A-Za-z]", "");
			if (orderNo.isEmpty() && trackingNo.isEmpty()) {
				continue;
			}
			int excelRow = r + 1;
			if (orderNo.isEmpty() || trackingNo.isEmpty()) {
				lines.add(new ImportLine(excelRow, orderNo, trackingNo, false, "주문번호나 운송장번호가 비어 있습니다."));
				continue;
			}
			// 한 주문에 상품이 여러 줄로 나뉜 양식이면 첫 줄만 처리한다
			if (!seen.add(orderNo)) {
				continue;
			}
			Long carrierId = carrierCol >= 0 ? matchCarrier(carriers, cell(cells, carrierCol)) : null;
			if (carrierId == null) {
				carrierId = defaultCarrierId;
			}
			if (carrierId == null) {
				lines.add(new ImportLine(excelRow, orderNo, trackingNo, false, "택배사를 알 수 없습니다. 기본 택배사를 골라 주세요."));
				continue;
			}
			try {
				orderService.ship(actorId, sellerId, orderNo, carrierId, trackingNo);
				lines.add(new ImportLine(excelRow, orderNo, trackingNo, true, "출고 완료"));
			} catch (ApiException e) {
				lines.add(new ImportLine(excelRow, orderNo, trackingNo, false, e.getMessage()));
			}
		}
		int success = (int) lines.stream().filter(ImportLine::ok).count();
		return new ImportResult(success, lines.size() - success, lines);
	}

	static Long matchCarrier(List<CarrierOption> carriers, String text) {
		String t = text.replace(" ", "").toUpperCase(Locale.ROOT);
		String core = t.replace("택배", "");
		if (core.length() < 2) {
			return null;
		}
		for (CarrierOption c : carriers) {
			// '한진택배' → '한진', 'CJ대한통운' 그대로. 코드(CJ)·전체 이름·일부 이름(대한통운) 모두 받는다
			String shortName = c.name().replace(" ", "").toUpperCase(Locale.ROOT).replace("택배", "");
			if (t.equals(c.code()) || core.contains(shortName) || shortName.contains(core)) {
				return c.id();
			}
		}
		return null;
	}

	private static List<List<String>> readTable(MultipartFile file) {
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
		try {
			if (name.endsWith(".csv")) {
				return readCsv(file.getBytes());
			}
			try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
				Sheet sheet = workbook.getSheetAt(0);
				DataFormatter formatter = new DataFormatter(Locale.KOREA);
				List<List<String>> rows = new ArrayList<>();
				for (int r = 0; r <= sheet.getLastRowNum(); r++) {
					Row row = sheet.getRow(r);
					List<String> cells = new ArrayList<>();
					if (row != null) {
						for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
							Cell cell = row.getCell(c);
							cells.add(cell == null ? "" : formatter.formatCellValue(cell).strip());
						}
					}
					rows.add(cells);
				}
				return rows;
			}
		} catch (IOException | RuntimeException e) {
			throw ApiException.badRequest("INVALID_FILE", "엑셀 파일을 읽지 못했습니다. xlsx, xls, csv 파일인지 확인해 주세요.");
		}
	}

	/** 택배사 프로그램이 내보내는 CSV는 UTF-8이 아니라 MS949(한글 윈도우)인 경우가 많다 */
	private static List<List<String>> readCsv(byte[] bytes) {
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		} catch (CharacterCodingException e) {
			text = new String(bytes, Charset.forName("MS949"));
		}
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		List<List<String>> rows = new ArrayList<>();
		for (String line : text.split("\r?\n")) {
			rows.add(parseCsvLine(line));
		}
		return rows;
	}

	static List<String> parseCsvLine(String line) {
		List<String> cells = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				} else if (ch == '"') {
					quoted = false;
				} else {
					current.append(ch);
				}
			} else if (ch == '"') {
				quoted = true;
			} else if (ch == ',') {
				cells.add(current.toString().strip());
				current.setLength(0);
			} else {
				current.append(ch);
			}
		}
		cells.add(current.toString().strip());
		return cells;
	}

	// ------------------------------------------------------------------
	// 보내는 사람·출고지·반품지
	// ------------------------------------------------------------------

	@Transactional(readOnly = true)
	public SenderSettings senderSettings(long sellerId) {
		SellersRecord s = shipping.findSeller(sellerId).orElseThrow(() -> ApiException.notFound("판매자"));
		return new SenderSettings(s.getSenderName(), s.getSenderPhone(), s.getShipFromZipcode(), s.getShipFromAddress(),
				s.getReturnZipcode(), s.getReturnAddress());
	}

	@Transactional
	public SenderSettings updateSenderSettings(long sellerId, SenderSettings settings) {
		shipping.updateSender(sellerId, settings);
		return senderSettings(sellerId);
	}

	// ------------------------------------------------------------------

	private static CellStyle headerStyle(Workbook workbook) {
		Font bold = workbook.createFont();
		bold.setBold(true);
		CellStyle style = workbook.createCellStyle();
		style.setFont(bold);
		return style;
	}

	private static String cell(List<String> cells, int index) {
		return index < cells.size() && cells.get(index) != null ? cells.get(index).strip() : "";
	}

	private static String firstNonBlank(String a, String b) {
		return a != null && !a.isBlank() ? a : nullToEmpty(b);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
