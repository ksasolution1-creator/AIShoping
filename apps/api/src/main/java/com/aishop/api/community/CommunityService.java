package com.aishop.api.community;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.api.common.web.ApiException;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.community.CommunityViews.AdminQuestion;
import com.aishop.api.community.CommunityViews.AdminReview;
import com.aishop.api.community.CommunityViews.ProductReviews;
import com.aishop.api.community.CommunityViews.QuestionView;
import com.aishop.api.community.CommunityViews.WishItem;
import com.aishop.api.jooq.tables.records.OrderItemsRecord;

/** 구매후기·상품문의·찜 */
@Service
public class CommunityService {

	private final CommunityRepository repo;

	public CommunityService(CommunityRepository repo) {
		this.repo = repo;
	}

	// ------------------------------------------------------------------
	// 구매후기: 구매확정한 주문 상품마다 하나
	// ------------------------------------------------------------------

	@Transactional
	public void writeReview(long userId, String orderNo, long itemId, int rating, String content) {
		OrderItemsRecord item = repo.findOwnItem(userId, orderNo, itemId)
			.orElseThrow(() -> ApiException.notFound("주문 상품"));
		if (!"CONFIRMED".equals(item.getStatus())) {
			throw ApiException.conflict("NOT_CONFIRMED", "구매확정한 상품만 후기를 쓸 수 있습니다.");
		}
		long productId = repo.findProductIdOfSku(item.getSkuId())
			.orElseThrow(() -> ApiException.conflict("NO_PRODUCT", "후기를 남길 수 없는 상품입니다."));
		if (repo.reviewExists(itemId)) {
			throw ApiException.conflict("ALREADY_REVIEWED", "이미 후기를 쓴 상품입니다.");
		}
		try {
			repo.insertReview(itemId, productId, userId, rating, content.strip());
		} catch (DuplicateKeyException e) {
			throw ApiException.conflict("ALREADY_REVIEWED", "이미 후기를 쓴 상품입니다.");
		}
	}

	@Transactional(readOnly = true)
	public ProductReviews productReviews(long productId, int page) {
		requireVisible(productId);
		return new ProductReviews(repo.reviewSummary(productId), repo.findReviews(productId, page, 10));
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminReview> sellerReviews(long sellerId, int page) {
		return repo.findSellerReviews(sellerId, page, 30);
	}

	@Transactional
	public void setReviewHidden(long sellerId, long reviewId, boolean hidden) {
		if (!repo.setReviewHidden(sellerId, reviewId, hidden)) {
			throw ApiException.notFound("후기");
		}
	}

	// ------------------------------------------------------------------
	// 상품문의
	// ------------------------------------------------------------------

	@Transactional
	public void ask(long userId, long productId, String question, boolean secret) {
		requireVisible(productId);
		repo.insertQuestion(productId, userId, question.strip(), secret);
	}

	@Transactional(readOnly = true)
	public PageResponse<QuestionView> questions(long productId, Long viewerId, int page) {
		requireVisible(productId);
		return repo.findQuestions(productId, viewerId, page, 10);
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminQuestion> sellerQuestions(long sellerId, boolean unansweredOnly, int page) {
		return repo.findSellerQuestions(sellerId, unansweredOnly, page, 30);
	}

	@Transactional
	public void answer(long sellerId, long actorId, long questionId, String answer) {
		if (!repo.answerQuestion(sellerId, questionId, actorId, answer.strip())) {
			throw ApiException.notFound("문의");
		}
	}

	@Transactional
	public void deleteQuestion(long sellerId, long questionId) {
		if (!repo.deleteQuestion(sellerId, questionId)) {
			throw ApiException.notFound("문의");
		}
	}

	// ------------------------------------------------------------------
	// 찜
	// ------------------------------------------------------------------

	@Transactional
	public void wish(long userId, long productId) {
		requireVisible(productId);
		repo.addWish(userId, productId);
	}

	@Transactional
	public void unwish(long userId, long productId) {
		repo.removeWish(userId, productId);
	}

	@Transactional(readOnly = true)
	public boolean isWished(long userId, long productId) {
		return repo.isWished(userId, productId);
	}

	@Transactional(readOnly = true)
	public List<WishItem> wishes(long userId) {
		return repo.findWishes(userId);
	}

	private void requireVisible(long productId) {
		if (!repo.productVisible(productId)) {
			throw ApiException.notFound("상품");
		}
	}

}
