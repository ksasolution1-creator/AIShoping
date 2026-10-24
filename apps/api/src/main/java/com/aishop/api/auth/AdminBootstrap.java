package com.aishop.api.auth;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.aishop.api.common.web.AdminContext;

/**
 * 최초 관리자 계정 준비.
 * ADMIN_EMAIL, ADMIN_PASSWORD 환경변수가 있고 그 이메일 계정이 없을 때만 만든다.
 * 계정을 만든 뒤에는 환경변수를 지워도 된다.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AuthService authService;
	private final String email;
	private final String password;

	public AdminBootstrap(AuthService authService,
			@Value("${aishop.admin.bootstrap-email:}") String email,
			@Value("${aishop.admin.bootstrap-password:}") String password) {
		this.authService = authService;
		this.email = email;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (email.isBlank() || password.isBlank() || authService.emailInUse(email)) {
			return;
		}
		if (password.length() < AuthService.STAFF_MIN_PASSWORD_LENGTH) {
			log.warn("관리자 계정을 만들지 않았습니다: ADMIN_PASSWORD는 {}자 이상이어야 합니다.",
					AuthService.STAFF_MIN_PASSWORD_LENGTH);
			return;
		}
		authService.createStaff(email, password, "관리자", Set.of("ADMIN"), AdminContext.HEAD_OFFICE_SELLER_ID);
		log.info("최초 관리자 계정을 만들었습니다: {}", email);
	}

}
