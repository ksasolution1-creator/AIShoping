package com.aishop.api.auth;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.auth.AuthRequests.Signup;
import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.jooq.tables.records.UsersRecord;

@Service
public class AuthService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final LoginAttemptGuard attemptGuard;
	private final String termsVersion;
	// 없는 이메일로 로그인해도 비밀번호 검사 시간을 똑같이 써서, 응답 시간으로 가입 여부를 알 수 없게 한다
	private final String dummyHash;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, LoginAttemptGuard attemptGuard,
			@Value("${aishop.auth.terms-version:2026-10-01}") String termsVersion) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.attemptGuard = attemptGuard;
		this.termsVersion = termsVersion;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public AuthUser signup(Signup request, String ip) {
		String email = request.email().strip();
		if (users.emailInUse(email)) {
			throw ApiException.conflict("EMAIL_IN_USE", "이미 가입된 이메일입니다.");
		}
		long userId = users.insert(email, passwordEncoder.encode(request.password()), request.name().strip(), null,
				"CUSTOMER");
		users.insertAgreement(userId, "AGE_14", termsVersion, true, ip);
		users.insertAgreement(userId, "TERMS", termsVersion, true, ip);
		users.insertAgreement(userId, "PRIVACY", termsVersion, true, ip);
		users.insertAgreement(userId, "MARKETING", termsVersion, request.marketingAgreed(), ip);
		users.setMarketingAgreed(userId, request.marketingAgreed());
		users.touchLastLogin(userId);
		return loadAuthUser(users.findById(userId).orElseThrow());
	}

	/** 이메일·비밀번호 확인. 실패 횟수를 세고, 연속 실패하면 잠근다. */
	@Transactional
	public AuthUser login(String email, String password) {
		String normalized = email.strip();
		attemptGuard.checkNotLocked(normalized);

		UsersRecord user = users.findActiveByEmail(normalized).orElse(null);
		String hash = user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash();
		boolean matches = passwordEncoder.matches(password, hash);
		if (user == null || user.getPasswordHash() == null || !matches) {
			attemptGuard.recordFailure(normalized);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않습니다.");
		}
		if (!"ACTIVE".equals(user.getStatus())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "이용이 정지된 계정입니다. 고객센터에 문의해 주세요.");
		}

		attemptGuard.reset(normalized);
		users.touchLastLogin(user.getId());
		return loadAuthUser(user);
	}

	@Transactional
	public void withdraw(long userId, String password) {
		UsersRecord user = users.findById(userId).orElseThrow(() -> ApiException.notFound("회원"));
		if ("STAFF".equals(user.getType())) {
			throw ApiException.badRequest("STAFF_CANNOT_WITHDRAW", "운영자 계정은 탈퇴할 수 없습니다.");
		}
		if (user.getPasswordHash() != null && !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "비밀번호가 맞지 않습니다.");
		}
		users.withdraw(userId);
	}

	/** 운영자 계정 생성 (최초 관리자 준비, 이후 운영자 추가 화면에서 사용) */
	@Transactional
	public long createStaff(String email, String rawPassword, String name, Set<String> roles, long sellerId) {
		if (users.emailInUse(email)) {
			throw ApiException.conflict("EMAIL_IN_USE", "이미 가입된 이메일입니다.");
		}
		long userId = users.insert(email, passwordEncoder.encode(rawPassword), name, null, "STAFF");
		roles.forEach(role -> users.addRole(userId, role));
		users.addSellerMember(sellerId, userId, "OWNER");
		return userId;
	}

	public boolean emailInUse(String email) {
		return users.emailInUse(email);
	}

	private AuthUser loadAuthUser(UsersRecord user) {
		boolean staff = "STAFF".equals(user.getType());
		Set<String> roles = staff ? users.findRoles(user.getId()) : Set.of();
		Long sellerId = null;
		if (staff) {
			sellerId = users.findSellerId(user.getId())
				.orElse(roles.contains("ADMIN") ? AdminContext.HEAD_OFFICE_SELLER_ID : null);
		}
		return new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getType(), Set.copyOf(roles), sellerId);
	}

}
