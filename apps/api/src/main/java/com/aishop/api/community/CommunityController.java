package com.aishop.api.community;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.auth.AuthUser;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.community.CommunityViews.ProductReviews;
import com.aishop.api.community.CommunityViews.QuestionForm;
import com.aishop.api.community.CommunityViews.QuestionView;
import com.aishop.api.community.CommunityViews.ReviewForm;
import com.aishop.api.community.CommunityViews.WishItem;

/** 쇼핑몰 구매후기·상품문의·찜. 목록 조회는 누구나, 쓰기는 로그인한 고객만 */
@RestController
public class CommunityController {

	private final CommunityService service;

	public CommunityController(CommunityService service) {
		this.service = service;
	}

	@GetMapping("/api/products/{productId}/reviews")
	public ProductReviews reviews(@PathVariable long productId, @RequestParam(defaultValue = "0") int page) {
		return service.productReviews(productId, Math.max(page, 0));
	}

	@PostMapping("/api/orders/{orderNo}/items/{itemId}/review")
	@ResponseStatus(HttpStatus.CREATED)
	public void writeReview(@AuthenticationPrincipal AuthUser user, @PathVariable String orderNo,
			@PathVariable long itemId, @Valid @RequestBody ReviewForm body) {
		service.writeReview(user.id(), orderNo, itemId, body.rating(), body.content());
	}

	@GetMapping("/api/products/{productId}/questions")
	public PageResponse<QuestionView> questions(@AuthenticationPrincipal AuthUser user, @PathVariable long productId,
			@RequestParam(defaultValue = "0") int page) {
		return service.questions(productId, user == null ? null : user.id(), Math.max(page, 0));
	}

	@PostMapping("/api/products/{productId}/questions")
	@ResponseStatus(HttpStatus.CREATED)
	public void ask(@AuthenticationPrincipal AuthUser user, @PathVariable long productId,
			@Valid @RequestBody QuestionForm body) {
		service.ask(user.id(), productId, body.question(), Boolean.TRUE.equals(body.secret()));
	}

	@GetMapping("/api/me/wishlist")
	public List<WishItem> wishes(@AuthenticationPrincipal AuthUser user) {
		return service.wishes(user.id());
	}

	@GetMapping("/api/me/wishlist/{productId}")
	public Map<String, Boolean> isWished(@AuthenticationPrincipal AuthUser user, @PathVariable long productId) {
		return Map.of("wished", service.isWished(user.id(), productId));
	}

	@PutMapping("/api/me/wishlist/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void wish(@AuthenticationPrincipal AuthUser user, @PathVariable long productId) {
		service.wish(user.id(), productId);
	}

	@DeleteMapping("/api/me/wishlist/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unwish(@AuthenticationPrincipal AuthUser user, @PathVariable long productId) {
		service.unwish(user.id(), productId);
	}

}
