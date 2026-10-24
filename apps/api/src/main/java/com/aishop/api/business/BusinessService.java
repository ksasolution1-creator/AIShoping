package com.aishop.api.business;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.business.BusinessViews.AdminApplication;
import com.aishop.api.business.BusinessViews.ApplyForm;
import com.aishop.api.business.BusinessViews.Profile;
import com.aishop.api.business.BusinessViews.ProductBusinessTerms;
import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.jooq.tables.records.BusinessProfilesRecord;

/**
 * 사업자 회원 인증. 고객이 사업자 정보를 내면 운영자가 홈택스 등에서 확인하고 승인한다.
 * 승인된 회원만 사업자 전용 상품을 사고 사업자 회원가를 받는다.
 */
@Service
public class BusinessService {

	private final BusinessRepository repo;

	public BusinessService(BusinessRepository repo) {
		this.repo = repo;
	}

	@Transactional(readOnly = true)
	public Profile profile(long userId) {
		return repo.find(userId).map(BusinessRepository::toProfile).orElse(Profile.NONE);
	}

	@Transactional
	public Profile apply(long userId, boolean customer, ApplyForm form) {
		if (!customer) {
			throw ApiException.badRequest("NOT_CUSTOMER", "고객 계정만 사업자 인증을 신청할 수 있습니다.");
		}
		String businessNo = BusinessViews.normalizeBusinessNo(form.businessNo());
		if (!BusinessViews.validBusinessNo(businessNo)) {
			throw ApiException.badRequest("INVALID_BUSINESS_NO", "사업자등록번호가 올바르지 않습니다. 숫자 10자리를 확인해 주세요.");
		}
		BusinessProfilesRecord current = repo.lock(userId).orElse(null);
		if (current != null && "APPROVED".equals(current.getStatus())) {
			throw ApiException.conflict("ALREADY_APPROVED", "이미 사업자 인증을 마쳤습니다.");
		}
		if (repo.approvedByOther(businessNo, userId)) {
			throw ApiException.conflict("BUSINESS_NO_IN_USE", "이미 다른 회원이 인증한 사업자등록번호입니다. 고객센터로 문의해 주세요.");
		}
		repo.upsert(userId, businessNo, form);
		return profile(userId);
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminApplication> applications(String status, int page) {
		return repo.findApplications(status, page, 30);
	}

	@Transactional
	public void review(long userId, long actorId, boolean approve, String rejectReason) {
		BusinessProfilesRecord current = repo.lock(userId).orElseThrow(() -> ApiException.notFound("사업자 인증 신청"));
		if (approve && repo.approvedByOther(current.getBusinessNo(), userId)) {
			throw ApiException.conflict("BUSINESS_NO_IN_USE", "같은 사업자등록번호로 이미 승인된 회원이 있습니다.");
		}
		if (!approve && (rejectReason == null || rejectReason.isBlank())) {
			throw ApiException.badRequest("REASON_REQUIRED", "거절 사유를 입력해 주세요. 고객에게 그대로 보입니다.");
		}
		repo.review(userId, approve ? "APPROVED" : "REJECTED", approve ? null : rejectReason.strip(), actorId);
	}

	@Transactional
	public void updateProductTerms(long productId, ProductBusinessTerms terms) {
		repo.updateProductFlags(productId, terms.businessOnly(), terms.imageReuseAllowed());
		if (terms.skuPrices() != null) {
			repo.updateBusinessPrices(productId, terms.skuPrices());
		}
	}

}
