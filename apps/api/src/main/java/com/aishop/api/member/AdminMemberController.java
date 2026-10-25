package com.aishop.api.member;

import java.time.LocalDate;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.member.MemberViews.ChangeStatus;
import com.aishop.api.member.MemberViews.MemberDetail;
import com.aishop.api.member.MemberViews.MemberRow;
import com.aishop.api.member.MemberViews.MemberSearch;

/** 운영자: 회원 목록·상세·이용 정지 */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

	private static final Set<String> METHODS = Set.of("EMAIL", "KAKAO", "NAVER");
	private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "WITHDRAWN");

	private final MemberService service;
	private final AdminContext admin;

	public AdminMemberController(MemberService service, AdminContext admin) {
		this.service = service;
		this.admin = admin;
	}

	@GetMapping
	public PageResponse<MemberRow> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String method,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "30") int size) {
		admin.user();
		return service.search(new MemberSearch(q, allowed(METHODS, method), allowed(STATUSES, status), from, to,
				Math.max(page, 0), Math.clamp(size, 1, 100)));
	}

	/** 목록에 있는 값만 쓴다 (Set.of는 null을 물으면 예외라 먼저 거른다) */
	private static String allowed(Set<String> values, String value) {
		return value != null && values.contains(value) ? value : null;
	}

	@GetMapping("/{userId}")
	public MemberDetail detail(@PathVariable long userId, HttpServletRequest request) {
		return service.detail(userId, admin.user().id(), request.getRemoteAddr());
	}

	@PostMapping("/{userId}/status")
	public MemberDetail changeStatus(@PathVariable long userId, @Valid @RequestBody ChangeStatus body,
			HttpServletRequest request) {
		return service.changeStatus(userId, body, admin.user().id(), request.getRemoteAddr());
	}

}
