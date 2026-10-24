package com.aishop.api.business;

import static com.aishop.api.jooq.Tables.BUSINESS_PROFILES;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.SKUS;
import static com.aishop.api.jooq.Tables.USERS;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.noCondition;

import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import com.aishop.api.business.BusinessViews.AdminApplication;
import com.aishop.api.business.BusinessViews.ApplyForm;
import com.aishop.api.business.BusinessViews.Profile;
import com.aishop.api.business.BusinessViews.SkuBusinessPrice;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.jooq.tables.records.BusinessProfilesRecord;

@Repository
public class BusinessRepository {

	private final DSLContext dsl;

	public BusinessRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	/** 승인된 사업자 회원인지 (가격·구매 자격 판단) */
	public boolean isApprovedBusiness(Long userId) {
		return userId != null && dsl.fetchExists(BUSINESS_PROFILES, BUSINESS_PROFILES.USER_ID.eq(userId),
				BUSINESS_PROFILES.STATUS.eq("APPROVED"));
	}

	public Optional<BusinessProfilesRecord> find(long userId) {
		return dsl.selectFrom(BUSINESS_PROFILES).where(BUSINESS_PROFILES.USER_ID.eq(userId)).fetchOptional();
	}

	public Optional<BusinessProfilesRecord> lock(long userId) {
		return dsl.selectFrom(BUSINESS_PROFILES).where(BUSINESS_PROFILES.USER_ID.eq(userId)).forUpdate().fetchOptional();
	}

	public boolean approvedByOther(String businessNo, long userId) {
		return dsl.fetchExists(BUSINESS_PROFILES, BUSINESS_PROFILES.BUSINESS_NO.eq(businessNo),
				BUSINESS_PROFILES.STATUS.eq("APPROVED"), BUSINESS_PROFILES.USER_ID.ne(userId));
	}

	/** 신청 또는 다시 신청 (거절된 뒤 고쳐서 내는 경우) */
	public void upsert(long userId, String businessNo, ApplyForm form) {
		String item = form.businessItem() == null || form.businessItem().isBlank() ? null : form.businessItem().strip();
		dsl.insertInto(BUSINESS_PROFILES)
			.set(BUSINESS_PROFILES.USER_ID, userId)
			.set(BUSINESS_PROFILES.BUSINESS_NO, businessNo)
			.set(BUSINESS_PROFILES.BUSINESS_NAME, form.businessName().strip())
			.set(BUSINESS_PROFILES.CEO_NAME, form.ceoName().strip())
			.set(BUSINESS_PROFILES.BUSINESS_ITEM, item)
			.onConflict(BUSINESS_PROFILES.USER_ID)
			.doUpdate()
			.set(BUSINESS_PROFILES.BUSINESS_NO, businessNo)
			.set(BUSINESS_PROFILES.BUSINESS_NAME, form.businessName().strip())
			.set(BUSINESS_PROFILES.CEO_NAME, form.ceoName().strip())
			.set(BUSINESS_PROFILES.BUSINESS_ITEM, item)
			.set(BUSINESS_PROFILES.STATUS, "PENDING")
			.setNull(BUSINESS_PROFILES.REJECT_REASON)
			.setNull(BUSINESS_PROFILES.REVIEWED_BY)
			.setNull(BUSINESS_PROFILES.REVIEWED_AT)
			.execute();
	}

	public void review(long userId, String status, String rejectReason, long actorId) {
		dsl.update(BUSINESS_PROFILES)
			.set(BUSINESS_PROFILES.STATUS, status)
			.set(BUSINESS_PROFILES.REJECT_REASON, rejectReason)
			.set(BUSINESS_PROFILES.REVIEWED_BY, actorId)
			.set(BUSINESS_PROFILES.REVIEWED_AT, currentOffsetDateTime())
			.where(BUSINESS_PROFILES.USER_ID.eq(userId))
			.execute();
	}

	public static Profile toProfile(BusinessProfilesRecord r) {
		return new Profile(r.getStatus(), r.getBusinessNo(), r.getBusinessName(), r.getCeoName(), r.getBusinessItem(),
				r.getRejectReason(), r.getCreatedAt(), r.getReviewedAt());
	}

	public PageResponse<AdminApplication> findApplications(String status, int page, int size) {
		Condition where = status == null ? noCondition() : BUSINESS_PROFILES.STATUS.eq(status);
		long total = dsl.fetchCount(BUSINESS_PROFILES, where);
		List<AdminApplication> items = dsl.select(BUSINESS_PROFILES.USER_ID, USERS.EMAIL, USERS.NAME,
				BUSINESS_PROFILES.BUSINESS_NO, BUSINESS_PROFILES.BUSINESS_NAME, BUSINESS_PROFILES.CEO_NAME,
				BUSINESS_PROFILES.BUSINESS_ITEM, BUSINESS_PROFILES.STATUS, BUSINESS_PROFILES.REJECT_REASON,
				BUSINESS_PROFILES.CREATED_AT, BUSINESS_PROFILES.REVIEWED_AT)
			.from(BUSINESS_PROFILES)
			.join(USERS).on(USERS.ID.eq(BUSINESS_PROFILES.USER_ID))
			.where(where)
			.orderBy(BUSINESS_PROFILES.STATUS.eq("PENDING").desc(), BUSINESS_PROFILES.UPDATED_AT.desc())
			.limit(size)
			.offset(page * size)
			.fetch(r -> new AdminApplication(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(),
					r.value7(), r.value8(), r.value9(), r.value10(), r.value11()));
		return new PageResponse<>(items, page, size, total);
	}

	// ------------------------------------------------------------------
	// 상품의 사업자 조건
	// ------------------------------------------------------------------

	public void updateProductFlags(long productId, boolean businessOnly, boolean imageReuseAllowed) {
		dsl.update(PRODUCTS)
			.set(PRODUCTS.BUSINESS_ONLY, businessOnly)
			.set(PRODUCTS.IMAGE_REUSE_ALLOWED, imageReuseAllowed)
			.where(PRODUCTS.ID.eq(productId))
			.execute();
	}

	/** 이 상품의 SKU만 바꾼다 (다른 상품 SKU ID가 섞여 들어와도 무시) */
	public void updateBusinessPrices(long productId, List<SkuBusinessPrice> prices) {
		for (SkuBusinessPrice p : prices) {
			dsl.update(SKUS)
				.set(SKUS.BUSINESS_PRICE, p.businessPrice())
				.where(SKUS.ID.eq(p.skuId()), SKUS.PRODUCT_ID.eq(productId))
				.execute();
		}
	}

}
