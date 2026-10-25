package com.aishop.api.auth;

import java.io.Serializable;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.aishop.api.auth.AuthRequests.SocialSignup;
import com.aishop.api.auth.AuthService.LoginMethods;
import com.aishop.api.common.web.ApiException;

/**
 * 카카오·네이버 간편로그인.
 * 쇼핑몰 도메인의 /api 경로로 오가므로 세션 쿠키가 그대로 이어진다.
 * 처음 온 회원은 약관 동의 화면(/signup/social)을 거쳐 가입한다.
 */
@RestController
public class SocialLoginController {

	static final String STATE_ATTR = "aishop.social.state";
	static final String PENDING_ATTR = "aishop.social.pending";

	/** 로그인 화면으로 보낸 요청 (위조 방지 state, 로그인 뒤 돌아갈 곳) */
	record OAuthState(SocialProvider provider, String state, String next) implements Serializable {
	}

	/** 약관 동의를 기다리는 첫 가입자 */
	record PendingSignup(SocialProfile profile, String next) implements Serializable {
	}

	public record ProviderView(String id, String label) {
	}

	public record PendingView(String provider, String providerLabel, String name, String email) {
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SocialOAuthClient client;
	private final AuthService authService;
	private final LoginSessions sessions;

	public SocialLoginController(SocialOAuthClient client, AuthService authService, LoginSessions sessions) {
		this.client = client;
		this.authService = authService;
		this.sessions = sessions;
	}

	/** 키가 설정된 제공자만 버튼을 보여 준다 */
	@GetMapping("/api/auth/social/providers")
	public List<ProviderView> providers() {
		return client.enabledProviders().stream().map(p -> new ProviderView(p.path(), p.label())).toList();
	}

	@GetMapping("/api/auth/social/{provider}/start")
	public ResponseEntity<Void> start(@PathVariable String provider, @RequestParam(required = false) String next,
			HttpServletRequest request) {
		SocialProvider p = SocialProvider.fromPath(provider).orElse(null);
		if (p == null || !client.isEnabled(p)) {
			return redirect(loginError("SOCIAL_DISABLED", next));
		}
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		request.getSession(true).setAttribute(STATE_ATTR, new OAuthState(p, state, safeNext(next)));
		return redirect(client.authorizeUrl(p, state));
	}

	@GetMapping("/api/auth/social/{provider}/callback")
	public ResponseEntity<Void> callback(@PathVariable String provider, @RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error,
			HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		OAuthState saved = session == null ? null : (OAuthState) session.getAttribute(STATE_ATTR);
		if (session != null) {
			session.removeAttribute(STATE_ATTR);
		}
		String next = saved == null ? "/" : saved.next();
		if (error != null) {
			// 제공자 동의 화면에서 취소
			return redirect(loginError("SOCIAL_CANCELLED", next));
		}
		if (saved == null || state == null || code == null || !saved.state().equals(state)
				|| !saved.provider().path().equals(provider)) {
			return redirect(loginError("SOCIAL_STATE_MISMATCH", next));
		}
		try {
			SocialProfile profile = client.fetchProfile(saved.provider(), code, state);
			Optional<AuthUser> user = authService.socialLogin(profile);
			if (user.isPresent()) {
				sessions.start(user.get(), request, response);
				return redirect(next);
			}
			request.getSession(true).setAttribute(PENDING_ATTR, new PendingSignup(profile, next));
			return redirect(UriComponentsBuilder.fromPath("/signup/social")
				.queryParam("next", next)
				.encode()
				.build()
				.toUriString());
		}
		catch (ApiException ex) {
			return redirect(loginError(ex.getCode(), next));
		}
	}

	/** 약관 동의 화면에 보여 줄 정보 */
	@GetMapping("/api/auth/social/pending")
	public PendingView pending(HttpServletRequest request) {
		SocialProfile p = pending(request.getSession(false)).profile();
		return new PendingView(p.provider().path(), p.provider().label(), p.name(), p.email());
	}

	@PostMapping("/api/auth/social/signup")
	public AuthUser signup(@Valid @RequestBody SocialSignup body, HttpServletRequest request,
			HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		PendingSignup pending = pending(session);
		AuthUser user = authService.socialSignup(pending.profile(), body, request.getRemoteAddr());
		session.removeAttribute(PENDING_ATTR);
		sessions.start(user, request, response);
		return user;
	}

	@GetMapping("/api/me/login-methods")
	public LoginMethods loginMethods(@AuthenticationPrincipal AuthUser user) {
		return authService.loginMethods(user.id());
	}

	private static PendingSignup pending(HttpSession session) {
		PendingSignup pending = session == null ? null : (PendingSignup) session.getAttribute(PENDING_ATTR);
		if (pending == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "SOCIAL_PENDING_EXPIRED",
					"간편로그인 정보가 만료됐습니다. 처음부터 다시 시도해 주세요.");
		}
		return pending;
	}

	/** 다른 사이트로 넘어가지 않도록 우리 사이트 안의 경로만 받는다 */
	static String safeNext(String next) {
		if (next == null || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")
				|| next.startsWith("/api/")) {
			return "/";
		}
		return next;
	}

	private static String loginError(String code, String next) {
		return UriComponentsBuilder.fromPath("/login")
			.queryParam("socialError", code)
			.queryParam("next", safeNext(next))
			.encode()
			.build()
			.toUriString();
	}

	private static ResponseEntity<Void> redirect(String location) {
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
	}

}
