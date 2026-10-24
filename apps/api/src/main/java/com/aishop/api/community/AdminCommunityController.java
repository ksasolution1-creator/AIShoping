package com.aishop.api.community;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.PageResponse;
import com.aishop.api.community.CommunityViews.AdminQuestion;
import com.aishop.api.community.CommunityViews.AdminReview;
import com.aishop.api.community.CommunityViews.AnswerForm;
import com.aishop.api.community.CommunityViews.HideReview;

/** 운영자 상품문의 답변, 구매후기 숨김 */
@RestController
@RequestMapping("/api/admin")
public class AdminCommunityController {

	private final CommunityService service;
	private final AdminContext admin;

	public AdminCommunityController(CommunityService service, AdminContext admin) {
		this.service = service;
		this.admin = admin;
	}

	@GetMapping("/questions")
	public PageResponse<AdminQuestion> questions(@RequestParam(defaultValue = "true") boolean unanswered,
			@RequestParam(defaultValue = "0") int page) {
		return service.sellerQuestions(admin.sellerId(), unanswered, Math.max(page, 0));
	}

	@PostMapping("/questions/{id}/answer")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void answer(@PathVariable long id, @Valid @RequestBody AnswerForm body) {
		service.answer(admin.sellerId(), admin.user().id(), id, body.answer());
	}

	@GetMapping("/reviews")
	public PageResponse<AdminReview> reviews(@RequestParam(defaultValue = "0") int page) {
		return service.sellerReviews(admin.sellerId(), Math.max(page, 0));
	}

	@PostMapping("/reviews/{id}/hidden")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void hide(@PathVariable long id, @Valid @RequestBody HideReview body) {
		service.setReviewHidden(admin.sellerId(), id, body.hidden());
	}

}
