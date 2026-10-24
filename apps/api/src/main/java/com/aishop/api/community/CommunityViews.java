package com.aishop.api.community;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.aishop.api.common.web.PageResponse;

/** 구매후기·상품문의·찜 요청과 응답 */
public final class CommunityViews {

	private CommunityViews() {
	}

	// ------------------------------------------------------------------
	// 구매후기
	// ------------------------------------------------------------------

	public record ReviewForm(
			@NotNull(message = "별점을 골라 주세요.") @Min(1) @Max(5) Integer rating,
			@NotBlank(message = "후기를 입력해 주세요.") @Size(min = 10, max = 1000, message = "후기는 10~1000자로 써 주세요.") String content) {
	}

	/** @param author 가운데를 가린 작성자 이름 (예: 김*혁) */
	public record ReviewView(long id, int rating, String content, String author, String optionText, String grade,
			OffsetDateTime createdAt) {
	}

	/** @param counts 별점별 개수 (1점부터 5점 순서) */
	public record ReviewSummary(double average, int count, List<Integer> counts) {
	}

	public record ProductReviews(ReviewSummary summary, PageResponse<ReviewView> page) {
	}

	public record AdminReview(long id, long productId, String productName, String author, int rating, String content,
			boolean hidden, OffsetDateTime createdAt) {
	}

	public record HideReview(@NotNull Boolean hidden) {
	}

	// ------------------------------------------------------------------
	// 상품문의
	// ------------------------------------------------------------------

	public record QuestionForm(
			@NotBlank(message = "문의 내용을 입력해 주세요.") @Size(min = 5, max = 1000, message = "문의는 5~1000자로 써 주세요.") String question,
			Boolean secret) {
	}

	/**
	 * @param question 비밀글이고 내 글이 아니면 null
	 * @param answer 비밀글이고 내 글이 아니면 null
	 */
	public record QuestionView(long id, String author, String question, boolean secret, boolean mine, String answer,
			OffsetDateTime answeredAt, OffsetDateTime createdAt) {
	}

	public record AdminQuestion(long id, long productId, String productName, String author, String question,
			boolean secret, String answer, OffsetDateTime answeredAt, OffsetDateTime createdAt) {
	}

	public record AnswerForm(
			@NotBlank(message = "답변을 입력해 주세요.") @Size(max = 2000, message = "답변은 2000자까지 쓸 수 있습니다.") String answer) {
	}

	// ------------------------------------------------------------------
	// 찜
	// ------------------------------------------------------------------

	public record WishItem(long productId, String name, String thumbnailUrl, Long minPrice, int availableQty,
			String status, OffsetDateTime createdAt) {
	}

	/** 작성자 이름 가리기: 두 글자면 뒤 한 글자, 세 글자 이상이면 처음과 끝만 남긴다 */
	static String mask(String name) {
		if (name == null || name.isBlank()) {
			return "구매자";
		}
		String n = name.strip();
		if (n.length() == 1) {
			return n;
		}
		if (n.length() == 2) {
			return n.charAt(0) + "*";
		}
		return n.charAt(0) + "*".repeat(n.length() - 2) + n.charAt(n.length() - 1);
	}

}
