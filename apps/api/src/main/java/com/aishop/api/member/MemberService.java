package com.aishop.api.member;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.aishop.api.auth.SessionRevoker;
import com.aishop.api.business.BusinessRepository;
import com.aishop.api.common.crypto.FieldCipher;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.jooq.tables.records.UsersRecord;
import com.aishop.api.member.MemberViews.ChangeStatus;
import com.aishop.api.member.MemberViews.MemberDetail;
import com.aishop.api.member.MemberViews.MemberRow;
import com.aishop.api.member.MemberViews.MemberSearch;

/** 운영자 회원 관리: 목록·상세·이용 정지 */
@Service
public class MemberService {

	private final MemberRepository members;
	private final BusinessRepository business;
	private final FieldCipher cipher;
	private final SessionRevoker sessionRevoker;

	public MemberService(MemberRepository members, BusinessRepository business, FieldCipher cipher,
			SessionRevoker sessionRevoker) {
		this.members = members;
		this.business = business;
		this.cipher = cipher;
		this.sessionRevoker = sessionRevoker;
	}

	@Transactional(readOnly = true)
	public PageResponse<MemberRow> search(MemberSearch search) {
		List<UsersRecord> users = members.search(search);
		long total = members.count(search);
		List<Long> ids = users.stream().map(UsersRecord::getId).toList();
		Map<Long, List<String>> social = ids.isEmpty() ? Map.of() : members.socialProviders(ids);
		Map<Long, String> businessStatus = ids.isEmpty() ? Map.of() : members.businessStatuses(ids);
		List<MemberRow> rows = users.stream()
			.map(u -> new MemberRow(u.getId(), u.getName(), u.getEmail(),
					joinMethods(u, social.getOrDefault(u.getId(), List.of())),
					MemberViews.maskPhone(cipher.decrypt(u.getPhoneEnc())), u.getStatus(),
					businessStatus.get(u.getId()), u.getCreatedAt(), u.getLastLoginAt()))
			.toList();
		return new PageResponse<>(rows, search.page(), search.size(), total);
	}

	/** 상세는 연락처 전체를 보여주므로 열람 기록을 남긴다 */
	@Transactional
	public MemberDetail detail(long userId, long actorId, String ip) {
		UsersRecord u = members.findCustomer(userId).orElseThrow(() -> ApiException.notFound("회원"));
		String phone = cipher.decrypt(u.getPhoneEnc());
		if (phone != null) {
			members.audit(actorId, "VIEW_PERSONAL_INFO", userId, null, ip);
		}
		return new MemberDetail(u.getId(), u.getName(), u.getEmail(), phone,
				joinMethods(u, members.socialProviders(List.of(userId)).getOrDefault(userId, List.of())),
				u.getStatus(), u.getCreatedAt(), u.getLastLoginAt(), u.getWithdrawnAt(),
				u.getMarketingAgreedAt() != null, u.getMarketingAgreedAt(), members.agreements(userId),
				business.find(userId).map(BusinessRepository::toProfile).orElse(null),
				members.orderStats(userId), members.recentOrders(userId, 10), members.statusLogs(userId));
	}

	@Transactional
	public MemberDetail changeStatus(long userId, ChangeStatus request, long actorId, String ip) {
		UsersRecord u = members.lockCustomer(userId).orElseThrow(() -> ApiException.notFound("회원"));
		if ("WITHDRAWN".equals(u.getStatus())) {
			throw ApiException.badRequest("WITHDRAWN_USER", "탈퇴한 회원은 상태를 바꿀 수 없습니다.");
		}
		if (request.status().equals(u.getStatus())) {
			throw ApiException.badRequest("SAME_STATUS",
					"SUSPENDED".equals(u.getStatus()) ? "이미 정지된 회원입니다." : "이미 정상 이용 중인 회원입니다.");
		}
		boolean suspend = "SUSPENDED".equals(request.status());
		members.updateStatus(userId, request.status());
		members.audit(actorId, suspend ? "SUSPEND_USER" : "UNSUSPEND_USER", userId,
				Map.of("reason", request.reason().strip()), ip);
		if (suspend) {
			// 커밋된 뒤에 끊는다 (DB 저장이 실패했는데 세션만 끊기지 않도록)
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					sessionRevoker.revoke(userId);
				}
			});
		}
		return detail(userId, actorId, ip);
	}

	/** 비밀번호가 있으면 이메일 가입, 그 뒤에 연결된 간편로그인 */
	private static List<String> joinMethods(UsersRecord u, List<String> social) {
		List<String> methods = new ArrayList<>();
		if (u.getPasswordHash() != null) {
			methods.add("EMAIL");
		}
		methods.addAll(social);
		return methods;
	}

}
