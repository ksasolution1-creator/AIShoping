package com.aishop.api.shipping;

import static com.aishop.api.jooq.Tables.AUDIT_LOGS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;
import static com.aishop.api.jooq.Tables.ORDERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.aishop.api.TestcontainersConfiguration;
import com.aishop.api.auth.AuthService;
import com.aishop.api.auth.AuthUser;
import com.aishop.api.auth.UserRepository;
import com.aishop.api.catalog.CatalogRequests.CreateProduct;
import com.aishop.api.catalog.CatalogRequests.CreateSku;
import com.aishop.api.catalog.CatalogRequests.ProductFields;
import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.catalog.ProductService;
import com.aishop.api.catalog.ProductStatus;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.order.OrderRepository.CarrierOption;
import com.aishop.api.order.OrderService;
import com.aishop.api.order.OrderViews.CheckoutInfo;
import com.aishop.api.order.OrderViews.CreateOrder;
import com.aishop.api.order.OrderViews.OrderLineRequest;
import com.aishop.api.payment.PaymentService;
import com.aishop.api.payment.TossPaymentsClient;
import com.aishop.api.payment.TossPaymentsClient.TossPayment;
import com.aishop.api.shipping.ShippingViews.ImportResult;
import com.aishop.api.shipping.ShippingViews.ShipmentRow;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShippingServiceTests {

	private static final long SELLER = 1L;

	@MockitoBean
	TossPaymentsClient toss;

	@Autowired
	ShippingService shipping;

	@Autowired
	OrderService orderService;

	@Autowired
	PaymentService paymentService;

	@Autowired
	ProductService productService;

	@Autowired
	AuthService authService;

	@Autowired
	UserRepository users;

	@Autowired
	DSLContext dsl;

	AuthUser customer;
	long staffId;

	@BeforeEach
	void setUp() {
		when(toss.isConfigured()).thenReturn(true);
		when(toss.clientKey()).thenReturn("test_ck_dummy");
		String email = "ship-" + uniq() + "@example.com";
		long userId = users.insert(email, null, "구매자", null, "CUSTOMER");
		customer = new AuthUser(userId, email, "구매자", "CUSTOMER", Set.of(), null);
		staffId = authService.createStaff("ship-staff-" + uniq() + "@example.test", "staffpass1234", "출고담당",
				Set.of("OPERATOR"), SELLER);
	}

	@Test
	void acceptAllMovesNewOrdersToPreparing() {
		String orderNo = paidOrder(1);

		assertThat(shipping.newOrders(SELLER)).extracting(ShippingViews.ReadyOrder::orderNo).contains(orderNo);
		var result = shipping.acceptAll(SELLER, List.of(orderNo, "NO-SUCH-ORDER"));

		assertThat(result.success()).isEqualTo(1);
		assertThat(result.failed()).isEqualTo(1);
		assertThat(shipping.readyOrders(SELLER)).extracting(ShippingViews.ReadyOrder::orderNo).contains(orderNo);
	}

	@Test
	void exportSheetHasDecryptedReceiverAndIsAudited() throws Exception {
		String orderNo = paidOrder(2);
		int auditsBefore = dsl.fetchCount(AUDIT_LOGS, AUDIT_LOGS.ACTOR_USER_ID.eq(staffId));

		byte[] file = shipping.exportSheet(SELLER, staffId, List.of(orderNo), "127.0.0.1");

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("주문번호");
			assertThat(sheet.getRow(0).getCell(13).getStringCellValue()).isEqualTo("운송장번호");
			Row row = sheet.getRow(1);
			assertThat(row.getCell(0).getStringCellValue()).isEqualTo(orderNo);
			assertThat(row.getCell(2).getStringCellValue()).isEqualTo("010-1234-5678");
			assertThat(row.getCell(3).getStringCellValue()).isEqualTo("06236");
			assertThat(row.getCell(8).getStringCellValue()).isEqualTo("2");
		}
		assertThat(dsl.fetchCount(AUDIT_LOGS, AUDIT_LOGS.ACTOR_USER_ID.eq(staffId))).isEqualTo(auditsBefore + 1);
	}

	@Test
	void importTrackingShipsOrdersAndReportsEachRow() throws Exception {
		String first = paidOrder(1);
		String second = paidOrder(1);
		String tracking1 = trackingNo();
		String tracking2 = trackingNo();
		byte[] sheet = workbook(List.of(
				List.of("주문번호", "받는분", "운송장 번호", "택배사"),
				List.of(first, "홍길동", tracking1.substring(0, 4) + "-" + tracking1.substring(4), "CJ대한통운"),
				List.of(second, "홍길동", tracking2, ""),
				List.of("20991231-NOPE0000", "없음", trackingNo(), "")));

		ImportResult result = shipping.importTracking(SELLER, staffId, xlsx(sheet), hanjinId());

		assertThat(result.success()).isEqualTo(2);
		assertThat(result.failed()).isEqualTo(1);
		assertThat(itemStatus(first)).isEqualTo("SHIPPED");
		assertThat(itemStatus(second)).isEqualTo("SHIPPED");

		ShipmentRow shipped = shipping.shipments(SELLER, null, first, 0).items().getFirst();
		assertThat(shipped.carrierName()).isEqualTo("CJ대한통운");
		assertThat(shipped.trackingNo()).isEqualTo(tracking1);
		assertThat(shipped.trackingUrl()).isEqualTo("https://tracker.delivery/#/kr.cjlogistics/" + tracking1);
		assertThat(shipping.shipments(SELLER, null, second, 0).items().getFirst().carrierName()).isEqualTo("한진택배");

		// 같은 파일을 다시 올려도 두 번 출고되지 않는다
		ImportResult again = shipping.importTracking(SELLER, staffId, xlsx(sheet), hanjinId());
		assertThat(again.success()).isZero();
	}

	@Test
	void importAcceptsCsvAndRejectsSheetWithoutColumns() {
		String orderNo = paidOrder(1);
		String csv = "주문번호,운송장번호\n" + orderNo + "," + trackingNo() + "\n";
		var file = new MockMultipartFile("file", "tracking.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

		assertThat(shipping.importTracking(SELLER, staffId, file, hanjinId()).success()).isEqualTo(1);

		var bad = new MockMultipartFile("file", "x.csv", "text/csv", "이름,전화\n가,1\n".getBytes(StandardCharsets.UTF_8));
		assertThatThrownBy(() -> shipping.importTracking(SELLER, staffId, bad, hanjinId()))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("주문번호");
	}

	@Test
	void markDeliveredUpdatesShipmentAndItems() {
		String orderNo = paidOrder(1);
		orderService.ship(staffId, SELLER, orderNo, hanjinId(), trackingNo());
		ShipmentRow shipment = shipping.shipments(SELLER, null, orderNo, 0).items().getFirst();

		shipping.markDelivered(SELLER, shipment.id());

		assertThat(itemStatus(orderNo)).isEqualTo("DELIVERED");
		assertThat(shipping.shipments(SELLER, Set.of("DELIVERED"), orderNo, 0).items()).hasSize(1);
		assertThatThrownBy(() -> shipping.markDelivered(SELLER, shipment.id())).isInstanceOf(ApiException.class);
	}

	@Test
	void carrierNamesAreMatchedLoosely() {
		List<CarrierOption> carriers = orderService.carriers();
		long cj = carriers.stream().filter(c -> c.code().equals("CJ")).findFirst().orElseThrow().id();

		assertThat(ShippingService.matchCarrier(carriers, "CJ대한통운")).isEqualTo(cj);
		assertThat(ShippingService.matchCarrier(carriers, "대한통운")).isEqualTo(cj);
		assertThat(ShippingService.matchCarrier(carriers, "cj")).isEqualTo(cj);
		assertThat(ShippingService.matchCarrier(carriers, "한진")).isEqualTo(hanjinId());
		assertThat(ShippingService.matchCarrier(carriers, "택배")).isNull();
		assertThat(ShippingService.matchCarrier(carriers, "")).isNull();
		assertThat(ShippingService.parseCsvLine("a,\"b,c\",\"d\"\"e\"")).containsExactly("a", "b,c", "d\"e");
	}

	// ------------------------------------------------------------------

	private long hanjinId() {
		return orderService.carriers().stream().filter(c -> c.code().equals("HANJIN")).findFirst().orElseThrow().id();
	}

	private String paidOrder(int qty) {
		ProductFields fields = new ProductFields("배송테스트 " + uniq(), null, null, null, null, Map.of(), null, 3_000L,
				50_000L);
		CreateSku sku = new CreateSku(null, Map.of(), ConditionGrade.NEW, null, null, 10_000L, null, 10);
		long productId = productService.createProduct(SELLER, new CreateProduct(fields, List.of(sku)));
		productService.changeStatus(SELLER, productId, ProductStatus.ON_SALE);
		long skuId = productService.getAdminProduct(SELLER, productId).skus().getFirst().id();

		CheckoutInfo checkout = orderService.createOrder(customer, new CreateOrder(List.of(new OrderLineRequest(skuId, qty)),
				"홍길동", "01012345678", "06236", "서울시 강남구 테헤란로 1", "101호", "문 앞"));
		String key = "pk_" + checkout.orderNo();
		when(toss.confirm(eq(key), eq(checkout.orderNo()), anyLong(), anyString()))
			.thenReturn(new TossPayment(key, checkout.orderNo(), "DONE", "카드", null, checkout.amount(), checkout.amount(),
					OffsetDateTime.now(), "tx", "{}"));
		assertThat(paymentService.confirm(customer.id(), key, checkout.orderNo(), checkout.amount()).success()).isTrue();
		return checkout.orderNo();
	}

	private String itemStatus(String orderNo) {
		return dsl.select(ORDER_ITEMS.STATUS)
			.from(ORDER_ITEMS)
			.join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDER_ID))
			.where(ORDERS.ORDER_NO.eq(orderNo))
			.fetchOne(ORDER_ITEMS.STATUS);
	}

	private static byte[] workbook(List<List<String>> rows) throws Exception {
		try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = wb.createSheet();
			for (int r = 0; r < rows.size(); r++) {
				Row row = sheet.createRow(r);
				for (int c = 0; c < rows.get(r).size(); c++) {
					row.createCell(c).setCellValue(rows.get(r).get(c));
				}
			}
			wb.write(out);
			return out.toByteArray();
		}
	}

	private static MockMultipartFile xlsx(byte[] bytes) {
		return new MockMultipartFile("file", "tracking.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
	}

	private static String trackingNo() {
		return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000_000L + 100_000_000_000L);
	}

	private static String uniq() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

}
