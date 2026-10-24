package com.aishop.api.cart;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.auth.AuthUser;
import com.aishop.api.cart.CartService.CartView;

/** 장바구니 (로그인 필요) */
@RestController
@RequestMapping("/api/cart")
public class CartController {

	private final CartService cartService;

	public CartController(CartService cartService) {
		this.cartService = cartService;
	}

	public record AddItem(@NotNull Long skuId, @NotNull @Min(1) @Max(CartService.MAX_QTY_PER_ITEM) Integer qty) {
	}

	public record ChangeQty(@NotNull @Min(1) @Max(CartService.MAX_QTY_PER_ITEM) Integer qty) {
	}

	@GetMapping
	public CartView view(@AuthenticationPrincipal AuthUser user) {
		return cartService.view(user.id());
	}

	@PostMapping("/items")
	public CartView add(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AddItem body) {
		return cartService.add(user.id(), body.skuId(), body.qty());
	}

	@PatchMapping("/items/{skuId}")
	public CartView changeQty(@AuthenticationPrincipal AuthUser user, @PathVariable long skuId,
			@Valid @RequestBody ChangeQty body) {
		return cartService.changeQty(user.id(), skuId, body.qty());
	}

	@DeleteMapping("/items")
	public CartView remove(@AuthenticationPrincipal AuthUser user, @RequestParam List<Long> skuIds) {
		return cartService.remove(user.id(), skuIds);
	}

}
