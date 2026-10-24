package com.aishop.api.claim;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.auth.AuthUser;
import com.aishop.api.claim.ClaimViews.CancelItem;
import com.aishop.api.claim.ClaimViews.ClaimView;
import com.aishop.api.claim.ClaimViews.RequestReturn;
import com.aishop.api.claim.ClaimViews.ReturnReason;

/** 고객 상품별 취소·반품 */
@RestController
public class ClaimController {

	private final ClaimService claimService;

	public ClaimController(ClaimService claimService) {
		this.claimService = claimService;
	}

	@GetMapping("/api/return-reasons")
	public List<Map<String, String>> returnReasons() {
		return Arrays.stream(ReturnReason.values())
			.map(r -> Map.of("code", r.name(), "label", r.label(), "fault", r.fault()))
			.toList();
	}

	@PostMapping("/api/orders/{orderNo}/items/{itemId}/cancel")
	public ClaimView cancelItem(@AuthenticationPrincipal AuthUser user, @PathVariable String orderNo,
			@PathVariable long itemId, @Valid @RequestBody(required = false) CancelItem body) {
		return claimService.cancelItemByCustomer(user.id(), orderNo, itemId, body == null ? null : body.reason());
	}

	@PostMapping("/api/orders/{orderNo}/items/{itemId}/return")
	public ClaimView requestReturn(@AuthenticationPrincipal AuthUser user, @PathVariable String orderNo,
			@PathVariable long itemId, @Valid @RequestBody RequestReturn body) {
		return claimService.requestReturn(user.id(), orderNo, itemId, body.reason(), body.detail());
	}

	@PostMapping("/api/claims/{claimId}/withdraw")
	public ClaimView withdraw(@AuthenticationPrincipal AuthUser user, @PathVariable long claimId) {
		return claimService.withdraw(user.id(), claimId);
	}

}
