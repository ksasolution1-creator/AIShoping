package com.aishop.api.claim;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ClaimViews {

	private ClaimViews() {
	}

	/** 반품 사유. 단순변심만 고객 귀책 */
	public enum ReturnReason {

		CHANGE_OF_MIND("단순 변심", "CUSTOMER"),
		DEFECTIVE("상품 불량·파손", "SELLER"),
		NOT_AS_DESCRIBED("상품 설명·상태등급과 다름", "SELLER"),
		WRONG_ITEM("다른 상품이 옴", "SELLER"),
		MISSING_PARTS("구성품 누락", "SELLER");

		private final String label;
		private final String fault;

		ReturnReason(String label, String fault) {
			this.label = label;
			this.fault = fault;
		}

		public String label() {
			return label;
		}

		public String fault() {
			return fault;
		}

		public static String labelOf(String code) {
			if ("CANCEL".equals(code)) {
				return "주문 취소";
			}
			try {
				return valueOf(code).label;
			}
			catch (IllegalArgumentException | NullPointerException ex) {
				return code;
			}
		}

	}

	public record CancelItem(@Size(max = 200) String reason) {
	}

	public record RequestReturn(@NotNull(message = "반품 사유를 선택해 주세요.") ReturnReason reason,
			@Size(max = 500) String detail) {
	}

	public record ApproveReturn(Long pickupCarrierId,
			@Pattern(regexp = "^[0-9-]{0,20}$", message = "송장번호는 숫자로 입력해 주세요.") String pickupTrackingNo) {
	}

	public record RejectClaim(@NotNull(message = "거절 사유를 입력해 주세요.") @Size(min = 2, max = 300) String reason) {
	}

	/**
	 * 반품 검수 완료.
	 * @param inspectionGrade 다시 매긴 상태등급 (NEW, RETURN_OPENED, USED_B ...) 또는 DISPOSE(폐기)
	 */
	public record CompleteReturn(
			@NotNull(message = "검수 결과를 선택해 주세요.")
			@Pattern(regexp = "^(NEW|RETURN_UNOPENED|RETURN_OPENED|REFURB|USED_A|USED_B|USED_C|DISPLAY|DISPOSE)$")
			String inspectionGrade,
			@Size(max = 500) String note,
			Long restockLocationId) {
	}

	public record ClaimView(long id, String type, String status, String orderNo, long orderItemId, String ordererName,
			String productName, String optionText, String conditionGrade, int qty, long lineTotal, String reasonCode,
			String reasonLabel, String reasonDetail, String fault, long returnShippingFee, Long refundAmount,
			String inspectionGrade, String rejectReason, String pickupCarrierName, String pickupTrackingNo,
			OffsetDateTime requestedAt, OffsetDateTime completedAt) {
	}

}
