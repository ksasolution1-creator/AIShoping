package com.aishop.api.member;

import static com.aishop.api.jooq.Tables.AUDIT_LOGS;
import static com.aishop.api.jooq.Tables.BUSINESS_PROFILES;
import static com.aishop.api.jooq.Tables.ORDERS;
import static com.aishop.api.jooq.Tables.SOCIAL_ACCOUNTS;
import static com.aishop.api.jooq.Tables.USERS;
import static com.aishop.api.jooq.Tables.USER_AGREEMENTS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.val;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import com.aishop.api.auth.UserRepository;
import com.aishop.api.jooq.tables.Users;
import com.aishop.api.jooq.tables.records.UsersRecord;
import com.aishop.api.member.MemberViews.Agreement;
import com.aishop.api.member.MemberViews.MemberSearch;
import com.aishop.api.member.MemberViews.OrderStats;
import com.aishop.api.member.MemberViews.RecentOrder;
import com.aishop.api.member.MemberViews.StatusLog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class MemberRepository {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	/** 결제가 끝난 주문 (입금 대기·취소 제외) */
	private static final List<String> PAID_ORDER = List.of("PAID", "COMPLETED");
	private static final String TARGET_USER = "USER";

	private final DSLContext dsl;
	private final JsonMapper jsonMapper;

	public MemberRepository(DSLContext dsl, JsonMapper jsonMapper) {
		this.dsl = dsl;
		this.jsonMapper = jsonMapper;
	}

	/** 고객 회원만 (운영자 계정 제외) */
	public List<UsersRecord> search(MemberSearch search) {
		return dsl.selectFrom(USERS)
			.where(conditions(search))
			.orderBy(USERS.CREATED_AT.desc(), USERS.ID.desc())
			.limit(search.size())
			.offset(search.page() * search.size())
			.fetch();
	}

	public long count(MemberSearch search) {
		return dsl.fetchCount(USERS, conditions(search));
	}

	private Condition conditions(MemberSearch s) {
		Condition where = USERS.TYPE.eq("CUSTOMER");
		if (s.q() != null && !s.q().isBlank()) {
			String like = "%" + s.q().strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			where = where.and(USERS.NAME.likeIgnoreCase(like, '\\').or(USERS.EMAIL.likeIgnoreCase(like, '\\')));
		}
		if (s.status() != null) {
			where = where.and(USERS.STATUS.eq(s.status()));
		}
		if (s.joinedFrom() != null) {
			where = where.and(USERS.CREATED_AT.ge(startOfDay(s.joinedFrom())));
		}
		if (s.joinedTo() != null) {
			where = where.and(USERS.CREATED_AT.lt(startOfDay(s.joinedTo().plusDays(1))));
		}
		if ("EMAIL".equals(s.method())) {
			where = where.and(USERS.PASSWORD_HASH.isNotNull());
		}
		else if (s.method() != null) {
			where = where.andExists(selectOne().from(SOCIAL_ACCOUNTS)
				.where(SOCIAL_ACCOUNTS.USER_ID.eq(USERS.ID), SOCIAL_ACCOUNTS.PROVIDER.eq(s.method())));
		}
		return where;
	}

	private static OffsetDateTime startOfDay(LocalDate date) {
		return date.atStartOfDay(KST).toOffsetDateTime();
	}

	public Optional<UsersRecord> findCustomer(long userId) {
		return dsl.selectFrom(USERS).where(USERS.ID.eq(userId), USERS.TYPE.eq("CUSTOMER")).fetchOptional();
	}

	public Optional<UsersRecord> lockCustomer(long userId) {
		return dsl.selectFrom(USERS).where(USERS.ID.eq(userId), USERS.TYPE.eq("CUSTOMER")).forUpdate().fetchOptional();
	}

	/** 회원별 연결된 간편로그인 (KAKAO, NAVER ...) */
	public Map<Long, List<String>> socialProviders(Collection<Long> userIds) {
		return dsl.select(SOCIAL_ACCOUNTS.USER_ID, SOCIAL_ACCOUNTS.PROVIDER)
			.from(SOCIAL_ACCOUNTS)
			.where(SOCIAL_ACCOUNTS.USER_ID.in(userIds))
			.orderBy(SOCIAL_ACCOUNTS.CREATED_AT)
			.fetchGroups(SOCIAL_ACCOUNTS.USER_ID, SOCIAL_ACCOUNTS.PROVIDER);
	}

	public Map<Long, String> businessStatuses(Collection<Long> userIds) {
		return dsl.select(BUSINESS_PROFILES.USER_ID, BUSINESS_PROFILES.STATUS)
			.from(BUSINESS_PROFILES)
			.where(BUSINESS_PROFILES.USER_ID.in(userIds))
			.fetchMap(BUSINESS_PROFILES.USER_ID, BUSINESS_PROFILES.STATUS);
	}

	public List<Agreement> agreements(long userId) {
		return dsl.select(USER_AGREEMENTS.TYPE, USER_AGREEMENTS.VERSION, USER_AGREEMENTS.AGREED,
				USER_AGREEMENTS.CREATED_AT)
			.from(USER_AGREEMENTS)
			.where(USER_AGREEMENTS.USER_ID.eq(userId))
			.orderBy(USER_AGREEMENTS.CREATED_AT.desc(), USER_AGREEMENTS.ID.desc())
			.limit(50)
			.fetch(r -> new Agreement(r.value1(), r.value2(), r.value3(), r.value4()));
	}

	public OrderStats orderStats(long userId) {
		var r = dsl.select(DSL.count(), coalesce(sum(ORDERS.PAY_AMOUNT), val(java.math.BigDecimal.ZERO)),
				max(ORDERS.ORDERED_AT))
			.from(ORDERS)
			.where(ORDERS.USER_ID.eq(userId), ORDERS.STATUS.in(PAID_ORDER))
			.fetchOne();
		return new OrderStats(r.value1(), r.value2().longValue(), r.value3());
	}

	public List<RecentOrder> recentOrders(long userId, int limit) {
		return dsl.select(ORDERS.ORDER_NO, ORDERS.STATUS, ORDERS.PAY_AMOUNT, ORDERS.ORDERED_AT)
			.from(ORDERS)
			.where(ORDERS.USER_ID.eq(userId))
			.orderBy(ORDERS.ORDERED_AT.desc())
			.limit(limit)
			.fetch(r -> new RecentOrder(r.value1(), r.value2(), r.value3(), r.value4()));
	}

	public void updateStatus(long userId, String status) {
		dsl.update(USERS).set(USERS.STATUS, status).where(USERS.ID.eq(userId)).execute();
	}

	/** 운영자 행동 기록. action: SUSPEND_USER, UNSUSPEND_USER, VIEW_PERSONAL_INFO, CREATE_STAFF ... */
	public void audit(long actorId, String action, long userId, Map<String, String> detail, String ip) {
		dsl.insertInto(AUDIT_LOGS)
			.set(AUDIT_LOGS.ACTOR_USER_ID, actorId)
			.set(AUDIT_LOGS.ACTION, action)
			.set(AUDIT_LOGS.TARGET_TYPE, TARGET_USER)
			.set(AUDIT_LOGS.TARGET_ID, userId)
			.set(AUDIT_LOGS.DETAIL, detail == null ? null : JSONB.valueOf(jsonMapper.writeValueAsString(detail)))
			.set(AUDIT_LOGS.IP, UserRepository.toInet(ip))
			.execute();
	}

	public List<StatusLog> statusLogs(long userId) {
		Users actor = USERS.as("actor");
		List<StatusLog> logs = new ArrayList<>();
		dsl.select(AUDIT_LOGS.ACTION, AUDIT_LOGS.DETAIL, actor.NAME, AUDIT_LOGS.CREATED_AT)
			.from(AUDIT_LOGS)
			.leftJoin(actor).on(actor.ID.eq(AUDIT_LOGS.ACTOR_USER_ID))
			.where(AUDIT_LOGS.TARGET_TYPE.eq(TARGET_USER), AUDIT_LOGS.TARGET_ID.eq(userId),
					AUDIT_LOGS.ACTION.in("SUSPEND_USER", "UNSUSPEND_USER"))
			.orderBy(AUDIT_LOGS.CREATED_AT.desc())
			.limit(20)
			.forEach(r -> logs.add(new StatusLog(r.value1(), reason(r.value2()), r.value3(), r.value4())));
		return logs;
	}

	private String reason(JSONB detail) {
		if (detail == null || detail.data() == null) {
			return null;
		}
		JsonNode node = jsonMapper.readTree(detail.data()).get("reason");
		return node == null ? null : node.asString();
	}

}
