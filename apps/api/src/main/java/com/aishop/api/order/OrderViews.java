package com.aishop.api.order;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.aishop.api.claim.ClaimViews.ClaimView;

public final class OrderViews {

	private OrderViews() {
	}

	// ------------------------------------------------------------------
	// 요청
	// ------------------------------------------------------------------

	public record OrderLineRequest(@NotNull Long skuId, @NotNull @Min(1) @Max(99) Integer qty) {
	}

	/**
	 * @param payMethod TOSS(카드·간편결제) 또는 BANK_TRANSFER(무통장입금)
	 * @param depositorName 무통장입금 입금자명
	 */
	public record CreateOrder(
			@NotEmpty(message = "주문할 상품이 없습니다.") @Size(max = 50) @Valid List<OrderLineRequest> items,
			@NotBlank(message = "받는 분 이름을 입력해 주세요.") @Size(max = 30) String receiverName,
			@NotBlank(message = "받는 분 연락처를 입력해 주세요.")
			@Pattern(regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 맞지 않습니다. 예: 010-1234-5678")
			String receiverPhone,
			@NotBlank(message = "우편번호를 입력해 주세요.") @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리입니다.")
			String zipcode,
			@NotBlank(message = "주소를 입력해 주세요.") @Size(max = 200) String address1,
			@Size(max = 100) String address2,
			@Size(max = 100) String deliveryMemo,
			@Pattern(regexp = "^(TOSS|BANK_TRANSFER)$", message = "결제 수단을 다시 선택해 주세요.") String payMethod,
			@Size(max = 20, message = "입금자명은 20자까지 입력할 수 있습니다.") String depositorName) {

		/** 결제 수단을 고르지 않은 요청은 토스 결제로 본다 */
		public CreateOrder(List<OrderLineRequest> items, String receiverName, String receiverPhone, String zipcode,
				String address1, String address2, String deliveryMemo) {
			this(items, receiverName, receiverPhone, zipcode, address1, address2, deliveryMemo, "TOSS", null);
		}

		public boolean bankTransfer() {
			return "BANK_TRANSFER".equals(payMethod);
		}

	}

	public record ConfirmPayment(@NotBlank String paymentKey, @NotBlank String orderId, @NotNull Long amount) {
	}

	public record CancelOrder(@Size(max = 200) String reason) {
	}

	public record ShipOrder(@NotNull(message = "택배사를 선택해 주세요.") Long carrierId,
			@NotBlank(message = "송장번호를 입력해 주세요.") @Pattern(regexp = "^[0-9-]{8,20}$", message = "송장번호는 숫자 8~20자리입니다.")
			String trackingNo) {
	}

	// ------------------------------------------------------------------
	// 응답
	// ------------------------------------------------------------------

	/** 결제창을 띄우는 데 필요한 값 */
	public record CheckoutInfo(String orderNo, String orderName, long amount, String customerKey, String customerName,
			String customerEmail, String clientKey, String payMethod) {
	}

	/** 무통장입금 안내: 입금할 계좌와 기한 */
	public record BankDeposit(String bankName, String accountNo, String accountHolder, String depositorName,
			OffsetDateTime dueAt) {
	}

	/** @param status REQUESTED(무통장입금 송금 대기), DONE(환불 완료) */
	public record RefundView(long id, long amount, String reason, String status, OffsetDateTime createdAt,
			OffsetDateTime completedAt) {
	}

	/** @param reviewed 구매후기를 이미 썼는지 */
	public record OrderLineView(long id, Long skuId, String productName, String optionText, String conditionGrade,
			String thumbnailUrl, int qty, long unitPrice, long lineTotal, String status, boolean reviewed) {
	}

	public record ShipmentView(long id, String carrierName, String trackingNo, String trackingUrl, String status,
			OffsetDateTime shippedAt, OffsetDateTime deliveredAt) {
	}

	public record PaymentView(String method, String easyPayProvider, long amount, long balanceAmount, String status,
			OffsetDateTime approvedAt) {
	}

	public record Receiver(String name, String phone, String zipcode, String address1, String address2,
			String deliveryMemo) {
	}

	public record OrderSummary(String orderNo, OffsetDateTime orderedAt, String status, String orderName,
			String thumbnailUrl, int itemCount, long payAmount, List<String> itemStatuses) {
	}

	/** @param bankDeposit 무통장입금 주문일 때만 값이 있다 */
	public record OrderDetail(String orderNo, String channel, OffsetDateTime orderedAt, OffsetDateTime paidAt,
			String status, String ordererName, String ordererEmail, Receiver receiver, List<OrderLineView> items,
			long itemsTotal, long shippingFee, long discountTotal, long payAmount, String payMethod,
			BankDeposit bankDeposit, PaymentView payment, List<RefundView> refunds, List<ShipmentView> shipments,
			boolean cancellable, List<ClaimView> claims) {
	}

}
