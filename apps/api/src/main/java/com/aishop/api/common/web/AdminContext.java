package com.aishop.api.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.aishop.api.auth.AuthUser;

/** 현재 관리자 요청의 로그인 사용자와 판매자 */
@Component
public class AdminContext {

	public static final long HEAD_OFFICE_SELLER_ID = 1L;

	public AuthUser user() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof AuthUser user) || !user.isStaff()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
		}
		return user;
	}

	public long sellerId() {
		Long sellerId = user().sellerId();
		if (sellerId == null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NO_SELLER", "소속된 판매자가 없는 계정입니다.");
		}
		return sellerId;
	}

}
