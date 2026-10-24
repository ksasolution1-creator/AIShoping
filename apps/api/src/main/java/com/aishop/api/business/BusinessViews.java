package com.aishop.api.business;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 사업자 회원 인증과 상품의 사업자 조건 */
public final class BusinessViews {

	private BusinessViews() {
	}

	public record ApplyForm(
			@NotBlank(message = "사업자등록번호를 입력해 주세요.") String businessNo,
			@NotBlank(message = "상호를 입력해 주세요.") @Size(max = 100) String businessName,
			@NotBlank(message = "대표자 이름을 입력해 주세요.") @Size(max = 50) String ceoName,
			@Size(max = 100) String businessItem) {
	}

	/** @param status NONE(신청 안 함)·PENDING·APPROVED·REJECTED */
	public record Profile(String status, String businessNo, String businessName, String ceoName, String businessItem,
			String rejectReason, OffsetDateTime createdAt, OffsetDateTime reviewedAt) {

		static final Profile NONE = new Profile("NONE", null, null, null, null, null, null, null);

	}

	public record AdminApplication(long userId, String email, String userName, String businessNo, String businessName,
			String ceoName, String businessItem, String status, String rejectReason, OffsetDateTime createdAt,
			OffsetDateTime reviewedAt) {
	}

	public record Review(@NotNull Boolean approve, @Size(max = 200) String rejectReason) {
	}

	/** 상품의 사업자 조건 */
	public record ProductBusinessTerms(
			boolean businessOnly,
			boolean imageReuseAllowed,
			@Size(max = 100) List<@Valid SkuBusinessPrice> skuPrices) {
	}

	/** @param businessPrice 사업자 회원가. null이면 일반 판매가 */
	public record SkuBusinessPrice(@NotNull Long skuId, @PositiveOrZero Long businessPrice) {
	}

	/**
	 * 사업자등록번호 형식·검증번호 확인 (국세청 규칙: 가중치 1,3,7,1,3,7,1,3,5).
	 * 휴·폐업 여부는 확인하지 못하므로 운영자가 승인할 때 홈택스에서 조회한다.
	 */
	static boolean validBusinessNo(String digits) {
		if (digits == null || !digits.matches("\\d{10}")) {
			return false;
		}
		int[] weights = { 1, 3, 7, 1, 3, 7, 1, 3, 5 };
		int sum = 0;
		for (int i = 0; i < 9; i++) {
			sum += (digits.charAt(i) - '0') * weights[i];
		}
		sum += (digits.charAt(8) - '0') * 5 / 10;
		int check = (10 - sum % 10) % 10;
		return check == digits.charAt(9) - '0';
	}

	static String normalizeBusinessNo(String raw) {
		return raw == null ? null : raw.replaceAll("[^0-9]", "");
	}

}
