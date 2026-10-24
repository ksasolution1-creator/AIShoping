package com.aishop.api.auth;

import static com.aishop.api.jooq.Tables.ADDRESSES;
import static com.aishop.api.jooq.Tables.CART_ITEMS;
import static com.aishop.api.jooq.Tables.SELLER_MEMBERS;
import static com.aishop.api.jooq.Tables.SOCIAL_ACCOUNTS;
import static com.aishop.api.jooq.Tables.USERS;
import static com.aishop.api.jooq.Tables.USER_AGREEMENTS;
import static com.aishop.api.jooq.Tables.USER_ROLES;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.lower;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.postgres.extensions.types.Inet;
import org.springframework.stereotype.Repository;

import com.aishop.api.jooq.tables.records.UsersRecord;

@Repository
public class UserRepository {

	private static final String WITHDRAWN = "WITHDRAWN";

	private final DSLContext dsl;

	public UserRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	/** 탈퇴하지 않은 회원을 이메일로 찾는다 (대소문자 무시) */
	public Optional<UsersRecord> findActiveByEmail(String email) {
		return dsl.selectFrom(USERS)
			.where(lower(USERS.EMAIL).eq(email.toLowerCase()), USERS.STATUS.ne(WITHDRAWN))
			.fetchOptional();
	}

	public Optional<UsersRecord> findById(long id) {
		return dsl.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOptional();
	}

	public boolean emailInUse(String email) {
		return findActiveByEmail(email).isPresent();
	}

	public long insert(String email, String passwordHash, String name, String phoneEnc, String type) {
		return dsl.insertInto(USERS)
			.set(USERS.EMAIL, email)
			.set(USERS.PASSWORD_HASH, passwordHash)
			.set(USERS.NAME, name)
			.set(USERS.PHONE_ENC, phoneEnc)
			.set(USERS.TYPE, type)
			.returning(USERS.ID)
			.fetchOne(USERS.ID);
	}

	public void insertAgreement(long userId, String type, String version, boolean agreed, String ip) {
		Inet inet = toInet(ip);
		dsl.insertInto(USER_AGREEMENTS)
			.set(USER_AGREEMENTS.USER_ID, userId)
			.set(USER_AGREEMENTS.TYPE, type)
			.set(USER_AGREEMENTS.VERSION, version)
			.set(USER_AGREEMENTS.AGREED, agreed)
			.set(USER_AGREEMENTS.IP, inet)
			.execute();
	}

	/** IP 문자열(숫자 형식)을 inet으로. 형식이 이상하면 기록하지 않는다 */
	public static Inet toInet(String ip) {
		if (ip == null || ip.isBlank() || !ip.matches("[0-9a-fA-F:.]+")) {
			return null;
		}
		try {
			return Inet.valueOf(InetAddress.getByName(ip));
		}
		catch (UnknownHostException ex) {
			return null;
		}
	}

	public void setMarketingAgreed(long userId, boolean agreed) {
		dsl.update(USERS)
			.set(USERS.MARKETING_AGREED_AT, agreed ? currentOffsetDateTime() : null)
			.where(USERS.ID.eq(userId))
			.execute();
	}

	public Set<String> findRoles(long userId) {
		return dsl.select(USER_ROLES.ROLE).from(USER_ROLES).where(USER_ROLES.USER_ID.eq(userId)).fetchSet(USER_ROLES.ROLE);
	}

	public void addRole(long userId, String role) {
		dsl.insertInto(USER_ROLES)
			.set(USER_ROLES.USER_ID, userId)
			.set(USER_ROLES.ROLE, role)
			.onConflictDoNothing()
			.execute();
	}

	/** 소속 판매자 (여러 곳이면 가장 먼저 등록된 곳) */
	public Optional<Long> findSellerId(long userId) {
		return dsl.select(SELLER_MEMBERS.SELLER_ID)
			.from(SELLER_MEMBERS)
			.where(SELLER_MEMBERS.USER_ID.eq(userId))
			.orderBy(SELLER_MEMBERS.CREATED_AT)
			.limit(1)
			.fetchOptional(SELLER_MEMBERS.SELLER_ID);
	}

	public void addSellerMember(long sellerId, long userId, String role) {
		dsl.insertInto(SELLER_MEMBERS)
			.set(SELLER_MEMBERS.SELLER_ID, sellerId)
			.set(SELLER_MEMBERS.USER_ID, userId)
			.set(SELLER_MEMBERS.ROLE, role)
			.onConflictDoNothing()
			.execute();
	}

	public void touchLastLogin(long userId) {
		dsl.update(USERS).set(USERS.LAST_LOGIN_AT, currentOffsetDateTime()).where(USERS.ID.eq(userId)).execute();
	}

	/**
	 * 탈퇴: 개인정보를 지우고 상태만 남긴다.
	 * 주문 기록은 전자상거래법에 따라 5년 보관해야 하므로 orders의 주문 시점 정보는 지우지 않는다.
	 */
	public void withdraw(long userId) {
		dsl.deleteFrom(ADDRESSES).where(ADDRESSES.USER_ID.eq(userId)).execute();
		dsl.deleteFrom(SOCIAL_ACCOUNTS).where(SOCIAL_ACCOUNTS.USER_ID.eq(userId)).execute();
		dsl.deleteFrom(CART_ITEMS).where(CART_ITEMS.USER_ID.eq(userId)).execute();
		dsl.update(USERS)
			.set(USERS.EMAIL, (String) null)
			.set(USERS.PASSWORD_HASH, (String) null)
			.set(USERS.PHONE_ENC, (String) null)
			.set(USERS.NAME, "탈퇴회원")
			.set(USERS.MARKETING_AGREED_AT, (OffsetDateTime) null)
			.set(USERS.STATUS, WITHDRAWN)
			.set(USERS.WITHDRAWN_AT, currentOffsetDateTime())
			.where(USERS.ID.eq(userId))
			.execute();
	}

}
