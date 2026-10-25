package com.aishop.api.member;

import static com.aishop.api.jooq.Tables.USERS;
import static com.aishop.api.jooq.Tables.USER_ROLES;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.aishop.api.auth.AuthService;
import com.aishop.api.auth.AuthUser;
import com.aishop.api.auth.SessionRevoker;
import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.jooq.tables.records.UsersRecord;

/**
 * 운영자 계정 관리. 관리자(ADMIN)만 쓸 수 있다.
 * ADMIN은 운영자 추가·정지까지 모두, OPERATOR는 상품·주문 등 일상 업무만 한다.
 */
@Service
public class StaffService {

	private static final Set<String> ROLES = Set.of("ADMIN", "OPERATOR");

	private final DSLContext dsl;
	private final AuthService authService;
	private final PasswordEncoder passwordEncoder;
	private final MemberRepository members;
	private final SessionRevoker sessionRevoker;

	public StaffService(DSLContext dsl, AuthService authService, PasswordEncoder passwordEncoder,
			MemberRepository members, SessionRevoker sessionRevoker) {
		this.dsl = dsl;
		this.authService = authService;
		this.passwordEncoder = passwordEncoder;
		this.members = members;
		this.sessionRevoker = sessionRevoker;
	}

	/** @param role ADMIN(관리자) 또는 OPERATOR(운영자) */
	public record Staff(long id, String name, String email, String role, String status, OffsetDateTime createdAt,
			OffsetDateTime lastLoginAt) {
	}

	public record CreateStaff(
			@NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 아닙니다.") @Size(max = 100) String email,
			@NotBlank(message = "이름을 입력해 주세요.") @Size(max = 50) String name,
			@NotBlank(message = "비밀번호를 입력해 주세요.") @Size(max = 100) String password,
			@NotBlank @Pattern(regexp = "ADMIN|OPERATOR", message = "권한을 선택해 주세요.") String role) {
	}

	public record ChangeStaff(
			@Pattern(regexp = "ADMIN|OPERATOR", message = "권한을 선택해 주세요.") String role,
			@Pattern(regexp = "ACTIVE|SUSPENDED", message = "사용 또는 정지만 선택할 수 있습니다.") String status) {
	}

	public record ResetPassword(@NotBlank(message = "새 비밀번호를 입력해 주세요.") @Size(max = 100) String password) {
	}

	@Transactional(readOnly = true)
	public List<Staff> list(AuthUser actor) {
		requireAdmin(actor);
		Map<Long, List<String>> roles = dsl.select(USER_ROLES.USER_ID, USER_ROLES.ROLE)
			.from(USER_ROLES)
			.fetchGroups(USER_ROLES.USER_ID, USER_ROLES.ROLE);
		return dsl.selectFrom(USERS)
			.where(USERS.TYPE.eq("STAFF"), USERS.STATUS.ne("WITHDRAWN"))
			.orderBy(USERS.CREATED_AT)
			.fetch(u -> toStaff(u, roles.getOrDefault(u.getId(), List.of())));
	}

	@Transactional
	public List<Staff> create(AuthUser actor, CreateStaff request, String ip) {
		requireAdmin(actor);
		checkPassword(request.password());
		long userId = authService.createStaff(request.email().strip(), request.password(), request.name().strip(),
				Set.of(request.role()), actor.sellerId() != null ? actor.sellerId() : AdminContext.HEAD_OFFICE_SELLER_ID);
		members.audit(actor.id(), "CREATE_STAFF", userId, Map.of("role", request.role()), ip);
		return list(actor);
	}

	/** 권한 변경·정지·정지 해제. 자기 자신과 마지막 남은 관리자는 바꿀 수 없다 */
	@Transactional
	public List<Staff> change(AuthUser actor, long userId, ChangeStaff request, String ip) {
		requireAdmin(actor);
		UsersRecord user = lockStaff(userId);
		if (userId == actor.id()) {
			throw ApiException.badRequest("SELF_CHANGE", "내 계정의 권한이나 상태는 바꿀 수 없습니다.");
		}
		boolean wasActiveAdmin = "ACTIVE".equals(user.getStatus()) && roleOf(userId).equals("ADMIN");
		boolean staysActiveAdmin = !"SUSPENDED".equals(request.status())
				&& (request.role() == null ? roleOf(userId).equals("ADMIN") : request.role().equals("ADMIN"));
		if (wasActiveAdmin && !staysActiveAdmin && activeAdminCount() <= 1) {
			throw ApiException.badRequest("LAST_ADMIN", "관리자가 한 명은 남아 있어야 합니다.");
		}

		boolean revoke = false;
		if (request.role() != null && !request.role().equals(roleOf(userId))) {
			dsl.deleteFrom(USER_ROLES).where(USER_ROLES.USER_ID.eq(userId)).execute();
			dsl.insertInto(USER_ROLES).set(USER_ROLES.USER_ID, userId).set(USER_ROLES.ROLE, request.role()).execute();
			members.audit(actor.id(), "CHANGE_STAFF_ROLE", userId, Map.of("role", request.role()), ip);
			// 권한은 로그인할 때 세션에 담기므로, 바뀐 권한으로 다시 로그인하게 한다
			revoke = true;
		}
		if (request.status() != null && !request.status().equals(user.getStatus())) {
			boolean suspend = "SUSPENDED".equals(request.status());
			members.updateStatus(userId, request.status());
			members.audit(actor.id(), suspend ? "SUSPEND_USER" : "UNSUSPEND_USER", userId, null, ip);
			revoke |= suspend;
		}
		if (revoke) {
			revokeAfterCommit(userId);
		}
		return list(actor);
	}

	@Transactional
	public void resetPassword(AuthUser actor, long userId, ResetPassword request, String ip) {
		requireAdmin(actor);
		lockStaff(userId);
		checkPassword(request.password());
		dsl.update(USERS)
			.set(USERS.PASSWORD_HASH, passwordEncoder.encode(request.password()))
			.where(USERS.ID.eq(userId))
			.execute();
		members.audit(actor.id(), "RESET_STAFF_PASSWORD", userId, null, ip);
		// 예전 비밀번호로 로그인해 있던 세션은 끊는다
		revokeAfterCommit(userId);
	}

	// ------------------------------------------------------------------

	private static void requireAdmin(AuthUser actor) {
		if (!actor.roles().contains("ADMIN")) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "운영자 관리는 관리자만 할 수 있습니다.");
		}
	}

	private static void checkPassword(String password) {
		if (password.length() < AuthService.STAFF_MIN_PASSWORD_LENGTH) {
			throw ApiException.badRequest("WEAK_PASSWORD",
					"운영자 비밀번호는 " + AuthService.STAFF_MIN_PASSWORD_LENGTH + "자 이상으로 정해 주세요.");
		}
	}

	private UsersRecord lockStaff(long userId) {
		return dsl.selectFrom(USERS)
			.where(USERS.ID.eq(userId), USERS.TYPE.eq("STAFF"), USERS.STATUS.ne("WITHDRAWN"))
			.forUpdate()
			.fetchOptional()
			.orElseThrow(() -> ApiException.notFound("운영자"));
	}

	private String roleOf(long userId) {
		Set<String> roles = dsl.select(USER_ROLES.ROLE)
			.from(USER_ROLES)
			.where(USER_ROLES.USER_ID.eq(userId))
			.fetchSet(USER_ROLES.ROLE);
		return roles.contains("ADMIN") ? "ADMIN" : "OPERATOR";
	}

	private int activeAdminCount() {
		return dsl.fetchCount(dsl.select(USERS.ID)
			.from(USERS)
			.join(USER_ROLES).on(USER_ROLES.USER_ID.eq(USERS.ID))
			.where(USERS.TYPE.eq("STAFF"), USERS.STATUS.eq("ACTIVE"), USER_ROLES.ROLE.eq("ADMIN")));
	}

	private static Staff toStaff(UsersRecord u, List<String> roles) {
		String role = roles.contains("ADMIN") ? "ADMIN" : "OPERATOR";
		return new Staff(u.getId(), u.getName(), u.getEmail(), role, u.getStatus(), u.getCreatedAt(),
				u.getLastLoginAt());
	}

	private void revokeAfterCommit(long userId) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				sessionRevoker.revoke(userId);
			}
		});
	}

}
