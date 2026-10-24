package com.aishop.api.claim;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.claim.ClaimViews.ApproveReturn;
import com.aishop.api.claim.ClaimViews.CancelItem;
import com.aishop.api.claim.ClaimViews.ClaimView;
import com.aishop.api.claim.ClaimViews.CompleteReturn;
import com.aishop.api.claim.ClaimViews.RejectClaim;
import com.aishop.api.common.web.AdminContext;
import com.aishop.api.common.web.PageResponse;

/** 운영자 취소·반품 처리 */
@RestController
@RequestMapping("/api/admin")
public class AdminClaimController {

	private final ClaimService claimService;
	private final AdminContext admin;

	public AdminClaimController(ClaimService claimService, AdminContext admin) {
		this.claimService = claimService;
		this.admin = admin;
	}

	/** status는 쉼표로 여러 개 (예: REQUESTED,COLLECTING) */
	@GetMapping("/claims")
	public PageResponse<ClaimView> list(@RequestParam(required = false) List<String> status,
			@RequestParam(required = false) String type, @RequestParam(defaultValue = "0") int page) {
		return claimService.sellerClaims(admin.sellerId(), status, type == null || type.isBlank() ? null : type,
				Math.max(page, 0), 30);
	}

	@PostMapping("/orders/{orderNo}/items/{itemId}/cancel")
	public ClaimView cancelItem(@PathVariable String orderNo, @PathVariable long itemId,
			@Valid @RequestBody(required = false) CancelItem body) {
		return claimService.cancelItemBySeller(admin.user().id(), admin.sellerId(), orderNo, itemId,
				body == null ? null : body.reason());
	}

	@PostMapping("/claims/{claimId}/approve")
	public ClaimView approve(@PathVariable long claimId, @Valid @RequestBody(required = false) ApproveReturn body) {
		return claimService.approve(admin.user().id(), admin.sellerId(), claimId,
				body == null ? null : body.pickupCarrierId(), body == null ? null : body.pickupTrackingNo());
	}

	@PostMapping("/claims/{claimId}/reject")
	public ClaimView reject(@PathVariable long claimId, @Valid @RequestBody RejectClaim body) {
		return claimService.reject(admin.user().id(), admin.sellerId(), claimId, body.reason());
	}

	@PostMapping("/claims/{claimId}/complete")
	public ClaimView complete(@PathVariable long claimId, @Valid @RequestBody CompleteReturn body) {
		return claimService.completeReturn(admin.user().id(), admin.sellerId(), claimId, body);
	}

}
