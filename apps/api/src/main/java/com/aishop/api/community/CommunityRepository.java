package com.aishop.api.community;

import static com.aishop.api.jooq.Tables.INVENTORIES;
import static com.aishop.api.jooq.Tables.ORDERS;
import static com.aishop.api.jooq.Tables.ORDER_ITEMS;
import static com.aishop.api.jooq.Tables.PRODUCTS;
import static com.aishop.api.jooq.Tables.PRODUCT_IMAGES;
import static com.aishop.api.jooq.Tables.PRODUCT_QUESTIONS;
import static com.aishop.api.jooq.Tables.REVIEWS;
import static com.aishop.api.jooq.Tables.SKUS;
import static com.aishop.api.jooq.Tables.USERS;
import static com.aishop.api.jooq.Tables.WISHLISTS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import com.aishop.api.catalog.ConditionGrade;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.community.CommunityViews.AdminQuestion;
import com.aishop.api.community.CommunityViews.AdminReview;
import com.aishop.api.community.CommunityViews.QuestionView;
import com.aishop.api.community.CommunityViews.ReviewSummary;
import com.aishop.api.community.CommunityViews.ReviewView;
import com.aishop.api.community.CommunityViews.WishItem;
import com.aishop.api.jooq.tables.records.OrderItemsRecord;

@Repository
public class CommunityRepository {

	private static final List<String> SHOP_VISIBLE = List.of("ON_SALE", "SOLD_OUT");

	private final DSLContext dsl;

	public CommunityRepository(DSLContext dsl) {
		this.dsl = dsl;
	}

	public boolean productVisible(long productId) {
		return dsl.fetchExists(PRODUCTS, PRODUCTS.ID.eq(productId), PRODUCTS.STATUS.in(SHOP_VISIBLE));
	}

	// ------------------------------------------------------------------
	// 구매후기
	// ------------------------------------------------------------------

	/** 이 고객의 주문 상품 (구매후기 쓰기 전 확인용) */
	public Optional<OrderItemsRecord> findOwnItem(long userId, String orderNo, long itemId) {
		return dsl.select(ORDER_ITEMS.fields())
			.from(ORDER_ITEMS)
			.join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDER_ID))
			.where(ORDER_ITEMS.ID.eq(itemId), ORDERS.ORDER_NO.eq(orderNo), ORDERS.USER_ID.eq(userId))
			.fetchOptional(r -> r.into(ORDER_ITEMS));
	}

	public Optional<Long> findProductIdOfSku(Long skuId) {
		return skuId == null ? Optional.empty()
				: dsl.select(SKUS.PRODUCT_ID).from(SKUS).where(SKUS.ID.eq(skuId)).fetchOptional(SKUS.PRODUCT_ID);
	}

	public boolean reviewExists(long orderItemId) {
		return dsl.fetchExists(REVIEWS, REVIEWS.ORDER_ITEM_ID.eq(orderItemId));
	}

	public long insertReview(long orderItemId, long productId, long userId, int rating, String content) {
		return dsl.insertInto(REVIEWS)
			.set(REVIEWS.ORDER_ITEM_ID, orderItemId)
			.set(REVIEWS.PRODUCT_ID, productId)
			.set(REVIEWS.USER_ID, userId)
			.set(REVIEWS.RATING, (short) rating)
			.set(REVIEWS.CONTENT, content)
			.returning(REVIEWS.ID)
			.fetchOne(REVIEWS.ID);
	}

	public ReviewSummary reviewSummary(long productId) {
		List<Integer> counts = new ArrayList<>(List.of(0, 0, 0, 0, 0));
		dsl.select(REVIEWS.RATING, count())
			.from(REVIEWS)
			.where(REVIEWS.PRODUCT_ID.eq(productId), REVIEWS.IS_HIDDEN.isFalse())
			.groupBy(REVIEWS.RATING)
			.forEach(r -> counts.set(r.value1() - 1, r.value2()));
		int total = counts.stream().mapToInt(Integer::intValue).sum();
		int sum = 0;
		for (int i = 0; i < 5; i++) {
			sum += counts.get(i) * (i + 1);
		}
		double average = total == 0 ? 0 : Math.round(sum * 10.0 / total) / 10.0;
		return new ReviewSummary(average, total, counts);
	}

	public PageResponse<ReviewView> findReviews(long productId, int page, int size) {
		Condition where = REVIEWS.PRODUCT_ID.eq(productId).and(REVIEWS.IS_HIDDEN.isFalse());
		long total = dsl.fetchCount(REVIEWS, where);
		List<ReviewView> items = dsl.select(REVIEWS.ID, REVIEWS.RATING, REVIEWS.CONTENT, USERS.NAME,
				ORDER_ITEMS.OPTION_TEXT, ORDER_ITEMS.CONDITION_GRADE, REVIEWS.CREATED_AT)
			.from(REVIEWS)
			.join(USERS).on(USERS.ID.eq(REVIEWS.USER_ID))
			.join(ORDER_ITEMS).on(ORDER_ITEMS.ID.eq(REVIEWS.ORDER_ITEM_ID))
			.where(where)
			.orderBy(REVIEWS.CREATED_AT.desc(), REVIEWS.ID.desc())
			.limit(size)
			.offset(page * size)
			.fetch(r -> new ReviewView(r.value1(), r.value2(), r.value3(), CommunityViews.mask(r.value4()),
					r.value5(), gradeLabel(r.value6()), r.value7()));
		return new PageResponse<>(items, page, size, total);
	}

	public PageResponse<AdminReview> findSellerReviews(long sellerId, int page, int size) {
		Condition where = PRODUCTS.SELLER_ID.eq(sellerId);
		long total = dsl.fetchCount(dsl.select(REVIEWS.ID).from(REVIEWS).join(PRODUCTS)
			.on(PRODUCTS.ID.eq(REVIEWS.PRODUCT_ID)).where(where));
		List<AdminReview> items = dsl.select(REVIEWS.ID, REVIEWS.PRODUCT_ID, PRODUCTS.NAME, USERS.NAME, REVIEWS.RATING,
				REVIEWS.CONTENT, REVIEWS.IS_HIDDEN, REVIEWS.CREATED_AT)
			.from(REVIEWS)
			.join(PRODUCTS).on(PRODUCTS.ID.eq(REVIEWS.PRODUCT_ID))
			.join(USERS).on(USERS.ID.eq(REVIEWS.USER_ID))
			.where(where)
			.orderBy(REVIEWS.CREATED_AT.desc())
			.limit(size)
			.offset(page * size)
			.fetch(r -> new AdminReview(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(),
					r.value7(), r.value8()));
		return new PageResponse<>(items, page, size, total);
	}

	public boolean setReviewHidden(long sellerId, long reviewId, boolean hidden) {
		return dsl.update(REVIEWS)
			.set(REVIEWS.IS_HIDDEN, hidden)
			.where(REVIEWS.ID.eq(reviewId))
			.and(REVIEWS.PRODUCT_ID.in(select(PRODUCTS.ID).from(PRODUCTS).where(PRODUCTS.SELLER_ID.eq(sellerId))))
			.execute() == 1;
	}

	// ------------------------------------------------------------------
	// 상품문의
	// ------------------------------------------------------------------

	public long insertQuestion(long productId, long userId, String question, boolean secret) {
		return dsl.insertInto(PRODUCT_QUESTIONS)
			.set(PRODUCT_QUESTIONS.PRODUCT_ID, productId)
			.set(PRODUCT_QUESTIONS.USER_ID, userId)
			.set(PRODUCT_QUESTIONS.QUESTION, question)
			.set(PRODUCT_QUESTIONS.IS_SECRET, secret)
			.returning(PRODUCT_QUESTIONS.ID)
			.fetchOne(PRODUCT_QUESTIONS.ID);
	}

	/** 비밀글은 작성자 본인에게만 내용을 보여 준다 */
	public PageResponse<QuestionView> findQuestions(long productId, Long viewerId, int page, int size) {
		Condition where = PRODUCT_QUESTIONS.PRODUCT_ID.eq(productId);
		long total = dsl.fetchCount(PRODUCT_QUESTIONS, where);
		List<QuestionView> items = dsl.select(PRODUCT_QUESTIONS.ID, USERS.NAME, PRODUCT_QUESTIONS.QUESTION,
				PRODUCT_QUESTIONS.IS_SECRET, PRODUCT_QUESTIONS.USER_ID, PRODUCT_QUESTIONS.ANSWER,
				PRODUCT_QUESTIONS.ANSWERED_AT, PRODUCT_QUESTIONS.CREATED_AT)
			.from(PRODUCT_QUESTIONS)
			.join(USERS).on(USERS.ID.eq(PRODUCT_QUESTIONS.USER_ID))
			.where(where)
			.orderBy(PRODUCT_QUESTIONS.CREATED_AT.desc(), PRODUCT_QUESTIONS.ID.desc())
			.limit(size)
			.offset(page * size)
			.fetch(r -> {
				boolean mine = viewerId != null && viewerId.equals(r.value5());
				boolean hide = r.value4() && !mine;
				return new QuestionView(r.value1(), CommunityViews.mask(r.value2()), hide ? null : r.value3(),
						r.value4(), mine, hide ? null : r.value6(), r.value7(), r.value8());
			});
		return new PageResponse<>(items, page, size, total);
	}

	public PageResponse<AdminQuestion> findSellerQuestions(long sellerId, boolean unansweredOnly, int page, int size) {
		Condition where = PRODUCTS.SELLER_ID.eq(sellerId)
			.and(unansweredOnly ? PRODUCT_QUESTIONS.ANSWER.isNull() : noCondition());
		long total = dsl.fetchCount(dsl.select(PRODUCT_QUESTIONS.ID).from(PRODUCT_QUESTIONS).join(PRODUCTS)
			.on(PRODUCTS.ID.eq(PRODUCT_QUESTIONS.PRODUCT_ID)).where(where));
		List<AdminQuestion> items = dsl.select(PRODUCT_QUESTIONS.ID, PRODUCT_QUESTIONS.PRODUCT_ID, PRODUCTS.NAME,
				USERS.NAME, PRODUCT_QUESTIONS.QUESTION, PRODUCT_QUESTIONS.IS_SECRET, PRODUCT_QUESTIONS.ANSWER,
				PRODUCT_QUESTIONS.ANSWERED_AT, PRODUCT_QUESTIONS.CREATED_AT)
			.from(PRODUCT_QUESTIONS)
			.join(PRODUCTS).on(PRODUCTS.ID.eq(PRODUCT_QUESTIONS.PRODUCT_ID))
			.join(USERS).on(USERS.ID.eq(PRODUCT_QUESTIONS.USER_ID))
			.where(where)
			// 답변 안 한 문의를 오래된 것부터 먼저
			.orderBy(PRODUCT_QUESTIONS.ANSWER.isNull().desc(), PRODUCT_QUESTIONS.CREATED_AT)
			.limit(size)
			.offset(page * size)
			.fetch(r -> new AdminQuestion(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(),
					r.value7(), r.value8(), r.value9()));
		return new PageResponse<>(items, page, size, total);
	}

	public boolean answerQuestion(long sellerId, long questionId, long actorId, String answer) {
		return dsl.update(PRODUCT_QUESTIONS)
			.set(PRODUCT_QUESTIONS.ANSWER, answer)
			.set(PRODUCT_QUESTIONS.ANSWERED_BY, actorId)
			.set(PRODUCT_QUESTIONS.ANSWERED_AT, currentOffsetDateTime())
			.where(PRODUCT_QUESTIONS.ID.eq(questionId))
			.and(PRODUCT_QUESTIONS.PRODUCT_ID
				.in(select(PRODUCTS.ID).from(PRODUCTS).where(PRODUCTS.SELLER_ID.eq(sellerId))))
			.execute() == 1;
	}

	// ------------------------------------------------------------------
	// 찜
	// ------------------------------------------------------------------

	public void addWish(long userId, long productId) {
		dsl.insertInto(WISHLISTS)
			.set(WISHLISTS.USER_ID, userId)
			.set(WISHLISTS.PRODUCT_ID, productId)
			.onConflictDoNothing()
			.execute();
	}

	public void removeWish(long userId, long productId) {
		dsl.deleteFrom(WISHLISTS).where(WISHLISTS.USER_ID.eq(userId), WISHLISTS.PRODUCT_ID.eq(productId)).execute();
	}

	public boolean isWished(long userId, long productId) {
		return dsl.fetchExists(WISHLISTS, WISHLISTS.USER_ID.eq(userId), WISHLISTS.PRODUCT_ID.eq(productId));
	}

	public List<WishItem> findWishes(long userId) {
		Field<Long> minPrice = field(select(min(SKUS.PRICE)).from(SKUS)
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.eq("ACTIVE")));
		Field<BigDecimal> available = field(select(coalesce(sum(INVENTORIES.ON_HAND.minus(INVENTORIES.RESERVED)),
				BigDecimal.ZERO))
			.from(INVENTORIES)
			.join(SKUS).on(SKUS.ID.eq(INVENTORIES.SKU_ID))
			.where(SKUS.PRODUCT_ID.eq(PRODUCTS.ID), SKUS.STATUS.eq("ACTIVE")));
		Field<String> thumbnail = field(select(PRODUCT_IMAGES.URL).from(PRODUCT_IMAGES)
			.where(PRODUCT_IMAGES.PRODUCT_ID.eq(PRODUCTS.ID))
			.orderBy(PRODUCT_IMAGES.SKU_ID.nullsFirst(), PRODUCT_IMAGES.SORT_ORDER, PRODUCT_IMAGES.ID)
			.limit(1));
		return dsl.select(PRODUCTS.ID, PRODUCTS.NAME, thumbnail, minPrice, available, PRODUCTS.STATUS,
				WISHLISTS.CREATED_AT)
			.from(WISHLISTS)
			.join(PRODUCTS).on(PRODUCTS.ID.eq(WISHLISTS.PRODUCT_ID))
			.where(WISHLISTS.USER_ID.eq(userId), PRODUCTS.STATUS.ne("DELETED"))
			.orderBy(WISHLISTS.CREATED_AT.desc())
			.fetch(r -> new WishItem(r.value1(), r.value2(), r.value3(), r.value4(),
					r.value5() == null ? 0 : Math.max(r.value5().intValue(), 0), r.value6(), r.value7()));
	}

	private static String gradeLabel(String code) {
		try {
			return code == null ? null : ConditionGrade.valueOf(code).label();
		} catch (IllegalArgumentException e) {
			return code;
		}
	}

}
