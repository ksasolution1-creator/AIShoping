package com.aishop.api.auth;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.api.auth.AuthRequests.ChangePassword;
import com.aishop.api.auth.AuthRequests.Login;
import com.aishop.api.auth.AuthRequests.Signup;
import com.aishop.api.auth.AuthRequests.Withdraw;
import com.aishop.api.common.web.ApiException;

@RestController
public class AuthController {

	private final AuthService authService;
	private final SecurityContextRepository contextRepository;
	private final CookieCsrfTokenRepository csrfTokenRepository;
	private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

	public AuthController(AuthService authService, SecurityContextRepository contextRepository,
			CookieCsrfTokenRepository csrfTokenRepository) {
		this.authService = authService;
		this.contextRepository = contextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	/** XSRF-TOKEN 쿠키를 발급한다. 변경 요청 전에 쿠키가 없으면 먼저 호출한다. */
	@GetMapping("/api/auth/csrf")
	public Map<String, String> csrf(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
	}

	@GetMapping("/api/auth/me")
	public AuthUser me(@AuthenticationPrincipal AuthUser user) {
		if (user == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
		}
		return user;
	}

	@PostMapping("/api/auth/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthUser signup(@Valid @RequestBody Signup request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		AuthUser user = authService.signup(request, httpRequest.getRemoteAddr());
		startSession(user, httpRequest, httpResponse);
		return user;
	}

	@PostMapping("/api/auth/login")
	public AuthUser login(@Valid @RequestBody Login request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		AuthUser user = authService.login(request.email(), request.password());
		startSession(user, httpRequest, httpResponse);
		return user;
	}

	@PostMapping("/api/auth/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		endSession(request, response);
	}

	/** 비밀번호 변경. 세션 ID를 새로 발급해 변경 전 세션 값을 다시 쓸 수 없게 한다. */
	@PostMapping("/api/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChangePassword body,
			HttpServletRequest request) {
		authService.changePassword(user.id(), body.currentPassword(), body.newPassword());
		request.changeSessionId();
	}

	/** 회원 탈퇴 (앱스토어 심사 요건: 앱 안에서 탈퇴 가능해야 함) */
	@PostMapping("/api/me/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody Withdraw body,
			HttpServletRequest request, HttpServletResponse response) {
		authService.withdraw(user.id(), body.password());
		endSession(request, response);
	}

	private void startSession(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
		// 세션 고정 공격 방지: 로그인 전에 쓰던 세션 ID를 바꾼다
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		List<SimpleGrantedAuthority> authorities = user.isStaff()
				? user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()
				: List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
		SecurityContext context = holder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
		holder.setContext(context);
		contextRepository.saveContext(context, request, response);
		// 로그인 전 CSRF 토큰은 버리고, 다음 변경 요청 전에 새로 받게 한다
		csrfTokenRepository.saveToken(null, request, response);
	}

	private void endSession(HttpServletRequest request, HttpServletResponse response) {
		holder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		csrfTokenRepository.saveToken(null, request, response);
	}

}
