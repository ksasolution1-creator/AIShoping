package com.aishop.api.business;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.auth.AuthUser;
import com.aishop.api.business.BusinessViews.ApplyForm;
import com.aishop.api.business.BusinessViews.Profile;

/** 고객의 사업자 인증 신청과 상태 확인 */
@RestController
public class BusinessController {

	private final BusinessService service;

	public BusinessController(BusinessService service) {
		this.service = service;
	}

	@GetMapping("/api/me/business")
	public Profile profile(@AuthenticationPrincipal AuthUser user) {
		return service.profile(user.id());
	}

	@PostMapping("/api/me/business")
	public Profile apply(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ApplyForm body) {
		return service.apply(user.id(), !user.isStaff(), body);
	}

}
