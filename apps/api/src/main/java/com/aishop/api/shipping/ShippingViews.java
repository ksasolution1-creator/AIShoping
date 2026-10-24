package com.aishop.api.shipping;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 배송·송장 화면의 요청과 응답 */
public final class ShippingViews {

	private ShippingViews() {
	}

	/** 출고 대기 주문 한 건 (이 판매자 상품만) */
	public record ReadyOrder(
			String orderNo,
			OffsetDateTime paidAt,
			String status,
			String receiverName,
			String itemsText,
			int totalQty,
			long payAmount) {
	}

	/** 송장 엑셀에 쓰는 주문 한 건 (수령인 개인정보 포함) */
	record ExportRow(
			String orderNo,
			String receiverName,
			String receiverPhone,
			String zipcode,
			String address1,
			String address2,
			String deliveryMemo,
			String itemsText,
			int totalQty,
			long orderId) {
	}

	public record ShipmentRow(
			long id,
			String orderNo,
			String receiverName,
			String carrierName,
			String trackingNo,
			String trackingUrl,
			String status,
			OffsetDateTime shippedAt,
			OffsetDateTime deliveredAt,
			String itemsText) {
	}

	public record OrderNos(@NotEmpty(message = "주문을 선택해 주세요.") @Size(max = 500) List<String> orderNos) {
	}

	/** 송장 엑셀 올리기 결과 한 줄 */
	public record ImportLine(int row, String orderNo, String trackingNo, boolean ok, String message) {
	}

	public record ImportResult(int success, int failed, List<ImportLine> lines) {
	}

	public record BulkResult(int success, int failed, List<String> errors) {
	}

	public record SenderSettings(
			@Size(max = 50) String senderName,
			@Size(max = 30) String senderPhone,
			@Size(max = 10) String shipFromZipcode,
			@Size(max = 200) String shipFromAddress,
			@Size(max = 10) String returnZipcode,
			@Size(max = 200) String returnAddress) {
	}

}
