package com.aishop.api.member;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.aishop.api.business.BusinessViews.Profile;

/** 운영자 회원 관리 화면 */
public final class MemberViews {

	private MemberViews() {
	}

	/**
	 * @param joinMethods EMAIL·KAKAO·NAVER (간편로그인을 여러 개 연결하면 여러 개)
	 * @param phoneMasked 010-****-5678 형태. 전체 번호는 상세에서만
	 * @param businessStatus 사업자 인증 상태 (신청 안 했으면 null)
	 */
	public record MemberRow(long id, String name, String email, List<String> joinMethods, String phoneMasked,
			String status, String businessStatus, OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
	}

	/**
	 * @param method EMAIL·KAKAO·NAVER
	 * @param status ACTIVE·SUSPENDED·WITHDRAWN
	 */
	public record MemberSearch(String q, String method, String status, LocalDate joinedFrom, LocalDate joinedTo,
			int page, int size) {
	}

	public record MemberDetail(long id, String name, String email, String phone, List<String> joinMethods,
			String status, OffsetDateTime createdAt, OffsetDateTime lastLoginAt, OffsetDateTime withdrawnAt,
			boolean marketingAgreed, OffsetDateTime marketingAgreedAt, List<Agreement> agreements, Profile business,
			OrderStats orders, List<RecentOrder> recentOrders, List<StatusLog> statusLogs) {
	}

	/** 약관·마케팅 동의 이력 (최근 순) */
	public record Agreement(String type, String version, boolean agreed, OffsetDateTime createdAt) {
	}

	/**
	 * 결제가 끝난 주문만 센다 (입금 대기·취소 제외)
	 *
	 * @param totalAmount 결제 금액 합계
	 */
	public record OrderStats(int count, long totalAmount, OffsetDateTime lastOrderedAt) {
	}

	public record RecentOrder(String orderNo, String status, long payAmount, OffsetDateTime orderedAt) {
	}

	/** 이용 정지·해제 기록 */
	public record StatusLog(String action, String reason, String actorName, OffsetDateTime createdAt) {
	}

	public record ChangeStatus(
			@NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED", message = "정상 또는 정지만 선택할 수 있습니다.") String status,
			@NotBlank(message = "사유를 입력해 주세요.") @Size(max = 200) String reason) {
	}

	/** 010-1234-5678 → 010-****-5678. 형식이 다르면 뒤 4자리만 남긴다 */
	static String maskPhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return null;
		}
		String digits = phone.replaceAll("[^0-9]", "");
		if (digits.length() < 8) {
			return "****";
		}
		String last = digits.substring(digits.length() - 4);
		String head = digits.length() >= 10 ? digits.substring(0, 3) : "";
		return head.isEmpty() ? "****-" + last : head + "-****-" + last;
	}

}
