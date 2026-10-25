package com.aishop.api.member;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.common.web.AdminContext;
import com.aishop.api.member.StaffService.ChangeStaff;
import com.aishop.api.member.StaffService.CreateStaff;
import com.aishop.api.member.StaffService.ResetPassword;
import com.aishop.api.member.StaffService.Staff;

/** 관리자: 운영자 계정 추가·권한·정지·비밀번호 초기화 */
@RestController
@RequestMapping("/api/admin/staff")
public class AdminStaffController {

	private final StaffService service;
	private final AdminContext admin;

	public AdminStaffController(StaffService service, AdminContext admin) {
		this.service = service;
		this.admin = admin;
	}

	@GetMapping
	public List<Staff> list() {
		return service.list(admin.user());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public List<Staff> create(@Valid @RequestBody CreateStaff body, HttpServletRequest request) {
		return service.create(admin.user(), body, request.getRemoteAddr());
	}

	@PatchMapping("/{userId}")
	public List<Staff> change(@PathVariable long userId, @Valid @RequestBody ChangeStaff body,
			HttpServletRequest request) {
		return service.change(admin.user(), userId, body, request.getRemoteAddr());
	}

	@PostMapping("/{userId}/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@PathVariable long userId, @Valid @RequestBody ResetPassword body,
			HttpServletRequest request) {
		service.resetPassword(admin.user(), userId, body, request.getRemoteAddr());
	}

}
