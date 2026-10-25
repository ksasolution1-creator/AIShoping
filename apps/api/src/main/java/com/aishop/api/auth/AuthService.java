package com.aishop.api.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.auth.AuthRequests.Signup;
import com.aishop.api.auth.AuthRequests.SocialSignup;
import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.jooq.tables.records.UsersRecord;

@Service
public class AuthService {

	/** 운영자는 고객보다 길게 받는다 (최초 관리자 준비와 같은 기준) */
	static final int STAFF_MIN_PASSWORD_LENGTH = 10;

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
		recordAgreements(userId, request.marketingAgreed(), ip);
		users.touchLastLogin(userId);
		return loadAuthUser(users.findById(userId).orElseThrow());
	}

	/**
	 * 간편로그인. 이미 연결된 회원이면 로그인하고, 처음이면 빈 값을 돌려줘 약관 동의 화면으로 보낸다.
	 * 같은 이메일로 가입한 회원이 있으면 남의 계정에 붙지 않도록 자동 연결하지 않고 막는다.
	 */
	@Transactional
	public Optional<AuthUser> socialLogin(SocialProfile profile) {
		UsersRecord user = users.findBySocial(profile.provider().name(), profile.providerUserId()).orElse(null);
		if (user != null) {
			if (!"ACTIVE".equals(user.getStatus())) {
				throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "이용이 정지된 계정입니다. 고객센터에 문의해 주세요.");
			}
			users.touchLastLogin(user.getId());
			return Optional.of(loadAuthUser(user));
		}
		requireEmailFree(profile);
		return Optional.empty();
	}

	/** 간편로그인 첫 가입 (약관 동의 후). 비밀번호 없이 만든다 */
	@Transactional
	public AuthUser socialSignup(SocialProfile profile, SocialSignup request, String ip) {
		if (users.findBySocial(profile.provider().name(), profile.providerUserId()).isPresent()) {
			throw ApiException.conflict("ALREADY_SIGNED_UP", "이미 가입된 계정입니다. 다시 로그인해 주세요.");
		}
		requireEmailFree(profile);
		long userId = users.insert(profile.email(), null, request.name().strip(), null, "CUSTOMER");
		users.linkSocial(userId, profile.provider().name(), profile.providerUserId());
		recordAgreements(userId, request.marketingAgreed(), ip);
		users.touchLastLogin(userId);
		return loadAuthUser(users.findById(userId).orElseThrow());
	}

	/** 마이페이지: 비밀번호가 있는지, 어떤 간편로그인이 연결됐는지 */
	@Transactional(readOnly = true)
	public LoginMethods loginMethods(long userId) {
		UsersRecord user = users.findById(userId).orElseThrow(() -> ApiException.notFound("회원"));
		return new LoginMethods(user.getPasswordHash() != null, users.findSocialProviders(userId));
	}

	public record LoginMethods(boolean hasPassword, List<String> socialProviders) {
	}

	/** @param agreedAt 동의한 시각 (동의하지 않았으면 null) */
	public record MarketingConsent(boolean agreed, OffsetDateTime agreedAt) {
	}

	@Transactional(readOnly = true)
	public MarketingConsent marketingConsent(long userId) {
		UsersRecord user = users.findById(userId).orElseThrow(() -> ApiException.notFound("회원"));
		return new MarketingConsent(user.getMarketingAgreedAt() != null, user.getMarketingAgreedAt());
	}

	/** 마이페이지에서 마케팅 수신 동의를 바꾼다. 동의·철회 이력을 남긴다 */
	@Transactional
	public MarketingConsent changeMarketingConsent(long userId, boolean agreed, String ip) {
		users.insertAgreement(userId, "MARKETING", termsVersion, agreed, ip);
		users.setMarketingAgreed(userId, agreed);
		return marketingConsent(userId);
	}

	private void requireEmailFree(SocialProfile profile) {
		if (profile.email() != null && users.emailInUse(profile.email())) {
			throw ApiException.conflict("EMAIL_IN_USE",
					"이미 같은 이메일(" + profile.email() + ")로 가입한 계정이 있습니다. 이메일로 로그인해 주세요.");
		}
	}

	private void recordAgreements(long userId, boolean marketingAgreed, String ip) {
		users.insertAgreement(userId, "AGE_14", termsVersion, true, ip);
		users.insertAgreement(userId, "TERMS", termsVersion, true, ip);
		users.insertAgreement(userId, "PRIVACY", termsVersion, true, ip);
		users.insertAgreement(userId, "MARKETING", termsVersion, marketingAgreed, ip);
		users.setMarketingAgreed(userId, marketingAgreed);
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
		if (user.getPasswordHash() != null
				&& (password == null || !passwordEncoder.matches(password, user.getPasswordHash()))) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "비밀번호가 맞지 않습니다.");
		}
		users.withdraw(userId);
	}

	/**
	 * 비밀번호 변경. 로그인 상태에서도 현재 비밀번호를 다시 확인하고,
	 * 틀린 횟수는 로그인 실패와 함께 세어서 대입 공격을 막는다.
	 */
	@Transactional
	public void changePassword(long userId, String currentPassword, String newPassword) {
		UsersRecord user = users.findById(userId).orElseThrow(() -> ApiException.notFound("회원"));
		if (user.getPasswordHash() == null) {
			throw ApiException.badRequest("NO_PASSWORD", "소셜 로그인 계정은 비밀번호를 바꿀 수 없습니다.");
		}
		attemptGuard.checkNotLocked(user.getEmail());
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			attemptGuard.recordFailure(user.getEmail());
			throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "현재 비밀번호가 맞지 않습니다.");
		}
		if ("STAFF".equals(user.getType()) && newPassword.length() < STAFF_MIN_PASSWORD_LENGTH) {
			throw ApiException.badRequest("WEAK_PASSWORD",
					"운영자 비밀번호는 " + STAFF_MIN_PASSWORD_LENGTH + "자 이상으로 정해 주세요.");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw ApiException.badRequest("SAME_PASSWORD", "지금 쓰는 비밀번호와 다른 비밀번호로 정해 주세요.");
		}
		attemptGuard.reset(user.getEmail());
		users.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
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

	AuthUser loadAuthUser(UsersRecord user) {
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
