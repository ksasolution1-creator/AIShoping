package com.aishop.api.auth;

import java.io.Serializable;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 로그인한 사용자. 세션(Redis)에 저장되므로 Serializable.
 *
 * @param type CUSTOMER 또는 STAFF
 * @param roles STAFF 권한 (ADMIN, OPERATOR, CS, WAREHOUSE, STORE)
 * @param sellerId 관리 화면에서 다루는 판매자. 고객은 null
 */
public record AuthUser(long id, String email, String name, String type, Set<String> roles, Long sellerId)
		implements Serializable {

	@JsonIgnore
	public boolean isStaff() {
		return "STAFF".equals(type);
	}

}
